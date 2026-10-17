package com.agent.agentplatform.controller;

import com.agent.agentplatform.dto.AgentResponse;
import com.agent.agentplatform.dto.TaskResponse;
import com.agent.agentplatform.service.AgentService;
import com.agent.agentplatform.service.TaskService;
import com.agent.agentplatform.service.AuditService;
import com.agent.agentplatform.entity.AuditEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminOperationsController {
    private final AgentService agentService;
    private final TaskService taskService;
    private final AuditService audit;

    public AdminOperationsController(AgentService agentService, TaskService taskService, AuditService audit) {
        this.agentService = agentService;
        this.taskService = taskService;
        this.audit = audit;
    }

    @GetMapping("/audit")
    public List<AuditEvent> getAudit() { return audit.recent(); }
    @GetMapping("/audit/page") public com.agent.agentplatform.dto.PageResult<AuditEvent> page(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue="0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue="20") int size) { return audit.page(page,size); }

    @GetMapping("/agents")
    public List<AgentResponse> getAllAgents(org.springframework.security.core.Authentication auth) {
        return agentService.page(auth,"visible",null,"",0,100).items();
    }

    @GetMapping("/tasks")
    public List<TaskResponse> getAllTasks(org.springframework.security.core.Authentication auth) {
        return taskService.page(auth,"all",0,100).items();
    }
}
