package com.agent.agentplatform.service;

import com.agent.agentplatform.dto.AgentRequest;
import com.agent.agentplatform.dto.AgentResponse;
import com.agent.agentplatform.entity.Agent;
import com.agent.agentplatform.entity.AgentStatus;
import com.agent.agentplatform.exception.ConflictException;
import com.agent.agentplatform.exception.ForbiddenException;
import com.agent.agentplatform.exception.ResourceNotFoundException;
import com.agent.agentplatform.repository.AgentRepository;
import com.agent.agentplatform.repository.TaskRepository;
import com.agent.agentplatform.repository.ChatSessionRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class AgentService {
    private final AgentRepository agentRepository;
    private final TaskRepository taskRepository;
    private final UserService userService;
    private final AuditService audit;
    private final ChatSessionRepository chats;

    public AgentService(AgentRepository agentRepository, TaskRepository taskRepository,
                        UserService userService, AuditService audit, ChatSessionRepository chats) {
        this.agentRepository = agentRepository;
        this.taskRepository = taskRepository;
        this.userService = userService;
        this.audit = audit;
        this.chats = chats;
    }

    public List<AgentResponse> getVisibleAgents(Authentication authentication) {
        List<Agent> agents = isAdmin(authentication)
                ? agentRepository.findAllByOrderByCreatedAtDesc()
                : agentRepository.findByStatusOrderByCreatedAtDesc(AgentStatus.ACTIVE);
        return agents.stream().map(AgentResponse::fromEntity).toList();
    }

    public com.agent.agentplatform.dto.PageResult<AgentResponse> page(Authentication auth, String scope, AgentStatus status, String q, int page, int size) {
        if (!scope.equals("mine") && !scope.equals("visible")) { throw new IllegalArgumentException("Invalid agent scope"); }
        boolean mine = scope.equals("mine");
        return com.agent.agentplatform.dto.PageResult.from(agentRepository.search(auth.getName(), !mine && isAdmin(auth), !mine, status,
                com.agent.agentplatform.dto.PageResult.query(q), com.agent.agentplatform.dto.PageResult.request(page,size)), AgentResponse::fromEntity);
    }

    public List<AgentResponse> getMyAgents(String username) {
        return agentRepository.findByOwnerUsernameOrderByCreatedAtDesc(username).stream()
                .map(AgentResponse::fromEntity).toList();
    }

    public List<AgentResponse> getAllAgents() {
        return agentRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(AgentResponse::fromEntity).toList();
    }

    public AgentResponse getAgent(Long id, Authentication authentication) {
        Agent agent = findById(id);
        if (agent.getStatus() != AgentStatus.ACTIVE && !canManage(agent, authentication)) {
            throw new ForbiddenException("This agent is not available");
        }
        return AgentResponse.fromEntity(agent);
    }

    @Transactional
    public AgentResponse createAgent(AgentRequest request, String username) {
        Agent agent = new Agent();
        apply(agent, request);
        agent.setOwner(userService.findByUsername(username));
        agentRepository.saveAndFlush(agent);
        audit.record(username, "AGENT_CREATED", "AGENT", agent.getId());
        return AgentResponse.fromEntity(agent);
    }

    @Transactional
    public AgentResponse updateAgent(Long id, AgentRequest request, Authentication authentication) {
        Agent agent = agentRepository.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Agent not found"));
        requireManage(agent, authentication);
        if (!java.util.Objects.equals(request.version(), agent.getVersion())) {
            throw new ConflictException("Agent changed; refresh and try again");
        }
        apply(agent, request);
        agentRepository.saveAndFlush(agent);
        audit.record(authentication.getName(), "AGENT_UPDATED", "AGENT", id);
        return AgentResponse.fromEntity(agent);
    }

    @Transactional
    public void deleteAgent(Long id, Authentication authentication) {
        Agent agent = findById(id);
        requireManage(agent, authentication);
        if (taskRepository.existsByAgentId(id) || chats.existsByAgentId(id)) {
            throw new ConflictException("Agent with task history cannot be deleted; stop it instead");
        }
        agentRepository.delete(agent);
        audit.record(authentication.getName(), "AGENT_DELETED", "AGENT", id);
    }

    public Agent findById(Long id) {
        return agentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent not found"));
    }

    private void apply(Agent agent, AgentRequest request) {
        agent.setName(request.name().trim());
        agent.setDescription(request.description() == null ? null : request.description().trim());
        agent.setSystemPrompt(request.systemPrompt().trim());
        agent.setModelName(request.modelName().trim());
        agent.setTemperature(request.temperature());
        agent.setMaxOutputTokens(request.maxOutputTokens());
        agent.setStatus(request.status());
    }

    private void requireManage(Agent agent, Authentication authentication) {
        if (!canManage(agent, authentication)) {
            throw new ForbiddenException("Only the creator or an administrator can manage this agent");
        }
    }

    private boolean canManage(Agent agent, Authentication authentication) {
        return isAdmin(authentication) || agent.getOwner().getUsername().equals(authentication.getName());
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
