package com.agent.agentplatform.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@ConditionalOnProperty(name = "app.worker.enabled", havingValue = "true", matchIfMissing = true)
public class TaskWorker {
    private static final Logger log = LoggerFactory.getLogger(TaskWorker.class);
    private final TaskService tasks;
    private final TaskExecutionService execution;
    private final java.util.concurrent.ExecutorService pool;
    private final java.util.concurrent.Semaphore slots;
    public TaskWorker(TaskService tasks, TaskExecutionService execution,
                      @org.springframework.beans.factory.annotation.Qualifier("taskExecutionPool") java.util.concurrent.ExecutorService pool,
                      @org.springframework.beans.factory.annotation.Value("${app.worker.concurrency:3}") int concurrency) {
        this.tasks = tasks;
        this.execution = execution;
        this.pool = pool; this.slots = new java.util.concurrent.Semaphore(concurrency);
    }

    @Scheduled(fixedDelayString = "${app.worker.poll-ms:1000}")
    public void poll() {
        try {
            while (slots.tryAcquire()) {
                com.agent.agentplatform.dto.TaskExecution task;
                try {
                    var claimed = tasks.claimQueuedTask();
                    if (claimed.isEmpty()) { slots.release(); break; }
                    task = claimed.get();
                } catch (RuntimeException failure) { slots.release(); throw failure; }
                try {
                    pool.execute(() -> {
                        try { execution.runClaimed(task); }
                        catch (RuntimeException failure) { log.error("Task execution failed ({})", failure.getClass().getSimpleName()); }
                        finally { slots.release(); }
                    });
                } catch (java.util.concurrent.RejectedExecutionException failure) {
                    slots.release(); tasks.finishExecution(task.taskId(), "Executor stopped before execution; submit a new task to retry.", false); break;
                }
            }
        } catch (RuntimeException exception) {
            log.error("Task worker iteration failed ({})", exception.getClass().getSimpleName());
        }
    }
}
