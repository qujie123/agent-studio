package com.agent.agentplatform.dto;
import jakarta.validation.constraints.*;
public record ModelSettingsRequest(
        @NotNull @Pattern(regexp = "DEMO|API") String mode,
        @NotNull @Size(max = 1000) String baseUrl,
        @Size(max = 2000) String apiKey,
        @NotNull @Size(max = 100) String defaultModel,
        @Min(1) @Max(120) int timeoutSeconds,
        boolean clearKey, @NotNull @Min(0) Long revision,
        @Pattern(regexp = "OPENAI|ANTHROPIC") String protocol) {
    public ModelSettingsRequest {
        if (protocol == null) protocol = "OPENAI";
    }
    public ModelSettingsRequest(String mode, String baseUrl, String apiKey, String defaultModel,
                                int timeoutSeconds, boolean clearKey, Long revision) {
        this(mode, baseUrl, apiKey, defaultModel, timeoutSeconds, clearKey, revision, "OPENAI");
    }
}
