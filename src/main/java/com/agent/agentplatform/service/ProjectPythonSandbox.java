package com.agent.agentplatform.service;

import com.agent.agentplatform.exception.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.time.Instant;

@Service
public class ProjectPythonSandbox {
    public record Result(String output, String error, String video, boolean gui) {}
    private record Run(String owner, String fingerprint, Instant created, CompletableFuture<Result> result) {}
    private final boolean enabled; private final String docker, image, guiImage, runner; private final ObjectMapper mapper;
    private final Map<UUID, Run> runs = new LinkedHashMap<>(); private final Semaphore slots = new Semaphore(2);
    public ProjectPythonSandbox(@Value("${app.sandbox.enabled:false}") boolean enabled, @Value("${app.sandbox.docker:}") String docker,
            @Value("${app.sandbox.image:python@sha256:2d9aefe2fef018a7eb2c13064c89c71929800fd2e5dccdbf52ea5da5bb8d929a}") String image,
            @Value("${app.sandbox.gui-image:agent-platform-python-gui:1.7}") String guiImage, ObjectMapper mapper) {
        this.enabled = enabled; this.image = image; this.guiImage = guiImage; this.mapper = mapper;
        String local = System.getenv("LOCALAPPDATA"); Path candidate = local == null ? null : Path.of(local, "Programs", "DockerDesktop", "resources", "bin", "docker.exe");
        this.docker = docker.isBlank() ? candidate != null && Files.isRegularFile(candidate) ? candidate.toString() : "docker" : docker;
        try (var stream = getClass().getResourceAsStream("/project-preview.py")) {
            if (stream == null) { throw new IllegalStateException("Missing preview runner"); }
            this.runner = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) { throw new IllegalStateException("Cannot load preview runner", failure); }
    }
    public Result run(UUID requestId, String owner, ProjectDelivery.Project project, String entry, String input, boolean gui) {
        if (!enabled) { throw new ModelUnavailableException("管理员未启用容器预览（SANDBOX_ENABLED），禁止回退主机执行"); }
        ProjectDelivery.requireComplete(project); ProjectDelivery.validatePath(entry);
        if (!entry.endsWith(".py") || ProjectDelivery.find(project.files(), entry) == null) { throw new IllegalArgumentException("请选择交付中的 .py 入口"); }
        String payload = mapper.writeValueAsString(Map.of("files", project.files(), "entry", entry, "input", input, "gui", gui));
        String fingerprint;
        try { fingerprint = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException failure) { throw new IllegalStateException("SHA-256 unavailable", failure); }
        CompletableFuture<Result> future;
        synchronized (runs) {
            runs.entrySet().removeIf(e -> e.getValue().result().isDone() && e.getValue().created().isBefore(Instant.now().minusSeconds(600)));
            var previous = runs.get(requestId);
            if (previous != null) {
                if (!previous.owner().equals(owner) || !previous.fingerprint().equals(fingerprint)) { throw new ConflictException("预览请求标识不能用于不同项目或参数"); }
                future = previous.result();
            } else {
                if (runs.size() >= 32) { var oldest = runs.entrySet().stream().filter(e -> e.getValue().result().isDone()).findFirst(); if (oldest.isPresent()) { runs.remove(oldest.get().getKey()); } else { throw new ModelUnavailableException("预览队列繁忙，请稍后重试"); } }
                if (!slots.tryAcquire()) { throw new ModelUnavailableException("最多同时运行两个项目预览，请稍后重试"); }
                future = new CompletableFuture<>(); runs.put(requestId, new Run(owner, fingerprint, Instant.now(), future));
                final var result = future;
                Thread.ofVirtual().start(() -> { try { result.complete(execute(payload, gui)); } catch (RuntimeException failure) { result.complete(new Result("", "容器预览失败，请检查环境", null, gui)); } finally { slots.release(); } });
            }
        }
        try { return future.get(30, TimeUnit.SECONDS); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new ModelUnavailableException("等待预览已中断，同一请求可确认原结果"); }
        catch (ExecutionException | TimeoutException failure) { throw new ModelUnavailableException("预览结果尚未确认，请使用同一请求重试；不会重复运行"); }
    }
    private Result execute(String payload, boolean gui) {
        String name = "agent-preview-" + UUID.randomUUID().toString().replace("-", ""); Process process = null;
        var output = new ByteArrayOutputStream();
        try {
            String bootstrap = "import base64;exec(compile(base64.b64decode('" + Base64.getEncoder().encodeToString(runner.getBytes(StandardCharsets.UTF_8)) + "'),'<preview-runner>','exec'))";
            var command = new ArrayList<>(List.of(docker, "run", "--rm", "--pull=never", "--name", name, "--network=none", "--read-only", "--cap-drop=ALL",
                    "--security-opt=no-new-privileges", "--user=65534:65534", "--memory=256m", "--memory-swap=256m", "--cpus=1", "--pids-limit=128",
                    "--ulimit=nofile=128:128", "--tmpfs=/tmp:rw,noexec,nosuid,size=32m", "-i", gui ? guiImage : image, gui ? "python3" : "python", "-I", "-c", bootstrap));
            process = new ProcessBuilder(command).redirectErrorStream(true).start(); final var child = process;
            var reading = new CompletableFuture<Void>();
            Thread.ofVirtual().start(() -> {
                try {
                    try (var stdin = child.getOutputStream()) { stdin.write(payload.getBytes(StandardCharsets.UTF_8)); }
                    try (var stream = child.getInputStream()) { byte[] block = new byte[4096]; int n; while ((n = stream.read(block)) != -1) { if (output.size() + n > 2_000_000) { throw new IOException("Preview output too large"); } output.write(block, 0, n); } }
                    reading.complete(null);
                } catch (IOException failure) { reading.completeExceptionally(failure); }
            });
            reading.get(20, TimeUnit.SECONDS);
            if (!process.waitFor(1, TimeUnit.SECONDS)) { return new Result("", "预览运行超过 20 秒", null, gui); }
            if (process.exitValue() != 0) {
                String diagnostic = output.toString(StandardCharsets.UTF_8);
                return new Result(diagnostic.substring(0, Math.min(2000, diagnostic.length())), "容器预览未成功启动（退出码 " + process.exitValue() + "），请查看诊断；不会在主机执行", null, gui);
            }
            var json = mapper.readTree(output.toByteArray());
            return new Result(json.path("output").asString(""), json.path("error").isString() ? json.path("error").asString() : null,
                    json.path("video").isString() ? json.path("video").asString() : null, gui);
        } catch (IOException failure) { return new Result("", "未找到 Docker 或无法启动镜像，禁止回退主机执行", null, gui); }
        catch (TimeoutException failure) { return new Result("", "预览运行超过 20 秒，容器已终止", null, gui); }
        catch (ExecutionException | RuntimeException failure) { return new Result("", "预览输出无效或过大", null, gui); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); return new Result("", "容器预览已中断", null, gui); }
        finally {
            if (process != null) { process.destroyForcibly(); }
            try { var cleanup = new ProcessBuilder(docker, "rm", "-f", name).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start(); if (!cleanup.waitFor(3, TimeUnit.SECONDS)) { cleanup.destroyForcibly(); } }
            catch (IOException ignored) {} catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
        }
    }
}
