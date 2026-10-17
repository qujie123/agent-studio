package com.agent.agentplatform.service;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.recovery.enabled", havingValue = "true", matchIfMissing = true)
public class ExecutionRecoveryWorker {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ExecutionRecoveryWorker.class);
    private final TaskService tasks;
    private final ChatService chats;
    private final UsageLedgerService usage;
    private final WorkflowService workflows;private final SkillExecutionService skills;
    public ExecutionRecoveryWorker(TaskService tasks, ChatService chats, UsageLedgerService usage,WorkflowService workflows,SkillExecutionService skills) { this.tasks = tasks; this.chats = chats; this.usage = usage;this.workflows=workflows;this.skills=skills; }
    @Scheduled(fixedDelay = 10000)
    public void recover() {
        try { tasks.recoverExpiredExecutions(); } catch (RuntimeException failure) { log.error("Task recovery failed ({})", failure.getClass().getSimpleName()); }
        try { chats.recoverExpiredSessions(); } catch (RuntimeException failure) { log.error("Chat recovery failed ({})", failure.getClass().getSimpleName()); }
        try { usage.recoverInterruptedTests(); } catch (RuntimeException failure) { log.error("Connection test recovery failed ({})", failure.getClass().getSimpleName()); }
        try { workflows.recover(); } catch (RuntimeException failure) { log.error("Workflow recovery failed ({})", failure.getClass().getSimpleName()); }
        try { skills.recover(); } catch (RuntimeException failure) { log.error("Skill recovery failed ({})", failure.getClass().getSimpleName()); }
    }
}
