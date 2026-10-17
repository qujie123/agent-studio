package com.agent.agentplatform.dto;

import com.agent.agentplatform.entity.AgentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AgentRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        @NotBlank @Size(max = 10000) String systemPrompt,
        @NotBlank @Size(max = 100) String modelName,
        @NotNull AgentStatus status,
        @jakarta.validation.constraints.Min(0) Long version,
        @jakarta.validation.constraints.DecimalMin("0.0") @jakarta.validation.constraints.DecimalMax("2.0") Double temperature,
        @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(65536) Integer maxOutputTokens
) {
    public AgentRequest(String name, String description, String systemPrompt, String modelName, AgentStatus status) {
        this(name, description, systemPrompt, modelName, status, 0L, null, null);
    }
    public AgentRequest(String name, String description, String systemPrompt, String modelName, AgentStatus status, Long version) {
        this(name, description, systemPrompt, modelName, status, version, null, null);
    }
}
