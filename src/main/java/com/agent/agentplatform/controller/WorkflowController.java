package com.agent.agentplatform.controller;
import com.agent.agentplatform.dto.*;
import com.agent.agentplatform.dto.WorkflowDtos.*;
import com.agent.agentplatform.service.WorkflowService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import java.security.Principal;

@RestController @RequestMapping("/api")
public class WorkflowController {
    private final WorkflowService workflows;
    public WorkflowController(WorkflowService workflows){this.workflows=workflows;}
    @GetMapping("/workflows/page") public PageResult<View> page(Principal user,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return workflows.page(user.getName(),q,page,size);}
    @GetMapping("/workflows/{id}") public View get(@PathVariable Long id,Principal user){return workflows.get(id,user.getName());}
    @PostMapping("/workflows") @ResponseStatus(HttpStatus.CREATED) public View create(@Valid @RequestBody Save request,Authentication auth){return workflows.create(request,auth);}
    @PutMapping("/workflows/{id}") public View update(@PathVariable Long id,@Valid @RequestBody Save request,Authentication auth){return workflows.update(id,request,auth);}
    @DeleteMapping("/workflows/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id,Principal user){workflows.delete(id,user.getName());}
    @PostMapping("/workflows/{id}/runs") public Run run(@PathVariable Long id,@Valid @RequestBody Request request,Authentication auth){return workflows.run(id,request,auth);}
    @GetMapping("/workflows/{id}/runs/page") public PageResult<Run> runs(@PathVariable Long id,Principal user,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return workflows.runs(id,user.getName(),page,size);}
    @GetMapping("/workflow-runs/{id}") public Run run(@PathVariable Long id,Principal user){return workflows.getRun(id,user.getName());}
}
