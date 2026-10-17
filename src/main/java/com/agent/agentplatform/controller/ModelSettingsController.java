package com.agent.agentplatform.controller;
import com.agent.agentplatform.dto.*;
import com.agent.agentplatform.service.*;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/model-settings")
public class ModelSettingsController {
    private final ModelSettingsService settings;
    private final ModelConnectionTestService tests;
    private final AuditService audit;
    private final ModelCatalogService catalog;
    public ModelSettingsController(ModelSettingsService settings, ModelConnectionTestService tests, AuditService audit, ModelCatalogService catalog) {
        this.settings = settings; this.tests = tests; this.audit = audit;
        this.catalog = catalog;
    }
    @GetMapping public ModelSettingsResponse view() { return settings.view(); }
    @PostMapping("/models") public ModelCatalogService.Catalog models() { return catalog.read(settings.connection()); }
    @PutMapping public ModelSettingsResponse save(@Valid @RequestBody ModelSettingsRequest request, Authentication auth) {
        return settings.save(request, auth.getName());
    }
    @PostMapping("/test") public Map<String, Object> test(Authentication auth) {
        var config = settings.connection();
        if (!"API".equals(config.mode())) { throw new IllegalArgumentException("Save API mode before testing"); }
        var result = tests.test(config, auth.getName());
        audit.record(auth.getName(), "MODEL_CONNECTION_TESTED", "MODEL_SETTINGS", 1L);
        return result;
    }
}
