package com.agent.agentplatform.dto;

import com.agent.agentplatform.entity.Agent;
import com.agent.agentplatform.entity.AgentStatus;

import java.time.Instant;

public record AgentResponse(Long id, String name, String description, String systemPrompt,
                            String modelName, AgentStatus status, Long creatorId,
                            String creatorUsername, Instant createdAt, Instant updatedAt, int skillCount, Long version,
                            Double temperature, Integer maxOutputTokens) {
    public static AgentResponse fromEntity(Agent agent) {
        return new AgentResponse(agent.getId(), agent.getName(), agent.getDescription(),
                agent.getSystemPrompt(), agent.getModelName(), agent.getStatus(),
                agent.getOwner().getId(), agent.getOwner().getUsername(),
                agent.getCreatedAt(), agent.getUpdatedAt(), agent.getSkills().size(), agent.getVersion(),agent.getTemperature(),agent.getMaxOutputTokens());
    }
}
