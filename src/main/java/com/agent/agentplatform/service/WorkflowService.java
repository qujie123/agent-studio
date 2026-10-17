package com.agent.agentplatform.service;
import com.agent.agentplatform.dto.*;
import com.agent.agentplatform.dto.WorkflowDtos.*;
import com.agent.agentplatform.entity.*;
import com.agent.agentplatform.repository.*;
import com.agent.agentplatform.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Semaphore;

@Service
public class WorkflowService {
    private final WorkflowRepository workflows;private final WorkflowRunRepository runs;private final UserService users;private final KnowledgeService knowledge;private final AgentService agents;private final TaskService tasks;private final TaskExecutionService execution;private final SkillExecutionService skills;private final AuditService audit;private final ObjectMapper mapper;private final TransactionTemplate transaction;private final Semaphore slots=new Semaphore(3);
    public WorkflowService(WorkflowRepository workflows,WorkflowRunRepository runs,UserService users,KnowledgeService knowledge,AgentService agents,TaskService tasks,TaskExecutionService execution,SkillExecutionService skills,AuditService audit,ObjectMapper mapper,PlatformTransactionManager manager){this.workflows=workflows;this.runs=runs;this.users=users;this.knowledge=knowledge;this.agents=agents;this.tasks=tasks;this.execution=execution;this.skills=skills;this.audit=audit;this.mapper=mapper;transaction=new TransactionTemplate(manager);}
    private Workflow owned(Long id,String username){return workflows.owned(id,username).orElseThrow(()->new ResourceNotFoundException("Workflow not found"));}
    private List<Step> steps(String json){return List.of(mapper.readValue(json,Step[].class));}
    private View view(Workflow w){return new View(w.id,w.version,w.name,w.enabled,steps(w.definition),w.updatedAt);}
    private Run view(WorkflowRun r){return new Run(r.id,r.requestId,r.workflow.getId(),r.input,r.output,r.error,r.status,steps(r.definition),List.of(mapper.readValue(r.steps,Trace[].class)),r.createdAt);}
    private void validate(List<Step> steps,Authentication auth){
        for(var step:steps){if(step==null||step.type()==null)throw new IllegalArgumentException("Missing workflow step");switch(step.type()){
            case "TEMPLATE"->{if(step.value()==null||step.value().isBlank())throw new IllegalArgumentException("模板节点需要内容，使用 {{input}} 引用上一节点输出");}
            case "RETRIEVE"->{if(step.resourceId()==null)throw new IllegalArgumentException("检索节点需要文档");knowledge.get(step.resourceId(),auth.getName());}
            case "AGENT"->{if(step.resourceId()==null)throw new IllegalArgumentException("模型节点需要智能体");if(agents.getAgent(step.resourceId(),auth).status()!=AgentStatus.ACTIVE)throw new ConflictException("工作流智能体未启用");}
            case "SKILL"->{if(step.resourceId()==null)throw new IllegalArgumentException("执行节点需要 Python Skill");skills.requireExecutable(step.resourceId(),auth.getName());}
            default->throw new IllegalArgumentException("不支持的工作流节点类型");
        }}
    }
    public PageResult<View> page(String username,String q,int page,int size){return transaction.execute(status->PageResult.from(workflows.search(username,PageResult.query(q),PageResult.request(page,size)),this::view));}
    public View get(Long id,String username){return transaction.execute(status->view(owned(id,username)));}
    public View create(Save request,Authentication auth){return transaction.execute(status->{if(workflows.countByOwnerUsername(auth.getName())>=100)throw new ConflictException("每人最多 100 个工作流");validate(request.steps(),auth);var workflow=new Workflow();workflow.owner=users.findByUsername(auth.getName());workflow.name=request.name().strip();workflow.enabled=request.enabled();workflow.definition=mapper.writeValueAsString(request.steps());workflows.saveAndFlush(workflow);audit.record(auth.getName(),"WORKFLOW_CREATED","WORKFLOW",workflow.id);return view(workflow);});}
    public View update(Long id,Save request,Authentication auth){return transaction.execute(status->{var workflow=owned(id,auth.getName());if(!Objects.equals(workflow.version,request.version()))throw new ConflictException("工作流已变化，请刷新后重试");validate(request.steps(),auth);workflow.name=request.name().strip();workflow.enabled=request.enabled();workflow.definition=mapper.writeValueAsString(request.steps());workflows.flush();audit.record(auth.getName(),"WORKFLOW_UPDATED","WORKFLOW",id);return view(workflow);});}
    public void delete(Long id,String username){transaction.executeWithoutResult(status->{var workflow=owned(id,username);if(runs.existsByWorkflowId(id))throw new ConflictException("有运行历史的工作流请停用，不可删除");workflows.delete(workflow);audit.record(username,"WORKFLOW_DELETED","WORKFLOW",id);});}
    public Run getRun(Long id,String username){return transaction.execute(status->view(runs.findByIdAndOwnerUsername(id,username).orElseThrow(()->new ResourceNotFoundException("Workflow run not found"))));}
    public PageResult<Run> runs(Long id,String username,int page,int size){return transaction.execute(status->{owned(id,username);return PageResult.from(runs.findByWorkflowIdAndOwnerUsernameOrderByIdDesc(id,username,PageResult.request(page,size)),this::view);});}
    private record Start(Run run,boolean fresh){}
    public Run run(Long id,Request request,Authentication auth){
        if(!slots.tryAcquire())throw new ModelUnavailableException("工作流执行繁忙，请稍后再试");
        try{
            Start start=transaction.execute(status->{var workflow=owned(id,auth.getName());var previous=runs.findByWorkflowIdAndRequestId(id,request.requestId().toString());if(previous.isPresent()){if(!previous.get().input.equals(request.input().strip()))throw new ConflictException("同一请求标识不能用于不同输入");return new Start(view(previous.get()),false);}if(!workflow.enabled)throw new ConflictException("工作流已停用");if(runs.existsByWorkflowIdAndStatus(id,"RUNNING"))throw new ConflictException("此工作流正在执行");validate(steps(workflow.definition),auth);var run=new WorkflowRun();run.workflow=workflow;run.owner=users.findByUsername(auth.getName());run.input=request.input().strip();run.requestId=request.requestId().toString();run.definition=workflow.definition;run.steps="[]";run.status="RUNNING";run.createdAt=Instant.now();run.deadline=run.createdAt.plusSeconds(1800);runs.saveAndFlush(run);audit.record(auth.getName(),"WORKFLOW_RUN_STARTED","WORKFLOW_RUN",run.id);return new Start(view(run),true);});
            if(!start.fresh())return start.run();String output=start.run().input(),error=null;var trace=new ArrayList<Trace>();
            for(int index=0;index<start.run().definition().size();index++){
                var step=start.run().definition().get(index);Long taskId=null,skillRunId=null;String nodeStatus="SUCCEEDED";
                try{if(!"RUNNING".equals(getRun(start.run().id(),auth.getName()).status()))break;
                    switch(step.type()){
                        case "TEMPLATE"->output=step.value().replace("{{input}}",output);
                        case "RETRIEVE"->{String query=output.substring(0,Math.min(100,output.length()));output=KnowledgeService.groundedInput(output,KnowledgeService.format(knowledge.retrieve(step.resourceId(),query,auth.getName())));}
                        case "AGENT"->{if(output.length()>20000)throw new ConflictException("模型节点输入超过 20000 字符，请精简前置节点");var task=tasks.createTask(new CreateTaskRequest(step.resourceId(),output),auth.getName());taskId=task.id();var result=execution.execute(task.id(),auth);output=result.output();if(result.status()!=TaskStatus.SUCCEEDED){nodeStatus="FAILED";error="模型节点失败，请查看任务 #"+task.id();}}
                        case "SKILL"->{if(output.length()>20000)throw new ConflictException("Skill 节点输入超过 20000 字符");var result=skills.run(step.resourceId(),new SkillRunDtos.Request(UUID.randomUUID(),output),auth.getName());skillRunId=result.id();output=result.output();if(!"SUCCEEDED".equals(result.status())){nodeStatus="FAILED";error="Skill 节点失败，请查看执行 #"+result.id();}}
                    }
                    if(output==null)output="";if(output.length()>50000)throw new ConflictException("节点输出超过 50000 字符");
                }catch(ConflictException|ResourceNotFoundException|ModelUnavailableException|IllegalArgumentException failure){nodeStatus="FAILED";error=failure.getMessage();}
                catch(RuntimeException failure){nodeStatus="FAILED";error="工作流节点执行失败";}
                if(output==null)output="";if(output.length()>50000)output=output.substring(0,50000);
                trace.add(new Trace(index+1,step.type(),nodeStatus,output.substring(0,Math.min(8000,output.length())),taskId,skillRunId));String current=output,currentError=error;String traces=mapper.writeValueAsString(trace);boolean last=index+1==start.run().definition().size()||error!=null;
                transaction.executeWithoutResult(status->{var run=runs.locked(start.run().id()).orElseThrow();if(!"RUNNING".equals(run.status))return;run.steps=traces;run.output=current;run.error=currentError;if(last){run.status=currentError==null?"SUCCEEDED":"FAILED";audit.record(auth.getName(),"WORKFLOW_RUN_FINISHED","WORKFLOW_RUN",run.id);}});
                if(error!=null)break;
            }return getRun(start.run().id(),auth.getName());
        }finally{slots.release();}
    }
    public void recover(){transaction.executeWithoutResult(status->{for(var candidate:runs.findByStatusAndDeadlineBefore("RUNNING",Instant.now())){var run=runs.locked(candidate.id).orElseThrow();if("RUNNING".equals(run.status)){run.status="FAILED";run.error="执行中断；未自动重试付费节点，请检查历史后重新提交";}}});}
}
