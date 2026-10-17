package com.agent.agentplatform.service;
import com.agent.agentplatform.dto.*;
import com.agent.agentplatform.dto.SkillRunDtos.*;
import com.agent.agentplatform.entity.*;
import com.agent.agentplatform.repository.*;
import com.agent.agentplatform.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Semaphore;

@Service
public class SkillExecutionService {
    private final SkillRepository skills;private final SkillRunRepository runs;private final UserRepository users;private final PythonSandbox sandbox;private final AuditService audit;private final TransactionTemplate transaction;private final Semaphore slots=new Semaphore(2);
    public SkillExecutionService(SkillRepository skills,SkillRunRepository runs,UserRepository users,PythonSandbox sandbox,AuditService audit,PlatformTransactionManager manager){this.skills=skills;this.runs=runs;this.users=users;this.sandbox=sandbox;this.audit=audit;transaction=new TransactionTemplate(manager);}
    private Skill owned(Long id,String username){var skill=skills.findById(id).orElseThrow(()->new ResourceNotFoundException("Skill not found"));if(!skill.getOwner().getUsername().equals(username))throw new ResourceNotFoundException("Skill not found");return skill;}
    public SkillResponse get(Long id,String username){return transaction.execute(status->SkillResponse.from(owned(id,username)));}
    public void requireExecutable(Long id,String username){transaction.executeWithoutResult(status->{var skill=owned(id,username);if(!skill.isEnabled()||!"PYTHON".equals(skill.getKind()))throw new ConflictException("此 Skill 不是已启用的 Python Skill");});}
    public View getRun(Long id,String username){return transaction.execute(status->View.from(runs.findByIdAndOwnerUsername(id,username).orElseThrow(()->new ResourceNotFoundException("Skill run not found"))));}
    public PageResult<View> page(String username,int page,int size){return transaction.execute(status->PageResult.from(runs.findByOwnerUsernameOrderByCreatedAtDescIdDesc(username,PageResult.request(page,size)),View::from));}
    public PageResult<SkillResponse> available(String username,String q,int page,int size){return transaction.execute(status->PageResult.from(skills.executable(username,PageResult.query(q),PageResult.request(page,size)),SkillResponse::from));}
    private record Start(SkillRun run,boolean fresh){}
    private View previous(Long skillId,Request request,String username){return runs.findByOwnerUsernameAndRequestId(username,request.requestId().toString()).map(r->{if(!Objects.equals(r.skill==null?null:r.skill.getId(),skillId)||!r.input.equals(request.input()))throw new ConflictException("同一执行标识不能用于不同 Skill 或输入");return View.from(r);}).orElse(null);}
    public View run(Long skillId,Request request,String username){
        var previous=transaction.execute(status->previous(skillId,request,username));if(previous!=null)return previous;requireExecutable(skillId,username);
        var availability=sandbox.status();if(!availability.available())throw new ModelUnavailableException(availability.message());if(!slots.tryAcquire())throw new ModelUnavailableException("容器执行繁忙，请稍后再试");
        try{
            var start=transaction.execute(status->{var owner=users.findByUsernameForUpdate(username).orElseThrow();if(!owner.isEnabled())throw new ConflictException("用户已停用");var prior=previous(skillId,request,username);if(prior!=null)return new Start(runs.findByIdAndOwnerUsername(prior.id(),username).orElseThrow(),false);var skill=owned(skillId,username);if(!skill.isEnabled()||!"PYTHON".equals(skill.getKind()))throw new ConflictException("该 Skill 不是已启用的 Python Skill");var run=new SkillRun();run.skill=skill;run.owner=owner;run.skillName=skill.getName();run.skillVersion=skill.getVersion();run.code=skill.getCode();run.requestId=request.requestId().toString();run.input=request.input();run.status="RUNNING";run.createdAt=Instant.now();run.deadline=run.createdAt.plusSeconds(20);runs.saveAndFlush(run);audit.record(username,"SKILL_RUN_STARTED","SKILL_RUN",run.id);return new Start(run,true);});
            if(!start.fresh())return View.from(start.run());
            PythonSandbox.Result result;try{result=sandbox.run(start.run().code,start.run().input);}catch(RuntimeException failure){result=new PythonSandbox.Result("","容器执行失败");}final var answer=result;
            return transaction.execute(status->{var run=runs.locked(start.run().id).orElseThrow();if("RUNNING".equals(run.status)){run.output=answer.output();run.error=answer.error();run.status=answer.error()==null?"SUCCEEDED":"FAILED";audit.record(username,"SKILL_RUN_FINISHED","SKILL_RUN",run.id);}return View.from(run);});
        }finally{slots.release();}
    }
    public void recover(){transaction.executeWithoutResult(status->{for(var candidate:runs.findByStatusAndDeadlineBefore("RUNNING",Instant.now())){var run=runs.locked(candidate.id).orElseThrow();if("RUNNING".equals(run.status)){run.status="FAILED";run.error="执行中断；未自动重试，请检查输出后重新提交";}}});}
}
