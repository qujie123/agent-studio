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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("demo")
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:accounts;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop","app.worker.enabled=false","app.bootstrap.username="})
@AutoConfigureMockMvc
class AccountModelIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired UserService accounts;
    @Autowired ModelSettingsService settings;
    @Autowired ModelSettingsRepository configurations;
    @Autowired AgentService agents;
    @Autowired TaskService tasks;
    @Autowired TaskExecutionService execution;
    @Autowired ChatService chats;
    @Autowired ChatExecutionService chatExecution;
    @Autowired UsageService usage;
    @Autowired JwtUtil jwt;
    @Autowired PasswordEncoder encoder;
    static HttpServer provider;
    static String url;
    static AtomicReference<String> key = new AtomicReference<>(), body = new AtomicReference<>();
    static AtomicInteger calls = new AtomicInteger();
    User admin, member;
    Long agentId;
    String adminToken, token;
    static final String PASSWORD = "AccountTest_2026!";
    @BeforeAll static void start() throws Exception {
        provider=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        url="http://127.0.0.1:"+provider.getAddress().getPort()+"/v1";
        provider.createContext("/v1/chat/completions", exchange->{
            key.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)); calls.incrementAndGet();
            byte[] answer=("{\"choices\":[{\"message\":{\"content\":\"Account reply\"},\"finish_reason\":\"stop\"}],"
                    +"\"usage\":{\"prompt_tokens\":13,\"completion_tokens\":7}}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,answer.length);exchange.getResponseBody().write(answer);exchange.close();
        });provider.start();
    }
    @AfterAll static void stop(){provider.stop(0);}
    @BeforeEach void setup(){
        for(String table:List.of("chat_turns","chat_sessions","agent_skills","tasks","agents","skills","model_settings","users","audit_events"))jdbc.update("delete from "+table);
        admin=user("account_admin","ADMIN");member=user("account_member","USER");calls.set(0);
        adminToken="Bearer "+jwt.generateToken(admin);token="Bearer "+jwt.generateToken(member);
        agentId=agents.createAgent(new AgentRequest("Shared agent","","Be helpful","demo-model",AgentStatus.ACTIVE),admin.getUsername()).id();
    }
    User user(String name,String role){User u=new User();u.setUsername(name);u.setEmail(name+"@example.com");u.setPassword(encoder.encode(PASSWORD));u.setRole(role);return users.saveAndFlush(u);}
    Authentication auth(User u){return new UsernamePasswordAuthenticationToken(u.getUsername(),null,List.of(new SimpleGrantedAuthority("ROLE_"+u.getRole())));}
    ModelSettingsRequest config(String secret,String model,long revision){return new ModelSettingsRequest("API",url,secret,model,5,false,revision);}
    String login(User user,String password) throws Exception {
        String response=mvc.perform(post("/api/users/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\""+user.getUsername()+"\",\"password\":\""+password+"\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer "+com.jayway.jsonpath.JsonPath.read(response,"$.token");
    }
    @Test void disablingAndReenablingDoesNotRestoreOldSessionsAndProtectsLastAdmin() throws Exception {
        login(member,PASSWORD);assertNotNull(users.findById(member.getId()).orElseThrow().getLastLoginAt());
        mvc.perform(patch("/api/admin/users/{id}/status",member.getId()).header("Authorization",adminToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}")).andExpect(status().isOk());
        mvc.perform(get("/api/users/me").header("Authorization",token)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/users/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"account_member\",\"password\":\""+PASSWORD+"\"}")).andExpect(status().isUnauthorized());
        accounts.setEnabled(member.getId(),true);
        mvc.perform(get("/api/users/me").header("Authorization",token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").header("Authorization",login(member,PASSWORD))).andExpect(status().isOk());
        mvc.perform(patch("/api/admin/users/{id}/status",admin.getId()).header("Authorization",adminToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}")).andExpect(status().isConflict());
    }
    @Test void passwordsAndRevocationInvalidateTokensAndMemberCannotManageOthers() throws Exception {
        mvc.perform(post("/api/admin/users/{id}/password",admin.getId()).header("Authorization",token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"Replacement_2026!\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/users/me/password").header("Authorization",token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"Replacement_2026!\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/users/{id}/password",member.getId()).header("Authorization",adminToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"Replacement_2026!\"}")).andExpect(status().isNoContent());
        mvc.perform(get("/api/users/me").header("Authorization",token)).andExpect(status().isUnauthorized());
        String replacement=login(member,"Replacement_2026!");
        mvc.perform(post("/api/users/me/revoke-sessions").header("Authorization",replacement)).andExpect(status().isNoContent());
        mvc.perform(get("/api/users/me").header("Authorization",replacement)).andExpect(status().isUnauthorized());
    }
    @Test void managedProfilesAndResourceCountsDoNotExposeSecretsOrPrivateContents() throws Exception {
        mvc.perform(post("/api/admin/users").header("Authorization",adminToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"managed_user\",\"displayName\":\"Managed\",\"email\":\"managed@example.com\",\"password\":\"ManagedPassword_2026!\",\"role\":\"USER\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("Managed")).andExpect(jsonPath("$.password").doesNotExist());
        mvc.perform(put("/api/users/me").header("Authorization",token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"New name\",\"email\":\"new@example.com\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("New name"));
        mvc.perform(get("/api/admin/users/{id}/resources",admin.getId()).header("Authorization",adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.agents").value(1)).andExpect(jsonPath("$.conversations").value(0));
        mvc.perform(get("/api/admin/users/{id}/resources",admin.getId()).header("Authorization",token)).andExpect(status().isForbidden());
    }
    @Test void personalKeyAndModelAreUsedForTasksAndChatsWithPrivateTokenAccounting() throws Exception {
        settings.save(config("platform-key","platform-model",0),admin.getUsername());
        settings.savePersonal(config("member-key","personal-model",0),member.getUsername());
        var task=tasks.createTask(new CreateTaskRequest(agentId,"personal task"),member.getUsername());
        assertEquals("personal-model",execution.execute(task.id(),auth(member)).executionModel());
        assertEquals("Bearer member-key",key.get());assertTrue(body.get().contains("personal-model"));
        var chat=chats.create(agentId,member.getUsername());
        chatExecution.send(chat.id(),new Send(UUID.randomUUID(),"personal chat"),member.getUsername());
        assertEquals("Bearer member-key",key.get());
        var summary=usage.mine(member.getUsername());assertEquals(2,summary.apiCalls());assertEquals(26,summary.inputTokens());assertEquals(14,summary.outputTokens());assertEquals(0,summary.missingUsage());
        assertEquals(0,usage.mine(admin.getUsername()).apiCalls());
        String metadata=mvc.perform(get("/api/me/model-settings").header("Authorization",token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.hasKey").value(true)).andReturn().getResponse().getContentAsString();
        assertFalse(metadata.contains("member-key"));assertFalse(metadata.contains("encryptedKey"));
        assertFalse(configurations.findById(member.getId()+1).orElseThrow().getEncryptedKey().contains("member-key"));
        mvc.perform(get("/api/system/status").header("Authorization",token)).andExpect(jsonPath("$.connectionSource").value("PERSONAL"));
        var adminTask=tasks.createTask(new CreateTaskRequest(agentId,"platform task"),admin.getUsername());
        execution.execute(adminTask.id(),auth(admin));assertEquals("Bearer platform-key",key.get());
    }
    @Test void nativeProtocolRoundTripsAndCannotReuseKeyWhenChangingProtocol() throws Exception {
        mvc.perform(get("/providers.js")).andExpect(status().isOk());
        var saved=settings.savePersonal(new ModelSettingsRequest("API",url,"native-key","claude-test",5,false,0L,"ANTHROPIC"),member.getUsername());
        assertEquals("ANTHROPIC",settings.connectionForUser(member.getUsername()).protocol());
        mvc.perform(get("/api/me/model-settings").header("Authorization",token)).andExpect(jsonPath("$.protocol").value("ANTHROPIC")).andExpect(jsonPath("$.hasKey").value(true));
        assertThrows(IllegalArgumentException.class,()->settings.savePersonal(new ModelSettingsRequest("API",url,"","test",5,false,saved.revision(),"OPENAI"),member.getUsername()));
        mvc.perform(put("/api/me/model-settings").header("Authorization",token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"API\",\"baseUrl\":\"https://example.com/v1\",\"apiKey\":\"fake\",\"defaultModel\":\"m\",\"timeoutSeconds\":5,\"clearKey\":false,\"revision\":"+saved.revision()+",\"protocol\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest());
        settings.inherit(member.getUsername(),saved.revision());
        var removed=settings.personalView(member.getUsername());assertFalse(removed.hasKey());assertEquals("",removed.baseUrl());assertEquals("",removed.defaultModel());
    }
    @Test void restoredConfigurationDoesNotReuseStaleQueueOrOldRevision() {
        var saved=settings.savePersonal(config("old-key","old-model",0),member.getUsername());
        var task=tasks.createTask(new CreateTaskRequest(agentId,"queued"),member.getUsername());tasks.enqueue(task.id(),auth(member));
        settings.inherit(member.getUsername(),saved.revision());
        long revision=settings.personalView(member.getUsername()).revision();
        assertTrue(revision>saved.revision());assertFalse(settings.personalView(member.getUsername()).hasKey());
        assertThrows(ConflictException.class,()->settings.savePersonal(config("stale","stale-model",saved.revision()),member.getUsername()));
        settings.savePersonal(config("new-key","new-model",revision),member.getUsername());
        assertEquals(TaskStatus.FAILED,execution.runClaimed(tasks.claimQueuedTask().orElseThrow()).status());assertEquals(0,calls.get());
    }
    @Test void disabledSubmitterCannotConsumeQueuedCallsAndInheritedBalanceIsPrivate() throws Exception {
        settings.save(config("platform-key","platform-model",0),admin.getUsername());
        mvc.perform(post("/api/me/model-settings/balance").header("Authorization",token)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/model-settings/balance").header("Authorization",token)).andExpect(status().isForbidden());
        settings.savePersonal(config("personal-key","personal-model",0),member.getUsername());
        mvc.perform(post("/api/me/model-settings/balance").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UNSUPPORTED"));
        var task=tasks.createTask(new CreateTaskRequest(agentId,"queued"),member.getUsername());tasks.enqueue(task.id(),auth(member));
        accounts.setEnabled(member.getId(),false);assertTrue(tasks.claimQueuedTask().isEmpty());assertEquals(0,calls.get());
    }
}
