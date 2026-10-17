package com.agent.agentplatform.controller;
import com.agent.agentplatform.dto.*;
import com.agent.agentplatform.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/me/model-settings")
public class PersonalModelController {
    private final ModelSettingsService settings;
    private final ModelConnectionTestService tests;
    private final ModelCatalogService catalog;
    public PersonalModelController(ModelSettingsService settings, ModelConnectionTestService tests, ModelCatalogService catalog) { this.settings = settings; this.tests = tests; this.catalog = catalog; }
    @GetMapping public ModelSettingsResponse view(Principal user) { return settings.personalView(user.getName()); }
    @PostMapping("/models") public ModelCatalogService.Catalog models(Principal user) {
        if ("INHERIT".equals(settings.personalView(user.getName()).mode())) { throw new IllegalArgumentException("请先保存个人 API 配置"); }
        return catalog.read(settings.connectionForUser(user.getName()));
    }
    @PutMapping public ModelSettingsResponse save(@Valid @RequestBody ModelSettingsRequest request, Principal user) { return settings.savePersonal(request, user.getName()); }
    public record Revision(@NotNull @Min(0) Long revision) {}
    @PostMapping("/inherit") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void inherit(@Valid @RequestBody Revision request, Principal user) { settings.inherit(user.getName(), request.revision()); }
    @PostMapping("/test") public Map<String, Object> test(Principal user) {
        if ("INHERIT".equals(settings.personalView(user.getName()).mode())) { throw new IllegalArgumentException("请先保存个人 API 配置"); }
        var config = settings.connectionForUser(user.getName());
        if (!"API".equals(config.mode())) { throw new IllegalArgumentException("请先选择真实 API 模式"); }
        return tests.test(config, user.getName());
    }
}
