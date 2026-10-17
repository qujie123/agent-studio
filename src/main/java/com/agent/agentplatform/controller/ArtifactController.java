package com.agent.agentplatform.controller;

import com.agent.agentplatform.service.*;
import com.agent.agentplatform.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/artifacts")
public class ArtifactController {
    public record Preview(@NotBlank @Size(max=240) String entrypoint) {}
    public record PythonPreview(@NotNull UUID requestId, @NotBlank @Size(max=240) String entrypoint,
                                @NotNull @Size(max=4000) String input, boolean gui) {}
    private final ArtifactAccess access; private final ProjectDelivery delivery; private final ArtifactPreview previews; private final ProjectPythonSandbox python;
    public ArtifactController(ArtifactAccess access, ProjectDelivery delivery, ArtifactPreview previews, ProjectPythonSandbox python) { this.access=access; this.delivery=delivery; this.previews=previews; this.python=python; }
    @GetMapping({"/tasks/{taskId}", "/chats/{chatId}/turns/{turnId}"})
    public ProjectDelivery.Project project(@PathVariable(required=false) Long taskId, @PathVariable(required=false) Long chatId, @PathVariable(required=false) Long turnId, Authentication user) {
        return access.project(new ArtifactAccess.Source(taskId, chatId, turnId), user);
    }
    @GetMapping({"/tasks/{taskId}/archive", "/chats/{chatId}/turns/{turnId}/archive"})
    public ResponseEntity<byte[]> archive(@PathVariable(required=false) Long taskId, @PathVariable(required=false) Long chatId, @PathVariable(required=false) Long turnId, Authentication user) {
        var project = project(taskId, chatId, turnId, user);
        return attachment(project.name()+".zip", "application/zip", delivery.archive(project));
    }
    @GetMapping({"/tasks/{taskId}/files", "/chats/{chatId}/turns/{turnId}/files"})
    public ResponseEntity<byte[]> file(@PathVariable(required=false) Long taskId, @PathVariable(required=false) Long chatId, @PathVariable(required=false) Long turnId,
                                       @RequestParam String path, Authentication user) {
        ProjectDelivery.validatePath(path); var project = project(taskId, chatId, turnId, user); ProjectDelivery.requireComplete(project);
        var file = ProjectDelivery.find(project.files(), path); if (file == null) { throw new ResourceNotFoundException("Project file not found"); }
        return attachment(path.substring(path.lastIndexOf('/')+1), "application/octet-stream", file.content().getBytes(StandardCharsets.UTF_8));
    }
    @PostMapping({"/tasks/{taskId}/preview", "/chats/{chatId}/turns/{turnId}/preview"})
    public Map<String,String> preview(@PathVariable(required=false) Long taskId, @PathVariable(required=false) Long chatId, @PathVariable(required=false) Long turnId,
                                       @Valid @RequestBody Preview request, Authentication user) {
        return Map.of("url", previews.create(new ArtifactAccess.Source(taskId, chatId, turnId), request.entrypoint(), user));
    }
    @PostMapping({"/tasks/{taskId}/python-preview", "/chats/{chatId}/turns/{turnId}/python-preview"})
    public ProjectPythonSandbox.Result python(@PathVariable(required=false) Long taskId, @PathVariable(required=false) Long chatId, @PathVariable(required=false) Long turnId,
                                               @Valid @RequestBody PythonPreview request, Authentication user) {
        return python.run(request.requestId(), user.getName(), project(taskId, chatId, turnId, user), request.entrypoint(), request.input(), request.gui());
    }
    private ResponseEntity<byte[]> attachment(String filename, String type, byte[] bytes) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(type)).body(bytes);
    }
}
