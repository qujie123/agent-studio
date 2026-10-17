package com.agent.agentplatform.controller;

import com.agent.agentplatform.dto.CompleteTaskRequest;
import com.agent.agentplatform.dto.CreateTaskRequest;
import com.agent.agentplatform.dto.TaskResponse;
import com.agent.agentplatform.service.TaskService;
import com.agent.agentplatform.service.TaskExecutionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskService taskService;
    private final TaskExecutionService executionService;

    public TaskController(TaskService taskService, TaskExecutionService executionService) {
        this.taskService = taskService;
        this.executionService = executionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse createTask(@Valid @RequestBody CreateTaskRequest request,
                                   Authentication authentication) {
        return taskService.createTask(request, authentication.getName());
    }

    @GetMapping
    public List<TaskResponse> getMyTasks(Authentication authentication) {
        return taskService.page(authentication,"mine",0,100).items();
    }

    @GetMapping("/assigned")
    public List<TaskResponse> getAssignedTasks(Authentication authentication) {
        return taskService.page(authentication,"assigned",0,100).items();
    }

    @GetMapping("/page")
    public com.agent.agentplatform.dto.PageResult<TaskResponse> page(Authentication auth,
            @RequestParam(defaultValue="mine") String scope, @RequestParam(defaultValue="") String q,
            @RequestParam(required=false) com.agent.agentplatform.entity.TaskStatus status,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return taskService.page(auth,scope,q,status,page,size);
    }

    @PostMapping("/{id}/execute")
    public TaskResponse executeTask(@PathVariable Long id, Authentication authentication) {
        return executionService.execute(id, authentication);
    }

    @PostMapping("/{id}/enqueue")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TaskResponse enqueue(@PathVariable Long id, Authentication authentication) {
        return executionService.enqueue(id, authentication);
    }

    @PostMapping("/{id}/cancel")
    public TaskResponse cancel(@PathVariable Long id, Authentication authentication) {
        return taskService.cancel(id, authentication);
    }

    @PostMapping("/{id}/recover")
    public TaskResponse recoverTask(@PathVariable Long id, Authentication authentication) {
        return taskService.recoverInterruptedTask(id, authentication);
    }

    @GetMapping("/{id}")
    public TaskResponse getTask(@PathVariable Long id, Authentication authentication) {
        return taskService.getTask(id, authentication);
    }

    @PatchMapping("/{id}/result")
    public TaskResponse completeTask(@PathVariable Long id,
                                     @Valid @RequestBody CompleteTaskRequest request,
                                     Authentication authentication) {
        return taskService.completeTask(id, request, authentication);
    }
}
