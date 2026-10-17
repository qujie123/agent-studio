package com.agent.agentplatform.controller;

import com.agent.agentplatform.service.ArtifactPreview;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class ArtifactPreviewController {
    private final ArtifactPreview previews;
    public ArtifactPreviewController(ArtifactPreview previews) { this.previews = previews; }
    @GetMapping("/artifact-preview/{token}")
    public ResponseEntity<String> preview(@PathVariable String token) {
        return ResponseEntity.ok().header("Content-Security-Policy", ArtifactPreview.CSP).header("X-Frame-Options", "SAMEORIGIN")
                .header("Referrer-Policy", "no-referrer").cacheControl(CacheControl.noStore()).contentType(MediaType.TEXT_HTML).body(previews.load(token));
    }
}
