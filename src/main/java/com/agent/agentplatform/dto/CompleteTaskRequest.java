package com.agent.agentplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CompleteTaskRequest(@NotBlank @Size(max = 50000) String output, @NotNull Boolean success) {
}
