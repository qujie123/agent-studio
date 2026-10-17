package com.agent.agentplatform.service;

import com.agent.agentplatform.dto.TaskExecution;

public interface ModelGateway {
    void requireConfigured();
    String complete(TaskExecution task);
    default com.agent.agentplatform.dto.ModelReply completeReply(TaskExecution task) {
        return com.agent.agentplatform.dto.ModelReply.text(complete(task));
    }
}
