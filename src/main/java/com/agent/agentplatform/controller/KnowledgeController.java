package com.agent.agentplatform.controller;
import com.agent.agentplatform.dto.*;
import com.agent.agentplatform.dto.KnowledgeDtos.*;
import com.agent.agentplatform.service.KnowledgeService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;

@RestController @RequestMapping("/api")
public class KnowledgeController {
    private final KnowledgeService knowledge;
    public KnowledgeController(KnowledgeService knowledge){this.knowledge=knowledge;}
    @GetMapping("/knowledge/page") public PageResult<View> page(Principal user,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return knowledge.page(user.getName(),q,page,size);}
    @GetMapping("/knowledge/{id}") public View get(@PathVariable Long id,Principal user){return knowledge.get(id,user.getName());}
    @PostMapping("/knowledge") @ResponseStatus(HttpStatus.CREATED) public View create(@Valid @RequestBody Save request,Principal user){return knowledge.create(request,user.getName());}
    @PutMapping("/knowledge/{id}") public View update(@PathVariable Long id,@Valid @RequestBody Save request,Principal user){return knowledge.update(id,request,user.getName());}
    @DeleteMapping("/knowledge/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id,Principal user){knowledge.delete(id,user.getName());}
    @PostMapping("/knowledge/{id}/retrieve") public List<Match> retrieve(@PathVariable Long id,@Valid @RequestBody Query query,Principal user){return knowledge.retrieve(id,query.query(),user.getName());}
    @GetMapping("/agents/{id}/knowledge") public Binding binding(@PathVariable Long id,Principal user){return knowledge.binding(id,user.getName());}
    @PutMapping("/agents/{id}/knowledge") public Binding bind(@PathVariable Long id,@Valid @RequestBody Bind request,Principal user){return knowledge.bind(id,request,user.getName());}
}
