package com.agent.agentplatform.controller;
import com.agent.agentplatform.service.*;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController
public class BalanceController {
    private final ModelSettingsService settings;
    private final BalanceService balances;
    public BalanceController(ModelSettingsService settings, BalanceService balances) { this.settings = settings; this.balances = balances; }
    @PostMapping("/api/me/model-settings/balance") public BalanceService.Balance personal(Principal user) {
        if ("INHERIT".equals(settings.personalView(user.getName()).mode())) { throw new IllegalArgumentException("你正在使用平台配置。个人余额需先保存自己的 API Key；平台账户余额仅管理员可查询。"); }
        return balances.query(settings.connectionForUser(user.getName()));
    }
    @PostMapping("/api/admin/model-settings/balance") public BalanceService.Balance platform() { return balances.query(settings.connection()); }
}
