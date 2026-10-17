package com.agent.agentplatform.controller;

import com.agent.agentplatform.dto.AgentRequest;
import com.agent.agentplatform.dto.AgentResponse;
import com.agent.agentplatform.service.AgentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/agents")
public class AgentController {
    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping
    public List<AgentResponse> getAgents(Authentication authentication) {
        return agentService.page(authentication,"visible",null,"",0,100).items();
    }

    @GetMapping("/mine")
    public List<AgentResponse> getMyAgents(Authentication authentication) {
        return agentService.page(authentication,"mine",null,"",0,100).items();
    }

    @GetMapping("/{id}")
    public AgentResponse getAgent(@PathVariable Long id, Authentication authentication) {
        return agentService.getAgent(id, authentication);
    }

    @GetMapping("/page")
    public com.agent.agentplatform.dto.PageResult<AgentResponse> page(Authentication auth,
            @RequestParam(defaultValue="visible") String scope, @RequestParam(required=false) com.agent.agentplatform.entity.AgentStatus status,
            @RequestParam(defaultValue="") String q, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return agentService.page(auth,scope,status,q,page,size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgentResponse createAgent(@Valid @RequestBody AgentRequest request,
                                     Authentication authentication) {
        return agentService.createAgent(request, authentication.getName());
    }

    @PutMapping("/{id}")
    public AgentResponse updateAgent(@PathVariable Long id, @Valid @RequestBody AgentRequest request,
                                     Authentication authentication) {
        return agentService.updateAgent(id, request, authentication);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAgent(@PathVariable Long id, Authentication authentication) {
        agentService.deleteAgent(id, authentication);
    }
}
