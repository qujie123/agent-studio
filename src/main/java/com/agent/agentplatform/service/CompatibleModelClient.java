package com.agent.agentplatform.service;

import com.agent.agentplatform.dto.TaskExecution;
import com.agent.agentplatform.dto.ModelReply;
import com.agent.agentplatform.exception.ModelCallException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class CompatibleModelClient {
    private final ObjectMapper mapper;
    private final HttpClient client;

    public CompatibleModelClient(ObjectMapper mapper) {
        this.mapper = mapper;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public String complete(TaskExecution task, ModelSettingsService.Connection config) {
        return complete(task.model(), List.of(Map.of("role", "system", "content", task.systemPrompt()),
                Map.of("role", "user", "content", task.input())), config);
    }

    public String complete(String model, List<Map<String, String>> messages, ModelSettingsService.Connection config) {
        return completeReply(model, messages, config).content();
    }

    public ModelReply completeReply(String model, List<Map<String, String>> messages, ModelSettingsService.Connection config) {
        return completeReply(model,messages,config,null,null);
    }
    public ModelReply completeReply(String model, List<Map<String, String>> messages, ModelSettingsService.Connection config, Double temperature, Integer maxOutputTokens) {
        ModelSettingsService.validate(config);
        try {
            var request = ModelProtocol.completion(mapper, config, model, messages, temperature, maxOutputTokens, false);
            HttpResponse<String> response = client.send(request, LimitedResponseBody.handler(1024 * 1024));
            if (response.statusCode() != 200) {
                throw new ModelCallException("Model service returned HTTP " + response.statusCode());
            }
            return ModelProtocol.reply(mapper.readTree(response.body()), ModelProtocol.anthropic(config));
        } catch (HttpTimeoutException exception) {
            throw new ModelCallException("Model request timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ModelCallException("Model request was interrupted");
        } catch (IOException exception) {
            if (LimitedResponseBody.exceeded(exception)) { throw new ModelCallException("Model response exceeds the 1 MiB size limit"); }
            throw new ModelCallException("Could not connect to the model service");
        } catch (ModelCallException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            // Provider bodies may contain credentials or user text; never persist them as errors.
            throw new ModelCallException("Model service returned an invalid response");
        }
    }
}
