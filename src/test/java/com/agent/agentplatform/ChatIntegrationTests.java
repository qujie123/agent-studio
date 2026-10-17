package com.agent.agentplatform;

import com.agent.agentplatform.dto.*;
import com.agent.agentplatform.dto.ChatDtos.*;
import com.agent.agentplatform.entity.*;
import com.agent.agentplatform.exception.*;
import com.agent.agentplatform.repository.*;
import com.agent.agentplatform.service.*;
import com.agent.agentplatform.utils.JwtUtil;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("demo")
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:chat_tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.worker.enabled=false", "app.bootstrap.username="})
@AutoConfigureMockMvc
class ChatIntegrationTests {
    @Autowired ChatService chats;
    @Autowired ChatExecutionService execution;
    @Autowired AgentService agents;
    @Autowired SkillService skills;
    @Autowired ModelSettingsService settings;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtUtil jwt;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    static HttpServer provider;
    static String url;
    static AtomicReference<String> body = new AtomicReference<>();
    static AtomicInteger calls = new AtomicInteger(), code = new AtomicInteger(200);
    Long agentId, sessionId;
    User owner, stranger;
    String token, strangerToken;
    UsernamePasswordAuthenticationToken auth;
    @BeforeAll static void startProvider() throws Exception {
        provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        url = "http://127.0.0.1:" + provider.getAddress().getPort() + "/v1";
        provider.createContext("/v1/chat/completions", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)); calls.incrementAndGet();
            byte[] bytes = "{\"choices\":[{\"message\":{\"content\":\"Remembered answer\"},\"finish_reason\":\"stop\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code.get(), bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        }); provider.start();
    }
    @AfterAll static void stopProvider() { provider.stop(0); }
    @BeforeEach void setup() {
        for (String table : List.of("chat_turns", "chat_sessions", "agent_skills", "tasks", "skills", "agents", "users", "model_settings", "audit_events")) { jdbc.update("delete from " + table); }
        owner = user("chat_owner", "USER"); stranger = user("chat_admin", "ADMIN");
        token = "Bearer " + jwt.generateToken(owner); strangerToken = "Bearer " + jwt.generateToken(stranger);
        auth = new UsernamePasswordAuthenticationToken(owner.getUsername(), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        agentId = agents.createAgent(new AgentRequest("Chat agent", "", "base prompt", "demo-model", AgentStatus.ACTIVE), owner.getUsername()).id();
        sessionId = chats.create(agentId, owner.getUsername()).id(); calls.set(0); code.set(200);
    }
    User user(String name, String role) { User u = new User(); u.setUsername(name); u.setEmail(name + "@example.com"); u.setPassword("unused"); u.setRole(role); return users.saveAndFlush(u); }
    Send request(String input) { return new Send(UUID.randomUUID(), input); }
    Turn send(String input) { return execution.send(sessionId, request(input), owner.getUsername()); }
    void configure() { settings.save(new ModelSettingsRequest("API", url, "fake-chat-key", "test-model", 5, false, 0L), stranger.getUsername()); }

    @Test void secondTurnContainsOrderedHistoryAndEnabledSkills() {
        configure(); var skill = skills.create(new SkillRequest("Review", "", "skill-marker", true, null), owner.getUsername());
        skills.bind(agentId, new SkillBindingRequest(List.of(skill.id())), auth);
        assertEquals("SUCCEEDED", send("My name is Alice").status());
        var second = send("What is my name?"); assertEquals(1, second.contextTurns());
        var messages = mapper.readTree(body.get()).path("messages");
        assertEquals(4, messages.size()); assertTrue(messages.path(0).path("content").asString().contains("skill-marker"));
        assertEquals("user", messages.path(1).path("role").asString()); assertEquals("My name is Alice", messages.path(1).path("content").asString());
        assertEquals("assistant", messages.path(2).path("role").asString()); assertEquals("Remembered answer", messages.path(2).path("content").asString());
        assertEquals("What is my name?", messages.path(3).path("content").asString()); assertEquals("Review · v1", second.skills());
        assertEquals(2, chats.detail(sessionId, owner.getUsername()).turns().size());
    }
    @Test void duplicateRequestDoesNotCallProviderTwiceAndCannotChangeInput() {
        configure(); var request = request("once"); var first = execution.send(sessionId, request, owner.getUsername());
        var duplicate = execution.send(sessionId, request, owner.getUsername());
        assertEquals(first.id(), duplicate.id()); assertEquals(1, calls.get());
        assertThrows(ConflictException.class, () -> execution.send(sessionId, new Send(request.requestId(), "changed"), owner.getUsername()));
    }
    @Test void privateEvenFromOtherAdminAndInvalidPayloadRejected() throws Exception {
        send("private content");
        mvc.perform(get("/api/chats").header("Authorization", strangerToken)).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/chats/" + sessionId).header("Authorization", strangerToken)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/chats/" + sessionId).header("Authorization", strangerToken)).andExpect(status().isNotFound());
        mvc.perform(patch("/api/chats/" + sessionId).header("Authorization", strangerToken).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"stolen\"}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/chats/" + sessionId + "/messages").header("Authorization", strangerToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request("hi")))).andExpect(status().isNotFound());
        mvc.perform(post("/api/chats/" + sessionId + "/messages").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request(" ")))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/chats")).andExpect(status().isUnauthorized());
        mvc.perform(get("/chat.js")).andExpect(status().isOk()); mvc.perform(get("/chat.css")).andExpect(status().isOk());
    }
    @Test void concurrentMessagesSerializeWithoutHoldingNetworkTransaction() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Boolean> begin = () -> { start.await(); try { chats.begin(sessionId, request("parallel"), owner.getUsername()); return true; } catch (ConflictException expected) { return false; } };
            var a = pool.submit(begin); var b = pool.submit(begin); start.countDown();
            assertNotEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
        }
        assertEquals(1, chats.detail(sessionId, owner.getUsername()).turns().size());
        assertThrows(ConflictException.class, () -> chats.delete(sessionId, owner.getUsername()));
    }
    @Test void expiredGenerationRecoversAndLateAnswerCannotOverwriteFailure() {
        var pending = chats.begin(sessionId, request("interrupted"), owner.getUsername());
        jdbc.update("update chat_turns set deadline=? where id=?", Timestamp.from(Instant.now().minusSeconds(1)), pending.turn().id());
        assertEquals("FAILED", chats.detail(sessionId, owner.getUsername()).turns().getFirst().status());
        assertEquals("FAILED", chats.finish(sessionId, pending.turn().id(), "late", null, owner.getUsername()).status());
        assertEquals(0, send("next").contextTurns());
    }
    @Test void failureIsSavedAndNotIncludedInFutureContext() {
        configure(); code.set(429); assertEquals("FAILED", send("failed-input").status());
        code.set(200); assertEquals(0, send("retry").contextTurns());
        assertFalse(body.get().contains("failed-input")); assertEquals(2, chats.detail(sessionId, owner.getUsername()).turns().size());
    }
    @Test void historyIsBoundedAndOversizedPromptsFailBeforeCallingProvider() {
        configure(); for (int i = 0; i < 23; i++) { send("message-" + i); }
        assertEquals(20, chats.detail(sessionId, owner.getUsername()).turns().getLast().contextTurns());
        assertEquals(42, mapper.readTree(body.get()).path("messages").size());
        var ids = new ArrayList<Long>();
        for (int i = 0; i < 6; i++) { ids.add(skills.create(new SkillRequest("Long " + i, "", "x".repeat(10000), true, null), owner.getUsername()).id()); }
        skills.bind(agentId, new SkillBindingRequest(ids), auth);
        int before = calls.get(); assertThrows(ConflictException.class, () -> send("too much")); assertEquals(before, calls.get());
    }
    @Test void stoppedAgentPreservesHistoryAndDeletionRemovesOnlyOwnChat() {
        send("history"); assertThrows(ConflictException.class, () -> agents.deleteAgent(agentId, auth));
        agents.updateAgent(agentId, new AgentRequest("Chat agent", "", "prompt", "demo-model", AgentStatus.STOPPED), auth);
        assertThrows(ConflictException.class, () -> send("blocked"));
        assertEquals(1, chats.detail(sessionId, owner.getUsername()).turns().size());
        assertEquals("Renamed", chats.rename(sessionId, "Renamed", owner.getUsername()).title());
        chats.delete(sessionId, owner.getUsername()); assertTrue(chats.list(owner.getUsername()).isEmpty());
        assertEquals(0L, jdbc.queryForObject("select count(*) from chat_turns", Long.class));
    }
}
