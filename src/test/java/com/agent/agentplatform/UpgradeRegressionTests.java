package com.agent.agentplatform;

import com.agent.agentplatform.dto.*;
import com.agent.agentplatform.dto.ChatDtos.Send;
import com.agent.agentplatform.entity.*;
import com.agent.agentplatform.exception.ConflictException;
import com.agent.agentplatform.repository.*;
import com.agent.agentplatform.service.*;
import com.agent.agentplatform.utils.JwtUtil;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("demo")
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:upgrade_regressions;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop","app.worker.enabled=false","app.recovery.enabled=false","app.bootstrap.username="})
@AutoConfigureMockMvc
class UpgradeRegressionTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired AgentService agents;
    @Autowired SkillService skills;
    @Autowired TaskService tasks;
    @Autowired ChatService chats;
    @Autowired ChatTurnRepository turns;
    @Autowired UsageService usage;
    @Autowired ModelSettingsService settings;
    @Autowired JwtUtil jwt;
    User owner,other;
    Authentication auth;
    Long agent;
    String token,otherToken;
    static HttpServer provider;
    static String url;
    @BeforeAll static void provider() throws Exception {
        provider=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        provider.createContext("/v1/chat/completions",exchange->{exchange.getRequestBody().readAllBytes();
            byte[] body="{\"choices\":[{\"message\":{\"content\":\"OK\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":17,\"completion_tokens\":9}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});
        provider.start();url="http://127.0.0.1:"+provider.getAddress().getPort()+"/v1";
    }
    @AfterAll static void stop() { provider.stop(0); }
    @BeforeEach void setup() {
        for(String table:List.of("usage_records","chat_turns","chat_sessions","agent_skills","tasks","agents","skills","model_settings","users","audit_events"))jdbc.update("delete from "+table);
        owner=user("upgrade_owner","ADMIN");other=user("upgrade_other","USER");
        auth=new UsernamePasswordAuthenticationToken(owner.getUsername(),null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        token="Bearer "+jwt.generateToken(owner);otherToken="Bearer "+jwt.generateToken(other);
        agent=agents.createAgent(new AgentRequest("Upgrade agent","","prompt","demo-model",AgentStatus.ACTIVE),owner.getUsername()).id();
        settings.savePersonal(new ModelSettingsRequest("API",url,"isolated-dummy-key","test-model",2,false,0L),owner.getUsername());
    }
    User user(String name,String role) { User u=new User();u.setUsername(name);u.setEmail(name+"@example.com");u.setPassword("unused-hash");u.setRole(role);return users.saveAndFlush(u); }
    Long message(Long session,String text) {
        var pending=chats.begin(session,new Send(UUID.randomUUID(),text),owner.getUsername());
        chats.finishReply(session,pending.turn().id(),new ModelReply("answer",17L,9L),null,owner.getUsername());return pending.turn().id();
    }
    @Test void chatDeletionDoesNotRemoveUsageAndRequestReplayDoesNotDuplicateRecords() {
        var session=chats.create(agent,owner.getUsername());var request=new Send(UUID.randomUUID(),"first");
        var pending=chats.begin(session.id(),request,owner.getUsername());
        chats.finishReply(session.id(),pending.turn().id(),new ModelReply("answer",17L,9L),null,owner.getUsername());
        assertNull(chats.begin(session.id(),request,owner.getUsername()).connection());
        chats.delete(session.id(),owner.getUsername());
        var summary=usage.mine(owner.getUsername());assertEquals(1,summary.apiCalls());assertEquals(17,summary.inputTokens());assertEquals(9,summary.outputTokens());assertEquals(0,summary.missingUsage());
        assertEquals(0,usage.mine(other.getUsername()).apiCalls());
    }
    @Test void oldTaskExecutedTodayIsCountedByStartTime() {
        var task=tasks.createTask(new CreateTaskRequest(agent,"old pending task"),owner.getUsername());
        jdbc.update("update tasks set create_time=? where id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(31L*86400)),task.id());
        tasks.beginExecution(task.id(),auth);tasks.finishReply(task.id(),new ModelReply("answer",17L,9L),true);
        assertEquals(1,usage.mine(owner.getUsername()).apiCalls());assertEquals(17,usage.mine(owner.getUsername()).inputTokens());
    }
    @Test void connectionTestsAreRecorded() throws Exception {
        mvc.perform(post("/api/me/model-settings/test").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        var summary=usage.mine(owner.getUsername());assertEquals(1,summary.apiCalls());assertEquals("TEST",summary.recent().getFirst().type());assertEquals(17,summary.inputTokens());
    }
    @Test void partialFailureRetainsTokens() {
        var session=chats.create(agent,owner.getUsername());var pending=chats.begin(session.id(),new Send(UUID.randomUUID(),"partial"),owner.getUsername());
        chats.finishReply(session.id(),pending.turn().id(),new ModelReply("partial answer",17L,9L),"incomplete",owner.getUsername());
        assertEquals(1,usage.mine(owner.getUsername()).failed());assertEquals(17,usage.mine(owner.getUsername()).inputTokens());assertEquals(0,usage.mine(owner.getUsername()).missingUsage());
    }
    @Test void expiredChatsRecoverWithoutReadingTheConversation() {
        var session=chats.create(agent,owner.getUsername());var pending=chats.begin(session.id(),new Send(UUID.randomUUID(),"interrupted"),owner.getUsername());
        jdbc.update("update chat_turns set deadline=? where id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(1)),pending.turn().id());
        chats.recoverExpiredSessions();
        assertEquals("FAILED",turns.findById(pending.turn().id()).orElseThrow().getStatus());assertEquals(1,usage.mine(owner.getUsername()).failed());
    }
    @Test void agentAndBindingEditsRejectStaleVersions() {
        var skill=skills.create(new SkillRequest("Versioned skill","","instructions",true,null),owner.getUsername());
        skills.bind(agent,new SkillBindingRequest(List.of(skill.id()),0L),auth);
        var view=agents.getAgent(agent,auth);assertTrue(view.version()>0);
        assertThrows(ConflictException.class,()->agents.updateAgent(agent,new AgentRequest("Stale","","changed","demo-model",AgentStatus.ACTIVE,0L),auth));
        agents.updateAgent(agent,new AgentRequest("Current","","updated","demo-model",AgentStatus.ACTIVE,view.version()),auth);
        assertThrows(ConflictException.class,()->skills.bind(agent,new SkillBindingRequest(List.of(),view.version()),auth));
        assertEquals(1,skills.bindingSnapshot(agent,auth).skills().size());
    }
    @Test void pagedTasksStayBoundedAndEnforcePermissions() throws Exception {
        for(int i=0;i<45;i++)tasks.createTask(new CreateTaskRequest(agent,"task "+i),owner.getUsername());
        mvc.perform(get("/api/tasks/page").param("page","1").param("size","20").header("Authorization",token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(20)).andExpect(jsonPath("$.total").value(45)).andExpect(jsonPath("$.totalPages").value(3));
        mvc.perform(get("/api/tasks/page").param("scope","all").header("Authorization",otherToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/tasks/page").param("size","101").header("Authorization",token)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/tasks/page").param("page","-1").header("Authorization",token)).andExpect(status().isBadRequest());
    }
    @Test void memberAndAgentSearchDoesNotExposePrivateAgents() throws Exception {
        agents.createAgent(new AgentRequest("Private draft","","secret","demo-model",AgentStatus.DRAFT),owner.getUsername());
        mvc.perform(get("/api/agents/page").param("q","private").header("Authorization",otherToken)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/admin/users/page").param("q","upgrade_other").param("role","USER").param("enabled","true").header("Authorization",token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].password").doesNotExist());
        mvc.perform(get("/api/admin/users/page").header("Authorization",otherToken)).andExpect(status().isForbidden());
    }
    @Test void messagePaginationUsesStableAnchorAndRemainsPrivate() throws Exception {
        Long session=chats.create(agent,owner.getUsername()).id();for(int i=0;i<24;i++)message(session,"message "+i);
        var first=chats.turnsPage(session,owner.getUsername(),0,20,null);Long anchor=first.items().getFirst().id();message(session,"new message");
        var older=chats.turnsPage(session,owner.getUsername(),1,20,anchor);assertEquals(24,older.total());assertEquals(4,older.items().size());
        var ids=new HashSet<Long>();first.items().forEach(t->ids.add(t.id()));assertTrue(older.items().stream().noneMatch(t->ids.contains(t.id())));
        mvc.perform(get("/api/chats/{id}/turns",session).header("Authorization",otherToken)).andExpect(status().isNotFound());
        mvc.perform(get("/api/chats/{id}/session",session).header("Authorization",otherToken)).andExpect(status().isNotFound());
    }
}
