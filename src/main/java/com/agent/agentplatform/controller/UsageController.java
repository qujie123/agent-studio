package com.agent.agentplatform.controller;
import com.agent.agentplatform.service.UsageService;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.Map;

@RestController
public class UsageController {
    private final UsageService usage;
    public UsageController(UsageService usage) { this.usage = usage; }
    @GetMapping("/api/me/usage") public UsageService.Summary mine(Principal user) { return usage.mine(user.getName()); }
    @GetMapping("/api/me/dashboard") public Map<String,Long> dashboard(Principal user) { return usage.dashboard(user.getName()); }
    @GetMapping("/api/admin/users/{id}/resources") public Map<String,Long> resources(@PathVariable Long id) { return usage.resources(id); }
}
