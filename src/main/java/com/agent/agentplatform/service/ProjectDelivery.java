package com.agent.agentplatform.service;

import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

@Service
public class ProjectDelivery {
    public static final String MARKER = "[AGENT_PROJECT_V1]";
    public static final String INSTRUCTIONS = """


        [AGENT_PROJECT_V1]
        本轮要求完整项目交付。先简短说明，再输出一个闭合的 agent-project 代码块，内容为合法 JSON：
        {"name":"project-name","entrypoint":"index.html","files":[{"path":"index.html","content":"完整文件内容"}]}
        列出每个文件的相对路径和完整原文，保留正确扩展名、目录、依赖清单及 README 运行说明。
        不要输出省略号、待补全文件或补丁。最多 100 个 UTF-8 文本文件，总内容不超过 1 MiB。
        网页优先使用无外部 CDN 的 HTML/CSS/JS，提供引用的本地资源。框架源码不能冒充已编译网页。
        Python 交付真正的 .py 和 requirements.txt，明确启动入口；桌面预览支持 Tkinter/Pygame/Matplotlib/Pillow。
        不要用 HTML 动画冒充 Python 执行结果，不要访问平台凭据或自动修改用户电脑。
        路径不能包含绝对路径、.. 或 Windows 保留名称。不能声称未运行的代码已验收。
        """;
    public record File(String path, String content) {}
    public record Project(String name, String entrypoint, List<File> files, boolean complete, List<String> warnings) {}
    private final ObjectMapper mapper;
    public ProjectDelivery(ObjectMapper mapper) { this.mapper = mapper; }
    public Project parse(String output, String status) {
        if (output == null || output.isBlank()) { return new Project("agent-project", "", List.of(), false, List.of("尚无项目文件")); }
        if (output.length() > 2_000_000) { throw new IllegalArgumentException("项目响应过大，请缩小项目后重新生成"); }
        var files = new ArrayList<File>(); var warnings = new ArrayList<String>();
        String name = "agent-project", entry = ""; boolean closed = true, structured = false;
        var fence = Pattern.compile("(?m)^[ \\t]*(`{3,}|~{3,})([^\\r\\n]*)\\r?\\n").matcher(output);
        int end = 0;
        while (fence.find(end)) {
            String marker = fence.group(1), info = fence.group(2).strip();
            var close = Pattern.compile("(?m)^[ \\t]*" + Pattern.quote(marker.substring(0, 1)) + "{" + marker.length() + ",}[ \\t]*\\r?$").matcher(output);
            if (!close.find(fence.end())) { closed = false; warnings.add("代码块未闭合，项目可能被截断；请补全后下载"); break; }
            String content = output.substring(fence.end(), close.start());
            if (content.endsWith("\r\n")) { content = content.substring(0, content.length() - 2); }
            else if (content.endsWith("\n")) { content = content.substring(0, content.length() - 1); }
            int start = fence.start(); end = close.end();
            if (info.equalsIgnoreCase("agent-project")) {
                if (structured || !files.isEmpty()) { throw new IllegalArgumentException("一轮只能交付一个项目清单"); }
                tools.jackson.databind.JsonNode json;
                try { json = mapper.readTree(content); } catch (RuntimeException failure) { throw new IllegalArgumentException("项目 JSON 不完整或格式错误，请让智能体重新交付完整清单"); }
                if (!json.isObject() || !json.path("files").isArray()) { throw new IllegalArgumentException("项目 JSON 缺少 files 数组"); }
                name = json.path("name").asString("agent-project"); entry = json.path("entrypoint").asString("");
                for (var file : json.path("files")) {
                    if (!file.path("path").isString() || !file.path("content").isString()) { throw new IllegalArgumentException("项目文件必须包含文本 path 与 content"); }
                    files.add(new File(file.path("path").asString(), file.path("content").asString()));
                    if (files.size() > 100) { throw new IllegalArgumentException("项目最多交付 100 个文件"); }
                }
                structured = true;
            } else if (!structured) {
                String path = filename(info);
                if (path == null) {
                    String preceding = output.substring(Math.max(0, start - 240), start).stripTrailing();
                    var heading = Pattern.compile("(?:^|\\n)[ \\t]*#{1,6}[ \\t]+`?([^`\\n]+\\.[A-Za-z0-9]+)`?[ \\t]*$").matcher(preceding);
                    if (heading.find()) { path = heading.group(1).strip(); }
                }
                if (path == null) { path = inferred(info.split("\\s+", 2)[0], files.size()); }
                if (path != null) { files.add(new File(path, content)); }
            }
        }
        if (output.contains("```agent-project") && !structured) { closed = false; }
        if (files.isEmpty() && output.stripLeading().matches("(?is)^(<!doctype\\s+html|<html\\b).*")) { files.add(new File("index.html", output.strip())); }
        validate(files);
        if (files.isEmpty()) { warnings.add("回复只有文本，没有可交付文件；请勾选完整项目交付重新生成"); }
        if (!structured && !files.isEmpty()) { warnings.add("从旧回复代码块提取：文件名可能为推断值，不保证这些片段构成完整工程"); }
        if (!"SUCCEEDED".equals(status)) { warnings.add("生成未成功结束，不能将部分响应作为完整项目下载"); }
        if (!entry.isBlank()) { validatePath(entry); if (find(files, entry) == null) { throw new IllegalArgumentException("入口未包含在交付文件中"); } }
        else { entry = files.stream().filter(f -> f.path().equals("index.html")).map(File::path).findFirst().orElseGet(() -> files.stream().filter(f -> f.path().endsWith(".html") || f.path().endsWith(".py")).map(File::path).findFirst().orElse("")); }
        name = name.replaceAll("[^\\p{L}\\p{N}_-]", "-");
        if (name.isBlank() || name.length() > 80 || reserved(name)) { name = "agent-project"; }
        if (!files.isEmpty() && files.stream().noneMatch(f -> f.path().equalsIgnoreCase("AGENT-DELIVERY.md"))) {
            files.add(new File("AGENT-DELIVERY.md", "# 项目交付说明\n\n入口：" + (entry.isBlank() ? "参照原项目 README" : entry)
                    + "\n\n文件保留原始后缀和 UTF-8 编码，无需改名。HTML 用浏览器打开；Python 需要 Python 及 requirements.txt 中的依赖。\n"
                    + "下载不包含 API Key、平台账号或运行环境。预览不能临时安装依赖。\n"
                    + "网页在隔离 iframe 执行；Python 桌面预览为容器真实录屏，不是交互窗口。\n"
                    + "生成结束不等于测试通过。框架和服务端工程需要按 README 安装构建，不能直接作为静态网页预览。\n"
                    + (warnings.isEmpty() ? "" : "\n注意：\n- " + String.join("\n- ", warnings) + "\n")));
        }
        return new Project(name, entry, List.copyOf(files), closed && !files.isEmpty() && "SUCCEEDED".equals(status), List.copyOf(warnings));
    }
    private String filename(String info) {
        var match = Pattern.compile("(?:^|\\s)(?:filename=|file=|path=)?[\"`]?([^\\s\"`]+\\.[A-Za-z0-9]+)[\"`]?(?:$|\\s)").matcher(info);
        return match.find() ? match.group(1) : null;
    }
    public static String inferred(String language, int index) {
        String path = switch (language.toLowerCase(Locale.ROOT)) {
            case "python", "py" -> "main.py"; case "html" -> "index.html"; case "css" -> "style.css";
            case "javascript", "js" -> "app.js"; case "typescript", "ts" -> "app.ts"; case "jsx" -> "App.jsx";
            case "tsx" -> "App.tsx"; case "java" -> "Main.java"; case "json" -> "data.json"; case "svg" -> "image.svg";
            case "bash", "sh" -> "run.sh"; case "powershell", "ps1" -> "run.ps1"; case "sql" -> "query.sql";
            case "yaml", "yml" -> "config.yml"; case "markdown", "md" -> "README.md"; default -> null;
        };
        if (path != null && index > 0) { path = "snippet-" + (index + 1) + path.substring(path.lastIndexOf('.')); }
        return path;
    }
    public static void validatePath(String path) {
        if (path == null || path.isBlank() || path.length() > 240 || !Normalizer.isNormalized(path, Normalizer.Form.NFC)
                || path.matches("(?s).*[\\\\\\x00-\\x1f\\x7f:*?<>|\"].*") || path.startsWith("/")) { throw new IllegalArgumentException("项目文件路径不安全"); }
        for (String part : path.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..") || part.endsWith(".") || part.endsWith(" ") || reserved(part)) { throw new IllegalArgumentException("项目路径包含无效或 Windows 保留名称"); }
        }
    }
    private static boolean reserved(String name) { return name.toUpperCase(Locale.ROOT).matches("(?:CON|PRN|AUX|NUL|COM[1-9¹²³]|LPT[1-9¹²³])(?:\\..*)?"); }
    private void validate(List<File> files) {
        if (files.size() > 100) { throw new IllegalArgumentException("项目最多交付 100 个文件"); }
        var paths = new HashSet<String>(); long bytes = 0;
        for (var file : files) {
            validatePath(file.path()); bytes += file.content().getBytes(StandardCharsets.UTF_8).length;
            if (bytes > 1_048_576) { throw new IllegalArgumentException("项目文本总大小超过 1 MiB"); }
            if (!paths.add(file.path().toLowerCase(Locale.ROOT))) { throw new IllegalArgumentException("项目包含重复文件名（Windows 不区分大小写）"); }
        }
        for (String path : paths) { int slash = path.indexOf('/'); while (slash >= 0) { if (paths.contains(path.substring(0, slash))) { throw new IllegalArgumentException("项目文件与目录名称冲突"); } slash = path.indexOf('/', slash + 1); } }
    }
    public static File find(List<File> files, String path) { return files.stream().filter(f -> f.path().equals(path)).findFirst().orElse(null); }
    public byte[] archive(Project project) {
        requireComplete(project);
        try (var output = new ByteArrayOutputStream(); var zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (var file : project.files()) { zip.putNextEntry(new ZipEntry(project.name() + "/" + file.path())); zip.write(file.content().getBytes(StandardCharsets.UTF_8)); zip.closeEntry(); }
            zip.finish(); return output.toByteArray();
        } catch (IOException failure) { throw new IllegalStateException("项目打包失败", failure); }
    }
    public static void requireComplete(Project project) { if (!project.complete()) { throw new com.agent.agentplatform.exception.ConflictException("项目尚未完整生成，请补全后再预览或下载"); } }
    public static String demo() { return DemoProject.sample(); }
}
