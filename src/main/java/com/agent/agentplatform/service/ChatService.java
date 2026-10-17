package com.agent.agentplatform.service;

import com.agent.agentplatform.dto.ChatDtos.*;
import com.agent.agentplatform.entity.*;
import com.agent.agentplatform.exception.*;
import com.agent.agentplatform.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class ChatService {
    public record Pending(Turn turn, List<Map<String, String>> messages, ModelSettingsService.Connection connection, Double temperature, Integer maxOutputTokens) {
        public Pending(Turn turn, List<Map<String,String>> messages, ModelSettingsService.Connection connection) { this(turn,messages,connection,null,null); }
        @Override public String toString() { return "ChatExecution[content and credentials redacted]"; }
    }
    private final ChatSessionRepository sessions;
    private final ChatTurnRepository turns;
    private final AgentService agents;
    private final UserService users;
    private final ModelSettingsService settings;
    private final AuditService audit;
    private final UsageLedgerService usage;
    private final KnowledgeService knowledge;
    public ChatService(ChatSessionRepository sessions, ChatTurnRepository turns, AgentService agents,
                       UserService users, ModelSettingsService settings, AuditService audit, UsageLedgerService usage, KnowledgeService knowledge) {
        this.sessions = sessions; this.turns = turns; this.agents = agents;
        this.users = users; this.settings = settings; this.audit = audit;
        this.usage = usage;this.knowledge=knowledge;
    }
    public List<Session> list(String username) {
        return sessions.findByOwnerUsernameOrderByUpdatedAtDescIdDesc(username).stream().map(Session::from).toList();
    }
    public com.agent.agentplatform.dto.PageResult<Session> page(String username, String q, int page, int size) {
        return page(username,q,"active",page,size);
    }
    public com.agent.agentplatform.dto.PageResult<Session> page(String username, String q, String view, int page, int size) {
        if (!Set.of("active","archived","pinned","all").contains(view)) { throw new IllegalArgumentException("Invalid conversation view"); }
        return com.agent.agentplatform.dto.PageResult.from(sessions.search(username, com.agent.agentplatform.dto.PageResult.query(q),
                view,com.agent.agentplatform.dto.PageResult.request(page,size)), Session::from);
    }
    @Transactional
    public com.agent.agentplatform.dto.PageResult<Turn> turnsPage(Long id, String username, int page, int size, Long beforeId) {
        ChatSession session = owned(id,username); recover(session);
        if (beforeId != null && beforeId < 1) { throw new IllegalArgumentException("Invalid message anchor"); }
        return com.agent.agentplatform.dto.PageResult.from(turns.page(id,beforeId,
                com.agent.agentplatform.dto.PageResult.request(page,size)), Turn::from);
    }
    @Transactional public Session session(Long id, String username) {
        var session = owned(id,username); recover(session); var view = Session.from(session);
        return new Session(view.id(),view.title(),view.agentId(),view.agentName(),view.agentStatus(),view.updatedAt(),turns.existsBySessionIdAndStatus(id,"RUNNING"),view.pinned(),view.archived());
    }
    @Transactional
    public Session create(Long agentId, String username) {
        Agent agent = agents.findById(agentId); requireActive(agent);
        ChatSession session = new ChatSession(); session.setAgent(agent);
        session.setOwner(users.findByUsername(username)); session.setTitle("新对话");
        sessions.saveAndFlush(session); audit.record(username, "CHAT_CREATED", "CHAT", session.getId());
        return Session.from(session);
    }
    @Transactional
    public Detail detail(Long id, String username) {
        ChatSession session = owned(id, username); recover(session);
        return new Detail(Session.from(session), turns.findBySessionIdOrderByIdAsc(id).stream().map(Turn::from).toList());
    }
    @Transactional
    public Session rename(Long id, String title, String username) {
        ChatSession session = owned(id, username); session.setTitle(title.strip()); session.touch();
        return Session.from(session);
    }
    @Transactional
    public Session preferences(Long id, Preferences request, String username) {
        ChatSession session=owned(id,username);recover(session);
        if (request.archived() && !session.isArchived()) { requireIdle(id); }
        session.setPinned(request.pinned());session.setArchived(request.archived());
        audit.record(username,"CHAT_PREFERENCES_UPDATED","CHAT",id);
        return Session.from(session);
    }
    @Transactional
    public void delete(Long id, String username) {
        ChatSession session = owned(id, username); recover(session); requireIdle(id);
        turns.deleteBySessionId(id); turns.flush(); sessions.delete(session);
        audit.record(username, "CHAT_DELETED", "CHAT", id);
    }
    @Transactional
    public Pending begin(Long id, Send request, String username) {
        ChatSession session = owned(id, username); recover(session);
        var existing = turns.findBySessionIdAndRequestId(id, request.requestId().toString());
        if (existing.isPresent()) {
            if (!existing.get().getInput().equals(request.input().strip()) || existing.get().isProjectMode() != request.projectMode()) {
                throw new ConflictException("同一请求标识不能用于不同消息");
            }
            return new Pending(Turn.from(existing.get()), List.of(), null);
        }
        requireIdle(id);
        if (session.isArchived()) { throw new ConflictException("会话已归档，请恢复后再发送消息"); }
        Agent agent = session.getAgent(); requireActive(agent);
        var config = settings.connectionForUser(username);
        if ("API".equals(config.mode())) { ModelSettingsService.validate(config); }
        StringBuilder prompt = new StringBuilder(agent.getSystemPrompt()), skillNames = new StringBuilder();
        for (var skill : agent.getSkills()) {
            if (!skill.isEnabled()||!"PROMPT".equals(skill.getKind())) { continue; }
            prompt.append("\n\n## Skill: ").append(skill.getName()).append("\n").append(skill.getInstructions());
            if (!skillNames.isEmpty()) { skillNames.append("\n"); }
            skillNames.append(skill.getName()).append(" · v").append(skill.getVersion() + 1);
        }
        if (request.projectMode()) { prompt.append(ProjectDelivery.INSTRUCTIONS); }
        // Keep complete user/assistant pairs, newest first when selecting, chronological when sending.
        String grounding=knowledge.context(agent,request.input(),username);
        var history = new ArrayList<ChatTurn>(); int chars = request.input().length() + prompt.length() + grounding.length();
        if (chars > 60000) { throw new ConflictException("智能体指令和消息过长，请精简 Skills 或输入（上下文上限 60000 字符）"); }
        for (var previous : turns.findTop20BySessionIdAndStatusOrderByIdDesc(id, "SUCCEEDED")) {
            int length = previous.getInput().length() + previous.getOutput().length();
            if (chars + length > 60000) { break; }
            history.add(previous); chars += length;
        }
        Collections.reverse(history);
        var messages = new ArrayList<Map<String, String>>();
        messages.add(Map.of("role", "system", "content", prompt.toString()));
        for (var previous : history) {
            messages.add(Map.of("role", "user", "content", previous.getInput()));
            messages.add(Map.of("role", "assistant", "content", previous.getOutput()));
        }
        messages.add(Map.of("role", "user", "content", KnowledgeService.groundedInput(request.input().strip(),grounding)));
        ChatTurn turn = new ChatTurn(); turn.setSession(session); turn.setRequestId(request.requestId().toString());
        turn.setProjectMode(request.projectMode());
        turn.setInput(request.input().strip()); turn.setMode(config.mode());
        turn.setModel((config.settingsId() != 1L || "demo-model".equals(agent.getModelName())) && "API".equals(config.mode()) ? config.defaultModel() : agent.getModelName());
        turn.setSkills(skillNames.toString()); turn.setContextTurns(history.size());
        turn.setKnowledge(grounding);
        turn.setTemperature(agent.getTemperature());turn.setMaxOutputTokens(agent.getMaxOutputTokens());
        turn.setDeadline(Instant.now().plusSeconds(config.timeoutSeconds() + 30L));
        if ("新对话".equals(session.getTitle())) { session.setTitle(turn.getInput().substring(0, Math.min(40, turn.getInput().length()))); }
        session.touch(); turns.saveAndFlush(turn);
        usage.start("CHAT", turn.getId(), username, turn.getModel(), turn.getMode());
        audit.record(username, "CHAT_MESSAGE_STARTED", "CHAT", id);
        return new Pending(Turn.from(turn), List.copyOf(messages), config,turn.getTemperature(),turn.getMaxOutputTokens());
    }
    @Transactional
    public Turn finish(Long sessionId, Long turnId, String output, String error, String username) {
        return finishReply(sessionId, turnId, com.agent.agentplatform.dto.ModelReply.text(output), error, username);
    }
    @Transactional
    public Turn finishReply(Long sessionId, Long turnId, com.agent.agentplatform.dto.ModelReply reply, String error, String username) {
        ChatSession session = owned(sessionId, username);
        ChatTurn turn = turns.findById(turnId).orElseThrow(() -> new ResourceNotFoundException("Message not found"));
        if (!turn.getSession().getId().equals(sessionId)) { throw new ResourceNotFoundException("Message not found"); }
        if ("RUNNING".equals(turn.getStatus())) {
            turn.finish(reply.content(), error); turn.setUsage(reply); session.touch();
            usage.finish("CHAT", turnId, error == null ? "SUCCEEDED" : "FAILED", reply);
            audit.record(username, error == null ? "CHAT_MESSAGE_SUCCEEDED" : "CHAT_MESSAGE_FAILED", "CHAT", sessionId);
        }
        return Turn.from(turn);
    }
    @Transactional
    public Turn getTurn(Long sessionId, Long turnId, String username) {
        owned(sessionId, username);
        var turn = turns.findById(turnId).orElseThrow(() -> new ResourceNotFoundException("Message not found"));
        if (!turn.getSession().getId().equals(sessionId)) { throw new ResourceNotFoundException("Message not found"); }
        return Turn.from(turn);
    }
    private ChatSession owned(Long id, String username) {
        var session = sessions.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
        // Private even from other administrators and the agent's creator.
        if (!session.getOwner().getUsername().equals(username)) { throw new ResourceNotFoundException("Conversation not found"); }
        return session;
    }
    private void requireActive(Agent agent) {
        if (agent.getStatus() != AgentStatus.ACTIVE) { throw new ConflictException("智能体已停用，不能发送新消息；历史仍可查看"); }
    }
    @Transactional
    public void recoverExpiredSessions() {
        for (Long id : turns.findExpiredSessions(Instant.now(), org.springframework.data.domain.PageRequest.of(0, 100))) {
            sessions.findForUpdate(id).ifPresent(this::recover);
        }
    }
    private void recover(ChatSession session) {
        for (var turn : turns.findBySessionIdAndStatus(session.getId(), "RUNNING")) {
            if (turn.getDeadline().isBefore(Instant.now())) {
                turn.finish(null, "生成已中断或超时，可重新发送；不会自动重试付费调用");
                usage.finish("CHAT", turn.getId(), "FAILED", com.agent.agentplatform.dto.ModelReply.text(null));
                audit.record("system", "CHAT_MESSAGE_RECOVERED", "CHAT", session.getId());
            }
        }
    }
    private void requireIdle(Long id) {
        if (!turns.findBySessionIdAndStatus(id, "RUNNING").isEmpty()) { throw new ConflictException("当前会话正在生成回复，请稍后再试"); }
    }
}
