package com.agent.agentplatform.dto;

import com.agent.agentplatform.entity.AgentTask;
import com.agent.agentplatform.entity.TaskStatus;

import java.time.Instant;

public record TaskResponse(Long id, Long agentId, String agentName, Long userId,
                           String username, String input, String output, TaskStatus status,
                           Instant createdAt, Instant updatedAt, String executionModel,
                           String executionMode, String executionSkills, Long agentCreatorId, Double temperature, Integer maxOutputTokens, String knowledge, boolean projectMode) {
    public static TaskResponse fromEntity(AgentTask task) {
        return new TaskResponse(task.getId(), task.getAgent().getId(), task.getAgent().getName(),
                task.getUser().getId(), task.getUser().getUsername(), task.getInput(),
                task.getOutput(), task.getStatus(), task.getCreatedAt(), task.getUpdatedAt(),
                task.getExecutionModel(), task.getExecutionMode(), task.getExecutionSkills(), task.getAgent().getOwner().getId(),task.getExecutionTemperature(),task.getExecutionMaxOutputTokens(),java.util.Objects.requireNonNullElse(task.getExecutionKnowledge(),""),task.isProjectMode());
    }
}
