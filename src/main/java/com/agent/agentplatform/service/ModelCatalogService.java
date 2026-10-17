package com.agent.agentplatform.service;

import org.springframework.stereotype.Service;
import com.agent.agentplatform.exception.ModelCallException;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

@Service
public class ModelCatalogService {
    public record Catalog(List<String> models, boolean truncated) {}
    private final ObjectMapper mapper;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    public ModelCatalogService(ObjectMapper mapper) { this.mapper=mapper; }
    public Catalog read(ModelSettingsService.Connection config) {
        if (!"API".equals(config.mode())) { throw new IllegalArgumentException("请先保存真实 API 配置"); }
        ModelSettingsService.validate(config);
        try {
            var request=ModelProtocol.request(config,"/models",Math.min(config.timeoutSeconds(),20)).GET().build();
            var response=client.send(request,LimitedResponseBody.handler(1024*1024));
            if (response.statusCode()!=200) { throw failure("服务商模型列表返回 HTTP "+response.statusCode()+"；可继续手动填写模型 ID"); }
            var root=mapper.readTree(response.body());var data=root.path("data");
            if (!data.isArray()) { throw failure("服务商未返回兼容的模型列表；可继续手动填写模型 ID"); }
            var models=new TreeSet<String>();boolean truncated=root.path("has_more").asBoolean(false);
            for (var item:data) {
                var id=item.path("id");if (!id.isString()) { continue; }
                String value=id.asString().strip();
                if (value.isEmpty()||value.length()>100||value.chars().anyMatch(Character::isISOControl)) { continue; }
                models.add(value);
                if (models.size()>500) { models.pollLast();truncated=true;break; }
            }
            return new Catalog(List.copyOf(models),truncated);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();throw failure("模型列表请求已中断");
        } catch (IOException error) {
            throw failure(LimitedResponseBody.exceeded(error)?"模型列表超过 1 MiB，已停止接收":"无法读取模型列表，请检查服务商连接");
        } catch (ModelCallException error) { throw error; }
        catch (RuntimeException error) { throw failure("服务商返回无效的模型列表；可继续手动填写模型 ID"); }
    }
    private ModelCallException failure(String message) { return new ModelCallException(message); }
}
