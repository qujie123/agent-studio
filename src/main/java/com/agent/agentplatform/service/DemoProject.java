package com.agent.agentplatform.service;

import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.IOException;

final class DemoProject {
    private DemoProject() {}
    static String sample() {
        var files = new ArrayList<ProjectDelivery.File>();
        for (String path : List.of("index.html", "style.css", "app.js", "main.py", "requirements.txt", "README.md")) {
            try (var stream = DemoProject.class.getResourceAsStream("/delivery-demo/" + path)) {
                if (stream == null) { throw new IllegalStateException("Missing delivery sample"); }
                files.add(new ProjectDelivery.File(path, new String(stream.readAllBytes(), StandardCharsets.UTF_8)));
            } catch (IOException failure) { throw new IllegalStateException("Cannot load delivery sample", failure); }
        }
        return "【演示成品 · 固定样例，未调用真实大模型】\n\n```agent-project\n"
                + new ObjectMapper().writeValueAsString(Map.of("name", "demo-heart", "entrypoint", "index.html", "files", files)) + "\n```";
    }
}
