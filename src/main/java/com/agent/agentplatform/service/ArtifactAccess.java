package com.agent.agentplatform.service;

import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;

@Service
public class ArtifactAccess {
    public record Source(Long taskId, Long chatId, Long turnId) {}
    private final TaskService tasks;
    private final ChatService chats;
    private final ProjectDelivery delivery;
    public ArtifactAccess(TaskService tasks, ChatService chats, ProjectDelivery delivery) { this.tasks = tasks; this.chats = chats; this.delivery = delivery; }
    public ProjectDelivery.Project project(Source source, Authentication user) {
        if (source.taskId() != null) { var task = tasks.getTask(source.taskId(), user); return delivery.parse(task.output(), task.status().name()); }
        var turn = chats.getTurn(source.chatId(), source.turnId(), user.getName());
        return delivery.parse(turn.output(), turn.status());
    }
}
