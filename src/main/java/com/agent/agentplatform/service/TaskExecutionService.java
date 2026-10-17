package com.agent.agentplatform.service;

import com.agent.agentplatform.dto.TaskResponse;
import com.agent.agentplatform.dto.TaskExecution;
import com.agent.agentplatform.exception.ModelCallException;
import com.agent.agentplatform.exception.ModelUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class TaskExecutionService {
    private final TaskService taskService;
    private final ModelGateway modelClient;
    private final boolean workerEnabled;

    public TaskExecutionService(TaskService taskService, ModelGateway modelClient,
                                @Value("${app.worker.enabled:true}") boolean workerEnabled) {
        this.taskService = taskService;
        this.modelClient = modelClient;
        this.workerEnabled = workerEnabled;
    }

    // Deliberately no transaction: the HTTP call must not hold a database connection or row lock.
    public TaskResponse execute(Long id, Authentication authentication) {
        taskService.getTask(id, authentication);
        var execution = taskService.beginExecution(id, authentication);
        return runClaimed(execution);
    }

    public TaskResponse enqueue(Long id, Authentication authentication) {
        taskService.getTask(id, authentication);
        if (!workerEnabled) { throw new ModelUnavailableException("Background worker is disabled"); }
        return taskService.enqueue(id, authentication);
    }

    public TaskResponse runClaimed(TaskExecution execution) {
        com.agent.agentplatform.dto.ModelReply output;
        try {
            output = modelClient.completeReply(execution);
        } catch (ModelCallException exception) {
            var partial = exception.getReply();
            String text = exception.getMessage() + (partial.content() == null || partial.content().isBlank() ? "" : "\n\nPartial answer (incomplete):\n" + partial.content());
            return taskService.finishReply(execution.taskId(), new com.agent.agentplatform.dto.ModelReply(text, partial.inputTokens(), partial.outputTokens()), false);
        } catch (RuntimeException exception) {
            return taskService.finishExecution(execution.taskId(), "Model execution failed", false);
        }
        return taskService.finishReply(execution.taskId(), output, true);
    }
}
