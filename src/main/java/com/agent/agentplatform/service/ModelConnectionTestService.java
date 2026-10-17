package com.agent.agentplatform.service;
import com.agent.agentplatform.dto.ModelReply;
import com.agent.agentplatform.exception.ModelCallException;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
public class ModelConnectionTestService {
    private final CompatibleModelClient client;
    private final UsageLedgerService usage;
    public ModelConnectionTestService(CompatibleModelClient client, UsageLedgerService usage) { this.client = client; this.usage = usage; }
    public Map<String, Object> test(ModelSettingsService.Connection config, String username) {
        ModelSettingsService.validate(config);
        Long id = usage.start("TEST", null, username, config.defaultModel(), config.mode());
        try {
            var reply = client.completeReply(config.defaultModel(), List.of(Map.of("role", "system", "content", "Reply with OK only."),
                    Map.of("role", "user", "content", "Connection test")), config);
            usage.finishTest(id, "SUCCEEDED", reply);
            return Map.of("success", true, "message", "连接成功，模型已返回有效文本");
        } catch (ModelCallException failure) {
            usage.finishTest(id, "FAILED", failure.getReply());
            return Map.of("success", false, "message", failure.getMessage());
        } catch (RuntimeException failure) {
            usage.finishTest(id, "FAILED", ModelReply.text(null));
            throw failure;
        }
    }
}
