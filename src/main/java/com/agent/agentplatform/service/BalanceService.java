package com.agent.agentplatform.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;

@Service
public class BalanceService {
    public record Amount(String currency, BigDecimal total, BigDecimal granted, BigDecimal toppedUp) {}
    public record Balance(String status, String provider, String kind, String message, List<Amount> balances,
                          BigDecimal used, BigDecimal remaining, Boolean available, Instant checkedAt, String consoleUrl) {}
    private final ObjectMapper mapper;
    private final HttpClient client;
    @Autowired public BalanceService(ObjectMapper mapper) {
        this(mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build());
    }
    BalanceService(ObjectMapper mapper, HttpClient client) { this.mapper = mapper; this.client = client; }
    public Balance query(ModelSettingsService.Connection config) {
        if (!"API".equals(config.mode()) || config.apiKey().isBlank()) { return unavailable("NOT_CONFIGURED", "", "请先保存真实 API 配置", ""); }
        ModelSettingsService.validate(config);
        URI base = URI.create(config.baseUrl());
        boolean standardHttps = "https".equals(base.getScheme()) && (base.getPort() == -1 || base.getPort() == 443);
        String endpoint, provider, console;
        if (standardHttps && "api.deepseek.com".equalsIgnoreCase(base.getHost()) && Set.of("", "/v1").contains(base.getPath())) {
            provider = "DeepSeek"; endpoint = "https://api.deepseek.com/user/balance"; console = "https://platform.deepseek.com/";
        } else if (standardHttps && "openrouter.ai".equalsIgnoreCase(base.getHost()) && "/api/v1".equals(base.getPath())) {
            provider = "OpenRouter"; endpoint = "https://openrouter.ai/api/v1/key"; console = "https://openrouter.ai/settings/credits";
        } else {
            console = "api.openai.com".equalsIgnoreCase(base.getHost()) ? "https://platform.openai.com/settings/organization/billing/overview" : "";
            return unavailable("UNSUPPORTED", base.getHost(), "该地址尚无已验证的普通 Key 余额适配器。兼容聊天接口不代表支持余额接口，请在服务商控制台查询。", console);
        }
        try {
            var request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + config.apiKey()).header("Accept", "application/json").GET().build();
            var response = client.send(request, LimitedResponseBody.handler(65536));
            if (response.statusCode() != 200) { return unavailable("ERROR", provider, "查询失败：服务商返回 HTTP " + response.statusCode() + "，请检查 Key 权限或稍后重试。", console); }
            if (response.body().length() > 65536) { throw new IllegalArgumentException(); }
            return parse(provider, mapper.readTree(response.body()), console);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt(); return unavailable("ERROR", provider, "余额查询已中断", console);
        } catch (Exception failure) { return unavailable("ERROR", provider, "余额查询失败或返回数据无效；没有将其视为零余额。", console); }
    }
    Balance parse(String provider, JsonNode root, String console) {
        if ("DeepSeek".equals(provider)) {
            if (!root.path("is_available").isBoolean() || !root.path("balance_infos").isArray() || root.path("balance_infos").isEmpty()) { throw new IllegalArgumentException(); }
            var amounts = new ArrayList<Amount>();
            for (var row : root.path("balance_infos")) {
                String currency = row.path("currency").asString();
                if (!Set.of("CNY", "USD").contains(currency)) { throw new IllegalArgumentException(); }
                amounts.add(new Amount(currency, decimal(row.path("total_balance")), decimal(row.path("granted_balance")), decimal(row.path("topped_up_balance"))));
            }
            return new Balance("OK", provider, "ACCOUNT_BALANCE", "服务商返回的账户余额，包含充值和未过期赠金；不同币种分开展示。",
                    List.copyOf(amounts), null, null, root.path("is_available").asBoolean(), Instant.now(), console);
        }
        var data = root.path("data");
        if (!data.has("limit") || !data.has("limit_remaining")) { throw new IllegalArgumentException(); }
        BigDecimal used = decimal(data.path("usage")), remaining = data.path("limit_remaining").isNull() ? null : decimal(data.path("limit_remaining"));
        return new Balance("OK", provider, "KEY_LIMIT", remaining == null ? "此 Key 未设置消费上限；不代表钱包余额无限。" : "此数值是当前 Key 剩余消费额度（USD），不是账户钱包余额。",
                List.of(), used, remaining, null, Instant.now(), console);
    }
    private BigDecimal decimal(JsonNode node) {
        if (!node.isNumber() && !node.isString()) { throw new IllegalArgumentException(); }
        String value = node.asString();
        if (value.length() > 40) { throw new IllegalArgumentException(); }
        BigDecimal number = new BigDecimal(value);
        if (number.precision() > 24 || Math.abs(number.scale()) > 12) { throw new IllegalArgumentException(); }
        return number;
    }
    private Balance unavailable(String status, String provider, String message, String console) {
        return new Balance(status, provider, "UNKNOWN", message, List.of(), null, null, null, Instant.now(), console);
    }
}
