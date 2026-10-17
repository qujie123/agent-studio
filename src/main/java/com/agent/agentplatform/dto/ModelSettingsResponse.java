package com.agent.agentplatform.dto;
public record ModelSettingsResponse(String mode, String baseUrl, boolean hasKey, String defaultModel,
                                    int timeoutSeconds, long revision, String source, String protocol) {}
