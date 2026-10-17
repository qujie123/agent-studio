package com.agent.agentplatform.controller;

import com.agent.agentplatform.service.ModelSettingsService;
import com.agent.agentplatform.exception.ModelUnavailableException;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemController {
    private final Environment environment;
    private final boolean workerEnabled;
    private final ModelSettingsService settings;
    public SystemController(Environment environment,
                            @Value("${app.worker.enabled:true}") boolean workerEnabled, ModelSettingsService settings) {
        this.environment = environment;
        this.workerEnabled = workerEnabled;
        this.settings = settings;
    }
    @GetMapping("/status")
    public Map<String, Object> status(java.security.Principal user) {
        boolean configured = true;
        try { var connection = settings.connectionForUser(user.getName()); if ("API".equals(connection.mode())) { ModelSettingsService.validate(connection); } }
        catch (ModelUnavailableException exception) { configured = false; }
        var modelSettings = settings.effectiveView(user.getName());
        return Map.of("demo", "DEMO".equals(modelSettings.mode()),
                "ephemeral", environment.acceptsProfiles(Profiles.of("demo")),
                "defaultModel", modelSettings.defaultModel(),
                "modelConfigured", configured, "workerEnabled", workerEnabled, "version", "1.7",
                "connectionSource", "PERSONAL".equals(modelSettings.source()) ? "PERSONAL" : "PLATFORM");
    }
}
