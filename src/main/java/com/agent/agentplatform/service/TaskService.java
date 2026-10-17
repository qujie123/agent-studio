package com.agent.agentplatform.service;

import com.agent.agentplatform.dto.CompleteTaskRequest;
import com.agent.agentplatform.dto.CreateTaskRequest;
import com.agent.agentplatform.dto.TaskResponse;
import com.agent.agentplatform.dto.TaskExecution;
import com.agent.agentplatform.entity.Agent;
import com.agent.agentplatform.entity.AgentStatus;
import com.agent.agentplatform.entity.AgentTask;
import com.agent.agentplatform.entity.TaskStatus;
import com.agent.agentplatform.exception.ConflictException;
import com.agent.agentplatform.exception.ForbiddenException;
import com.agent.agentplatform.exception.ResourceNotFoundException;
import com.agent.agentplatform.repository.TaskRepository;
import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.Instant;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class TaskService {
    private final TaskRepository taskRepository;
    private final AgentService agentService;
    private final UserService userService;
    private final int executionTimeoutSeconds;
    private final AuditService audit;
    private final ModelSettingsService modelSettings;
    private final UsageLedgerService usage;
    private final KnowledgeService knowledge;

    public TaskService(TaskRepository taskRepository, AgentService agentService, UserService userService,
                       @Value("${app.llm.timeout-seconds:60}") int executionTimeoutSeconds,
                       AuditService audit, ModelSettingsService modelSettings, UsageLedgerService usage, KnowledgeService knowledge) {
        this.taskRepository = taskRepository;
        this.agentService = agentService;
        this.userService = userService;
        this.executionTimeoutSeconds = executionTimeoutSeconds;
        this.audit = audit;
        this.modelSettings = modelSettings;
        this.usage = usage;this.knowledge=knowledge;
    }

    @Transactional
    public TaskResponse createTask(CreateTaskRequest request, String username) {
        Agent agent = agentService.findById(request.agentId());
        if (agent.getStatus() != AgentStatus.ACTIVE) {
            throw new ConflictException("Only active agents can accept tasks");
        }
        AgentTask task = new AgentTask();
        task.setAgent(agent);
        task.setUser(userService.findByUsername(username));
        task.setInput(request.input().trim());
        task.setProjectMode(request.projectMode());
        task.setStatus(TaskStatus.PENDING);
        taskRepository.saveAndFlush(task);
        audit.record(username, "TASK_CREATED", "TASK", task.getId());
        return TaskResponse.fromEntity(task);
    }

    public List<TaskResponse> getMyTasks(String username) {
        return taskRepository.findByUserUsernameOrderByCreatedAtDesc(username).stream()
                .map(TaskResponse::fromEntity).toList();
    }

    public List<TaskResponse> getAllTasks() {
        return taskRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(TaskResponse::fromEntity).toList();
    }

    public List<TaskResponse> getAssignedTasks(String username) {
        return taskRepository.findByAgentOwnerUsernameOrderByCreatedAtDesc(username).stream()
                .map(TaskResponse::fromEntity).toList();
    }

    public com.agent.agentplatform.dto.PageResult<TaskResponse> page(Authentication auth, String scope, int page, int size) {
        return page(auth,scope,"",null,page,size);
    }
    public com.agent.agentplatform.dto.PageResult<TaskResponse> page(Authentication auth, String scope, String q, TaskStatus status, int page, int size) {
        if (!java.util.Set.of("mine","assigned","all").contains(scope)) { throw new IllegalArgumentException("Invalid task scope"); }
        if (scope.equals("all") && !isAdmin(auth)) { throw new ForbiddenException("Only administrators can view all tasks"); }
        return com.agent.agentplatform.dto.PageResult.from(taskRepository.search(auth.getName(), scope.equals("all"), scope.equals("assigned"),com.agent.agentplatform.dto.PageResult.query(q),status,
                com.agent.agentplatform.dto.PageResult.request(page,size)), TaskResponse::fromEntity);
    }

    @Transactional
    public TaskResponse enqueue(Long id, Authentication authentication) {
        AgentTask task = findForUpdate(id);
        requireExecutable(task, authentication);
        task.setStatus(TaskStatus.QUEUED);
        captureConfiguration(task);
        taskRepository.flush();
        audit.record(authentication.getName(), "TASK_QUEUED", "TASK", id);
        return TaskResponse.fromEntity(task);
    }

    @Transactional
    public Optional<TaskExecution> claimQueuedTask() {
        var candidate = taskRepository.findFirstByStatusOrderByCreatedAtAscIdAsc(TaskStatus.QUEUED);
        if (candidate.isEmpty()) { return Optional.empty(); }
        AgentTask task = candidate.get();
        if (task.getStatus() != TaskStatus.QUEUED) { return Optional.empty(); }
        if (task.getAgent().getStatus() != AgentStatus.ACTIVE || !task.getUser().isEnabled()) {
            task.setStatus(TaskStatus.FAILED);
            task.setOutput(task.getUser().isEnabled() ? "Agent was stopped before execution" : "Task owner was disabled before execution");
            audit.record("system", "TASK_FAILED", "TASK", task.getId());
            return Optional.empty();
        }
        task.setStatus(TaskStatus.RUNNING);
        taskRepository.flush();
        usage.start("TASK", task.getId(), task.getUser().getUsername(), task.getExecutionModel(), task.getExecutionMode());
        audit.record("system", "TASK_STARTED", "TASK", task.getId());
        return Optional.of(new TaskExecution(task.getId(), task.getExecutionModel(),
                task.getExecutionPrompt(), task.getInput(), task.getExecutionMode(), task.getProviderRevision(), task.getProviderSettingsId(),task.getExecutionTemperature(),task.getExecutionMaxOutputTokens(),task.getExecutionKnowledge()));
    }

    @Transactional
    public TaskResponse cancel(Long id, Authentication authentication) {
        AgentTask task = findForUpdate(id);
        if (!canView(task, authentication)) { throw new ForbiddenException("You cannot cancel this task"); }
        if (task.getStatus() != TaskStatus.PENDING && task.getStatus() != TaskStatus.QUEUED) {
            throw new ConflictException("Only pending or queued tasks can be cancelled");
        }
        task.setStatus(TaskStatus.CANCELLED);
        taskRepository.flush();
        audit.record(authentication.getName(), "TASK_CANCELLED", "TASK", id);
        return TaskResponse.fromEntity(task);
    }

    @Transactional
    public void recoverExpiredExecutions() {
        for (AgentTask task : taskRepository.findByStatusAndUpdatedAtBefore(TaskStatus.RUNNING,
                Instant.now().minusSeconds(31))) {
            if (task.getUpdatedAt().isAfter(Instant.now().minusSeconds(timeout(task) + 30L))) { continue; }
            task.setStatus(TaskStatus.FAILED);
            task.setOutput("Execution interrupted; recovered after deadline. Submit a new task to retry.");
            usage.finish("TASK", task.getId(), "FAILED", com.agent.agentplatform.dto.ModelReply.text(null));
            audit.record("system", "TASK_RECOVERED", "TASK", task.getId());
        }
    }

    @Transactional
    public TaskExecution beginExecution(Long id, Authentication authentication) {
        AgentTask task = findForUpdate(id);
        requireExecutable(task, authentication);
        task.setStatus(TaskStatus.RUNNING);
        captureConfiguration(task);
        taskRepository.flush();
        usage.start("TASK", task.getId(), task.getUser().getUsername(), task.getExecutionModel(), task.getExecutionMode());
        audit.record(authentication.getName(), "TASK_STARTED", "TASK", id);
        return new TaskExecution(task.getId(), task.getExecutionModel(), task.getExecutionPrompt(), task.getInput(),
                task.getExecutionMode(), task.getProviderRevision(), task.getProviderSettingsId(),task.getExecutionTemperature(),task.getExecutionMaxOutputTokens(),task.getExecutionKnowledge());
    }

    private void requireExecutable(AgentTask task, Authentication authentication) {
        if (!canView(task, authentication)) {
            throw new ForbiddenException("You cannot execute this task");
        }
        if (task.getStatus() != TaskStatus.PENDING) {
            throw new ConflictException("Only pending tasks can be executed");
        }
        if (task.getAgent().getStatus() != AgentStatus.ACTIVE) {
            throw new ConflictException("The agent is no longer active");
        }
    }

    private void captureConfiguration(AgentTask task) {
        if (!task.getUser().isEnabled()) { throw new ConflictException("Task owner is disabled"); }
        var config = modelSettings.connectionForUser(task.getUser().getUsername());
        if ("API".equals(config.mode())) { ModelSettingsService.validate(config); }
        String model = task.getAgent().getModelName();
        task.setExecutionModel((config.settingsId() != 1L || "demo-model".equals(model)) && "API".equals(config.mode()) && !config.defaultModel().isBlank()
                ? config.defaultModel() : model);
        task.setExecutionMode(config.mode()); task.setProviderRevision(config.revision());
        task.setProviderSettingsId(config.settingsId());
        task.setExecutionTimeoutSeconds(config.timeoutSeconds());
        task.setExecutionTemperature(task.getAgent().getTemperature());
        task.setExecutionMaxOutputTokens(task.getAgent().getMaxOutputTokens());
        StringBuilder prompt = new StringBuilder(task.getAgent().getSystemPrompt());
        StringBuilder summary = new StringBuilder();
        for (var skill : task.getAgent().getSkills()) {
            if (!skill.isEnabled()||!"PROMPT".equals(skill.getKind())) { continue; }
            prompt.append("\n\n## Skill: ").append(skill.getName()).append("\n").append(skill.getInstructions());
            if (!summary.isEmpty()) { summary.append("\n"); }
            summary.append(skill.getName()).append(" · v").append(skill.getVersion() + 1);
        }
        if (task.isProjectMode()) { prompt.append(ProjectDelivery.INSTRUCTIONS); }
        task.setExecutionKnowledge(knowledge.context(task.getAgent(),task.getInput(),task.getUser().getUsername()));
        if(prompt.length()+task.getInput().length()+task.getExecutionKnowledge().length()>60000)throw new ConflictException("指令和知识引用超过 60000 字符，请精简后重试");
        task.setExecutionPrompt(prompt.toString()); task.setExecutionSkills(summary.toString());
    }

    private int timeout(AgentTask task) {
        return task.getExecutionTimeoutSeconds() == null ? executionTimeoutSeconds : task.getExecutionTimeoutSeconds();
    }

    @Transactional
    public TaskResponse finishExecution(Long id, String output, boolean success) {
        return finishReply(id, com.agent.agentplatform.dto.ModelReply.text(output), success);
    }

    @Transactional
    public TaskResponse finishReply(Long id, com.agent.agentplatform.dto.ModelReply reply, boolean success) {
        AgentTask task = findForUpdate(id);
        if (task.getStatus() != TaskStatus.RUNNING) {
            throw new ConflictException("Task is no longer running");
        }
        task.setOutput(reply.content()); task.setUsage(reply);
        task.setStatus(success ? TaskStatus.SUCCEEDED : TaskStatus.FAILED);
        usage.finish("TASK", id, success ? "SUCCEEDED" : "FAILED", reply);
        taskRepository.flush();
        audit.record("system", success ? "TASK_SUCCEEDED" : "TASK_FAILED", "TASK", id);
        return TaskResponse.fromEntity(task);
    }

    @Transactional
    public TaskResponse recoverInterruptedTask(Long id, Authentication authentication) {
        AgentTask task = findForUpdate(id);
        if (!canView(task, authentication)) {
            throw new ForbiddenException("You cannot recover this task");
        }
        if (task.getStatus() != TaskStatus.RUNNING
                || task.getUpdatedAt().isAfter(Instant.now().minusSeconds(timeout(task) + 30L))) {
            throw new ConflictException("Only a running task past its execution deadline can be recovered");
        }
        task.setStatus(TaskStatus.FAILED);
        task.setOutput("Execution interrupted; recovered after deadline. Submit a new task to retry.");
        usage.finish("TASK", id, "FAILED", com.agent.agentplatform.dto.ModelReply.text(null));
        taskRepository.flush();
        audit.record(authentication.getName(), "TASK_RECOVERED", "TASK", id);
        return TaskResponse.fromEntity(task);
    }

    public TaskResponse getTask(Long id, Authentication authentication) {
        AgentTask task = findById(id);
        if (!canView(task, authentication)) {
            throw new ForbiddenException("You cannot view this task");
        }
        return TaskResponse.fromEntity(task);
    }

    @Transactional
    public TaskResponse completeTask(Long id, CompleteTaskRequest request, Authentication authentication) {
        AgentTask task = findForUpdate(id);
        if (!isAdmin(authentication)
                && !task.getAgent().getOwner().getUsername().equals(authentication.getName())) {
            throw new ForbiddenException("Only the agent creator or an administrator can complete this task");
        }
        if (task.getStatus() != TaskStatus.PENDING) {
            throw new ConflictException("Only pending tasks can be completed manually");
        }
        task.setOutput(request.output().trim());
        task.setStatus(request.success() ? TaskStatus.SUCCEEDED : TaskStatus.FAILED);
        taskRepository.flush();
        audit.record(authentication.getName(), "TASK_MANUALLY_COMPLETED", "TASK", id);
        return TaskResponse.fromEntity(task);
    }

    private AgentTask findForUpdate(Long id) {
        return taskRepository.findForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
    }

    private AgentTask findById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
    }

    private boolean canView(AgentTask task, Authentication authentication) {
        return isAdmin(authentication)
                || task.getUser().getUsername().equals(authentication.getName())
                || task.getAgent().getOwner().getUsername().equals(authentication.getName());
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
