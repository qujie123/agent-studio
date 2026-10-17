package com.agent.agentplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTaskRequest(@NotNull Long agentId, @NotBlank @Size(max = 20000) String input, Boolean projectMode) {
    public CreateTaskRequest { projectMode = Boolean.TRUE.equals(projectMode); }
    public CreateTaskRequest(Long agentId, String input) { this(agentId, input, false); }
}
