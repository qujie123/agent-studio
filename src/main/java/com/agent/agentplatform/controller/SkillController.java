package com.agent.agentplatform.controller;
import com.agent.agentplatform.dto.*;
import com.agent.agentplatform.service.SkillService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class SkillController {
    private final SkillService service;
    public SkillController(SkillService service) { this.service = service; }
    @GetMapping("/skills") public List<SkillResponse> mine(Authentication auth) { return service.page(auth.getName(),"",0,100).items(); }
    @GetMapping("/skills/page") public PageResult<SkillResponse> page(Authentication auth, @RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) { return service.page(auth.getName(),q,page,size); }
    @GetMapping("/agents/{id}/skills/available/page") public PageResult<SkillResponse> availablePage(@PathVariable Long id, Authentication auth,
            @RequestParam(defaultValue="") String q, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) { return service.availablePage(id,auth,q,page,size); }
    @GetMapping("/agents/{id}/skills/snapshot") public SkillService.BindingView snapshot(@PathVariable Long id, Authentication auth) { return service.bindingSnapshot(id,auth); }
    @PostMapping("/skills") @ResponseStatus(HttpStatus.CREATED)
    public SkillResponse create(@Valid @RequestBody SkillRequest request, Authentication auth) { return service.create(request, auth.getName()); }
    @PutMapping("/skills/{id}") public SkillResponse update(@PathVariable Long id, @Valid @RequestBody SkillRequest request, Authentication auth) { return service.update(id, request, auth); }
    @DeleteMapping("/skills/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication auth) { service.delete(id, auth); }
    @GetMapping("/agents/{id}/skills") public List<SkillResponse> bindings(@PathVariable Long id, Authentication auth) { return service.bindings(id, auth); }
    @GetMapping("/agents/{id}/skills/available") public List<SkillResponse> available(@PathVariable Long id, Authentication auth) { return service.availablePage(id,auth,"",0,100).items(); }
    @PutMapping("/agents/{id}/skills") public List<SkillResponse> bind(@PathVariable Long id, @Valid @RequestBody SkillBindingRequest request, Authentication auth) { return service.bind(id, request, auth); }
}
