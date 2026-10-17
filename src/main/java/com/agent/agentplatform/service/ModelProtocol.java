package com.agent.agentplatform.service;

import com.agent.agentplatform.dto.ModelReply;
import com.agent.agentplatform.exception.ModelCallException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.*;

// Wire differences shared by normal requests, SSE and model discovery.
final class ModelProtocol {
    private ModelProtocol() {}
    static boolean anthropic(ModelSettingsService.Connection config) { return "ANTHROPIC".equals(config.protocol()); }
    static HttpRequest.Builder request(ModelSettingsService.Connection config, String path, int timeout) {
        var builder = HttpRequest.newBuilder(URI.create(config.baseUrl() + path))
                .timeout(Duration.ofSeconds(timeout)).header("Content-Type", "application/json");
        return anthropic(config) ? builder.header("x-api-key", config.apiKey()).header("anthropic-version", "2023-06-01")
                : builder.header("Authorization", "Bearer " + config.apiKey());
    }
    static HttpRequest completion(ObjectMapper mapper, ModelSettingsService.Connection config, String model,
                                  List<Map<String,String>> messages, Double temperature, Integer maxTokens, boolean stream) {
        var parameters = new LinkedHashMap<String,Object>();
        parameters.put("model", model); parameters.put("stream", stream);
        if (anthropic(config)) {
            var system = new ArrayList<String>(); var turns = new ArrayList<Map<String,String>>();
            for (var message : messages) {
                if ("system".equals(message.get("role"))) { system.add(message.get("content")); }
                else { turns.add(message); }
            }
            if (!system.isEmpty()) parameters.put("system", String.join("\n\n", system));
            parameters.put("messages", turns);
            parameters.put("max_tokens", maxTokens == null ? 4096 : maxTokens);
        } else {
            parameters.put("messages", messages);
            if (stream) parameters.put("stream_options", Map.of("include_usage", true));
            if (maxTokens != null) parameters.put("max_tokens", maxTokens);
        }
        if (temperature != null) parameters.put("temperature", temperature);
        return request(config, anthropic(config) ? "/messages" : "/chat/completions", config.timeoutSeconds())
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(parameters))).build();
    }
    static ModelReply reply(JsonNode root, boolean anthropic) {
        String text; String finish;
        var usage = root.path("usage");
        if (anthropic) {
            var content = new StringBuilder();
            for (var block : root.path("content")) {
                if ("text".equals(block.path("type").asString()) && block.path("text").isString()) content.append(block.path("text").asString());
            }
            text = content.toString(); finish = root.path("stop_reason").asString("");
        } else {
            var choice = root.path("choices").path(0); var content = choice.path("message").path("content");
            text = content.isString() ? content.asString() : null; finish = choice.path("finish_reason").asString("");
        }
        var reply = new ModelReply(text, anthropic ? inputTokens(usage) : count(usage.path("prompt_tokens")),
                count(usage.path(anthropic ? "output_tokens" : "completion_tokens")));
        if (text == null || text.isBlank()) throw new ModelCallException("Model service returned no text answer", reply);
        if (!finished(finish, anthropic)) throw new ModelCallException("Model response was incomplete or requires unsupported tool calls", reply);
        return reply;
    }
    static boolean finished(String reason, boolean anthropic) { return anthropic ? "end_turn".equals(reason) || "stop_sequence".equals(reason) : "stop".equals(reason); }
    static Long count(JsonNode value) { return value.isIntegralNumber() && value.canConvertToLong() && value.asLong() >= 0 ? value.asLong() : null; }
    static Long inputTokens(JsonNode usage) {
        Long total = count(usage.path("input_tokens")); if (total == null) return null;
        for (String field : List.of("cache_creation_input_tokens", "cache_read_input_tokens")) {
            if (usage.has(field)) { Long extra = count(usage.path(field)); if (extra == null) return null; total = Math.addExact(total, extra); }
        }
        return total;
    }
}
