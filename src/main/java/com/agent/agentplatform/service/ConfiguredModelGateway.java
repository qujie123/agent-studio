package com.agent.agentplatform.service;
import com.agent.agentplatform.dto.TaskExecution;
import com.agent.agentplatform.exception.ModelCallException;
import org.springframework.stereotype.Component;

@Component
public class ConfiguredModelGateway implements ModelGateway {
    private final ModelSettingsService settings;
    private final CompatibleModelClient api;
    private final DemoModelClient demo;
    public ConfiguredModelGateway(ModelSettingsService settings, CompatibleModelClient api, DemoModelClient demo) {
        this.settings = settings; this.api = api; this.demo = demo;
    }
    public void requireConfigured() {
        var config = settings.connection();
        if ("API".equals(config.mode())) { ModelSettingsService.validate(config); }
    }
    public String complete(TaskExecution task) {
        return completeReply(task).content();
    }
    @Override public com.agent.agentplatform.dto.ModelReply completeReply(TaskExecution task) {
        if ("DEMO".equals(task.executionMode())) { return com.agent.agentplatform.dto.ModelReply.text(demo.complete(task)); }
        var config = settings.connectionForSettings(task.settingsId());
        if (!"API".equals(config.mode()) || task.providerRevision() == null
                || task.providerRevision() != config.revision()) {
            throw new ModelCallException("Model settings changed after task was queued; submit a new task");
        }
        return api.completeReply(task.model(), java.util.List.of(java.util.Map.of("role", "system", "content", task.systemPrompt()),
                java.util.Map.of("role", "user", "content", KnowledgeService.groundedInput(task.input(),task.knowledge()))), config,task.temperature(),task.maxOutputTokens());
    }
}
