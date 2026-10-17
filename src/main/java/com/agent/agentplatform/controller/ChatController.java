package com.agent.agentplatform.controller;
import com.agent.agentplatform.dto.ChatDtos.*;
import com.agent.agentplatform.service.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/chats")
public class ChatController {
    private final ChatService chats;
    private final ChatExecutionService execution;
    private final ChatStreamingService streaming;
    public ChatController(ChatService chats, ChatExecutionService execution,ChatStreamingService streaming) { this.chats = chats; this.execution = execution;this.streaming=streaming; }
    @GetMapping public List<Session> list(Principal user) { return chats.page(user.getName(),"",0,100).items(); }
    @GetMapping("/page") public com.agent.agentplatform.dto.PageResult<Session> page(Principal user, @RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="active") String view,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) { return chats.page(user.getName(),q,view,page,size); }
    @GetMapping("/{id}/turns") public com.agent.agentplatform.dto.PageResult<Turn> turns(@PathVariable Long id, Principal user,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) Long beforeId) { return chats.turnsPage(id,user.getName(),page,size,beforeId); }
    @GetMapping("/{id}/session") public Session session(@PathVariable Long id, Principal user) { return chats.session(id,user.getName()); }
    @PostMapping public Session create(@Valid @RequestBody Create request, Principal user) { return chats.create(request.agentId(), user.getName()); }
    @GetMapping("/{id}") public Detail detail(@PathVariable Long id, Principal user) {
        var session=chats.session(id,user.getName());
        var items=new java.util.ArrayList<>(chats.turnsPage(id,user.getName(),0,100,null).items());
        java.util.Collections.reverse(items); return new Detail(session,java.util.List.copyOf(items));
    }
    @PatchMapping("/{id}") public Session rename(@PathVariable Long id, @Valid @RequestBody Rename request, Principal user) { return chats.rename(id, request.title(), user.getName()); }
    @DeleteMapping("/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Principal user) { chats.delete(id, user.getName()); }
    @PatchMapping("/{id}/preferences") public Session preferences(@PathVariable Long id, @Valid @RequestBody Preferences request, Principal user) { return chats.preferences(id,request,user.getName()); }
    @PostMapping("/{id}/messages") public Turn send(@PathVariable Long id, @Valid @RequestBody Send request, Principal user) { return execution.send(id, request, user.getName()); }
    @PostMapping(value="/{id}/messages/stream",produces="text/event-stream") public org.springframework.web.servlet.mvc.method.annotation.SseEmitter stream(@PathVariable Long id,@Valid @RequestBody Send request,Principal user){return streaming.send(id,request,user.getName());}
}
