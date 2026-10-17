package com.agent.agentplatform.dto;
public record ModelReply(String content, Long inputTokens, Long outputTokens) {
    public static ModelReply text(String value) { return new ModelReply(value, null, null); }
}
