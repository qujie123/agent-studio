package com.agent.agentplatform.exception;

public class ModelCallException extends RuntimeException {
    private final com.agent.agentplatform.dto.ModelReply reply;
    public ModelCallException(String message) {
        this(message, com.agent.agentplatform.dto.ModelReply.text(null));
    }
    public ModelCallException(String message, com.agent.agentplatform.dto.ModelReply reply) { super(message); this.reply = reply; }
    public com.agent.agentplatform.dto.ModelReply getReply() { return reply; }
}
