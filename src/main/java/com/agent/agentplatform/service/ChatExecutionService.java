package com.agent.agentplatform.service;
import com.agent.agentplatform.dto.ChatDtos.*;
import com.agent.agentplatform.exception.ModelCallException;
import org.springframework.stereotype.Service;

@Service
public class ChatExecutionService {
    private final ChatService chats;
    private final CompatibleModelClient client;
    public ChatExecutionService(ChatService chats, CompatibleModelClient client) { this.chats = chats; this.client = client; }
    // Network I/O is outside the database transaction. A request UUID makes transport retries idempotent.
    public Turn send(Long id, Send request, String username) {
        var pending = chats.begin(id, request, username);
        if (pending.connection() == null) { return pending.turn(); }
        com.agent.agentplatform.dto.ModelReply output = com.agent.agentplatform.dto.ModelReply.text(null);
        String error = null;
        try {
            output = "DEMO".equals(pending.connection().mode())
                    ? com.agent.agentplatform.dto.ModelReply.text(pending.turn().projectMode() ? ProjectDelivery.demo() : "【模拟回复 · 未调用真实大模型】\n本轮携带 " + pending.turn().contextTurns()
                    + " 轮历史。\n你的消息：" + pending.turn().input()
                    + "\n请在模型服务中配置 API Key 并切换 API 模式，以获得真实回答。")
                    : client.completeReply(pending.turn().model(), pending.messages(), pending.connection(),pending.temperature(),pending.maxOutputTokens());
        } catch (ModelCallException failure) { output = failure.getReply(); error = failure.getMessage(); }
        catch (RuntimeException failure) { error = "模型调用失败，请检查模型配置后重试"; }
        return chats.finishReply(id, pending.turn().id(), output, error, username);
    }
}
