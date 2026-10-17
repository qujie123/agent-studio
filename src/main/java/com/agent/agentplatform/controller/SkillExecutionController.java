package com.agent.agentplatform.controller;
import com.agent.agentplatform.dto.*;
import com.agent.agentplatform.dto.SkillRunDtos.*;
import com.agent.agentplatform.service.*;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.security.Principal;

@RestController @RequestMapping("/api")
public class SkillExecutionController {
    private final SkillExecutionService skills;private final PythonSandbox sandbox;
    public SkillExecutionController(SkillExecutionService skills,PythonSandbox sandbox){this.skills=skills;this.sandbox=sandbox;}
    @GetMapping("/skills/{id}") public SkillResponse get(@PathVariable Long id,Principal user){return skills.get(id,user.getName());}
    @GetMapping("/skills/sandbox-status") public PythonSandbox.Status status(){return sandbox.status();}
    @GetMapping("/skills/python/page") public PageResult<SkillResponse> available(Principal user,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return skills.available(user.getName(),q,page,size);}
    @PostMapping("/skills/{id}/run") public View run(@PathVariable Long id,@Valid @RequestBody Request request,Principal user){return skills.run(id,request,user.getName());}
    @GetMapping("/skill-runs/{id}") public View run(@PathVariable Long id,Principal user){return skills.getRun(id,user.getName());}
    @GetMapping("/skill-runs/page") public PageResult<View> page(Principal user,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return skills.page(user.getName(),page,size);}
}
