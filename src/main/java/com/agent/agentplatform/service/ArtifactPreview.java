package com.agent.agentplatform.service;

import com.agent.agentplatform.repository.UserRepository;
import com.agent.agentplatform.exception.ResourceNotFoundException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.regex.*;

@Service
public class ArtifactPreview {
    public static final String CSP = "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data:; font-src data:; media-src data:; connect-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'; sandbox allow-scripts; frame-ancestors 'self'";
    private record Ticket(String owner, long tokenVersion, ArtifactAccess.Source source, String html, Instant deadline) {}
    private final Map<String, Ticket> tickets = new LinkedHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final ArtifactAccess access; private final UserRepository users;
    public ArtifactPreview(ArtifactAccess access, UserRepository users) { this.access = access; this.users = users; }
    public synchronized String create(ArtifactAccess.Source source, String entry, Authentication auth) {
        var project = access.project(source, auth); ProjectDelivery.requireComplete(project);
        String html = html(project, entry);
        tickets.entrySet().removeIf(e -> e.getValue().deadline().isBefore(Instant.now()) || e.getValue().owner().equals(auth.getName()));
        while (tickets.size() >= 32) { tickets.remove(tickets.keySet().iterator().next()); }
        var user = users.findByUsername(auth.getName()).orElseThrow(() -> new ResourceNotFoundException("Preview unavailable"));
        byte[] bytes = new byte[32]; random.nextBytes(bytes); String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tickets.put(token, new Ticket(auth.getName(), user.getTokenVersion(), source, html, Instant.now().plusSeconds(300)));
        return "/artifact-preview/" + token;
    }
    public synchronized String load(String token) {
        Ticket ticket = tickets.get(token);
        if (ticket == null || ticket.deadline().isBefore(Instant.now())) { tickets.remove(token); throw new ResourceNotFoundException("预览已过期，请重新点击预览"); }
        var user = users.findByUsername(ticket.owner()).orElseThrow(() -> new ResourceNotFoundException("Preview unavailable"));
        if (!user.isEnabled() || user.getTokenVersion() != ticket.tokenVersion()) { tickets.remove(token); throw new ResourceNotFoundException("Preview unavailable"); }
        var auth = UsernamePasswordAuthenticationToken.authenticated(user.getUsername(), null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
        access.project(ticket.source(), auth); // A deleted conversation or revoked role invalidates its ticket.
        return ticket.html();
    }
    public String html(ProjectDelivery.Project project, String entry) {
        ProjectDelivery.validatePath(entry);
        var file = ProjectDelivery.find(project.files(), entry);
        if (file == null || !(entry.endsWith(".html") || entry.endsWith(".htm") || entry.endsWith(".svg"))) { throw new IllegalArgumentException("请选择交付中的 HTML 或 SVG 入口；框架源码须先构建"); }
        var doc = Jsoup.parse(file.content()); doc.outputSettings().prettyPrint(false);
        if (doc.select("[src],link[rel=stylesheet],style,[style]").size() > 100) { throw new IllegalArgumentException("预览资源引用过多，请缩小页面"); }
        doc.select("base, meta[http-equiv], iframe, object, embed").remove();
        for (var link : doc.select("link")) {
            if (link.attr("rel").equalsIgnoreCase("stylesheet")) {
                String path = resolve(entry, link.attr("href"));
                var css = required(project, path); var style = new Element("style");
                style.appendChild(new DataNode(css(project, path, css.content(), new HashSet<>()))); link.replaceWith(style);
            } else { link.remove(); }
        }
        for (var script : doc.select("script[src]")) {
            String path = resolve(entry, script.attr("src")); String content = required(project, path).content();
            if (script.attr("type").equalsIgnoreCase("module") && Pattern.compile("\\bimport\\s*(?:[\\{'\"]|[^;]+\\bfrom\\b)").matcher(content).find()) { throw new IllegalArgumentException("模块依赖项目需要先构建为独立 HTML/JS 后预览"); }
            script.removeAttr("src"); script.removeAttr("integrity"); script.empty();
            script.appendChild(new DataNode(content.replaceAll("(?i)</script", "<\\\\/script")));
        }
        for (var style : doc.select("style")) { style.html(css(project, entry, style.data(), new HashSet<>())); }
        for (var element : doc.select("[style]")) { element.attr("style", css(project, entry, element.attr("style"), new HashSet<>())); }
        for (var element : doc.select("[src], [poster], [srcset]")) {
            element.removeAttr("srcset");
            for (String attr : List.of("src", "poster")) {
                if (!element.hasAttr(attr)) { continue; }
                String ref = element.attr(attr);
                if (!ref.startsWith("data:")) { element.attr(attr, data(project, resolve(entry, ref))); }
            }
        }
        for (var element : doc.select("a, form")) { element.removeAttr("href"); element.removeAttr("action"); element.removeAttr("target"); }
        doc.head().appendElement("meta").attr("charset", "utf-8");
        doc.head().prependChild(new Element("script").appendChild(new DataNode("window.addEventListener('error',e=>parent.postMessage({type:'agent-preview-error',message:String(e.message).slice(0,500)},'*'));window.addEventListener('unhandledrejection',e=>parent.postMessage({type:'agent-preview-error',message:String(e.reason).slice(0,500)},'*'));")));
        String html = doc.outerHtml();
        if (html.length() > 2_000_000) { throw new IllegalArgumentException("内联后的预览页面过大，请减少重复资源"); }
        return html;
    }
    private ProjectDelivery.File required(ProjectDelivery.Project project, String path) {
        var file = ProjectDelivery.find(project.files(), path);
        if (file == null) { throw new IllegalArgumentException("预览缺少文件：" + path + "，请让智能体补齐后重新交付"); }
        return file;
    }
    private String resolve(String base, String ref) {
        try {
            var uri = URI.create(ref);
            if (uri.isAbsolute() || uri.getRawAuthority() != null || ref.startsWith("/") || ref.contains("\\")) { throw new IllegalArgumentException(); }
            String path = URI.create(base).resolve(uri).normalize().getPath(); ProjectDelivery.validatePath(path); return path;
        } catch (IllegalArgumentException failure) { throw new IllegalArgumentException("隔离预览不加载外部地址或不安全路径，请把资源包含在项目中"); }
    }
    private String data(ProjectDelivery.Project project, String path) {
        if (!path.toLowerCase(Locale.ROOT).endsWith(".svg")) { throw new IllegalArgumentException("当前文本项目预览的本地图片仅支持 SVG：" + path); }
        return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(required(project, path).content().getBytes(StandardCharsets.UTF_8));
    }
    private String css(ProjectDelivery.Project project, String base, String content, Set<String> importing) {
        var imports = Pattern.compile("@import\\s+(?:url\\(\\s*)?['\"]([^'\"]+)['\"]\\s*\\)?\\s*;", Pattern.CASE_INSENSITIVE).matcher(content);
        var expanded = new StringBuilder();
        while (imports.find()) {
            String path = resolve(base, imports.group(1)); if (!importing.add(path) || importing.size() > 20) { throw new IllegalArgumentException("CSS 引用循环或过多"); }
            imports.appendReplacement(expanded, Matcher.quoteReplacement(css(project, path, required(project, path).content(), importing))); importing.remove(path);
        }
        imports.appendTail(expanded); content = expanded.toString();
        if (content.toLowerCase(Locale.ROOT).contains("@import")) { throw new IllegalArgumentException("请将 CSS @import 展开为本地文件后预览"); }
        var urls = Pattern.compile("url\\(\\s*(['\"]?)([^)'\"]+)\\1\\s*\\)", Pattern.CASE_INSENSITIVE).matcher(content); var out = new StringBuilder();
        while (urls.find()) { String ref = urls.group(2).strip(); String value = ref.startsWith("data:") || ref.startsWith("#") ? ref : data(project, resolve(base, ref)); urls.appendReplacement(out, Matcher.quoteReplacement("url(\"" + value + "\")")); }
        urls.appendTail(out); return out.toString();
    }
}
