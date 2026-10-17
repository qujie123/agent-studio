package com.agent.agentplatform;

import com.agent.agentplatform.dto.*;
import com.agent.agentplatform.entity.*;
import com.agent.agentplatform.exception.*;
import com.agent.agentplatform.repository.*;
import com.agent.agentplatform.service.*;
import com.agent.agentplatform.utils.JwtUtil;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("demo")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workspace_features;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.worker.enabled=false", "app.bootstrap.username="
})
@AutoConfigureMockMvc
class WorkspaceFeaturesIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired ModelSettingsRepository settingsRepository;
    @Autowired ModelSettingsService settings;
    @Autowired SkillService skills;
    @Autowired AgentService agents;
    @Autowired TaskService tasks;
    @Autowired TaskExecutionService execution;
    @Autowired JwtUtil jwt;
    @Autowired SecretCipher cipher;
    private static HttpServer provider;
    private static String baseUrl;
    private static final AtomicReference<String> body = new AtomicReference<>();
    private static final AtomicReference<String> authorization = new AtomicReference<>();
    private static final AtomicInteger calls = new AtomicInteger();
    private User admin, member;
    private Authentication owner, outsider;
    private String token, memberToken;
    private Long agentId;

    @BeforeAll static void start() throws Exception {
        provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + provider.getAddress().getPort() + "/v1";
        provider.createContext("/v1/chat/completions", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            calls.incrementAndGet();
            byte[] answer = "{\"choices\":[{\"message\":{\"content\":\"Real adapter answer\"},\"finish_reason\":\"stop\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, answer.length); exchange.getResponseBody().write(answer); exchange.close();
        });
        provider.start();
    }
    @AfterAll static void stop() { provider.stop(0); }
    @BeforeEach void setup() {
        jdbc.update("delete from agent_skills"); jdbc.update("delete from tasks"); jdbc.update("delete from agents");
        jdbc.update("delete from skills"); jdbc.update("delete from users");
        jdbc.update("delete from model_settings"); jdbc.update("delete from audit_events"); calls.set(0);
        admin = user("feature_admin", "ADMIN"); member = user("feature_member", "USER");
        owner = auth(admin); outsider = auth(member); token = "Bearer " + jwt.generateToken(admin);
        memberToken = "Bearer " + jwt.generateToken(member);
        agentId = agents.createAgent(new AgentRequest("Feature agent", "", "base instructions", "demo-model", AgentStatus.ACTIVE), admin.getUsername()).id();
    }
    User user(String name, String role) {
        User user = new User(); user.setUsername(name); user.setEmail(name + "@example.com");
        user.setPassword("unused"); user.setRole(role); return users.saveAndFlush(user);
    }
    Authentication auth(User user) { return new UsernamePasswordAuthenticationToken(user.getUsername(), null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()))); }
    ModelSettingsResponse configure() {
        return settings.save(new ModelSettingsRequest("API", baseUrl, "private-test-api-key", "test-model", 5, false, 0L), admin.getUsername());
    }
    SkillResponse skill(String name, String instructions, String username) {
        return skills.create(new SkillRequest(name, "test description", instructions, true, null), username);
    }
    Long task() { return tasks.createTask(new CreateTaskRequest(agentId, "hello"), admin.getUsername()).id(); }

    @Test void credentialsAreEncryptedAndNeverReturnedAndTestUsesSavedProvider() throws Exception {
        configure();
        String saved = settingsRepository.findById(1L).orElseThrow().getEncryptedKey();
        assertFalse(saved.contains("private-test-api-key")); assertEquals("private-test-api-key", cipher.decrypt(saved));
        assertThrows(ModelUnavailableException.class, () -> cipher.decrypt("invalid-ciphertext"));
        String response = mvc.perform(get("/api/admin/model-settings").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hasKey").value(true))
                .andExpect(jsonPath("$.apiKey").doesNotExist()).andReturn().getResponse().getContentAsString();
        assertFalse(response.contains(saved)); assertFalse(response.contains("private-test-api-key"));
        mvc.perform(post("/api/admin/model-settings/test").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        assertEquals("Bearer private-test-api-key", authorization.get()); assertEquals(1, calls.get());
        mvc.perform(get("/api/admin/model-settings").header("Authorization", memberToken)).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/model-settings").header("Authorization", memberToken)
                .contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isForbidden());
    }
    @Test void blankKeyRetainsCredentialButChangedEndpointRequiresNewKey() {
        var saved = configure();
        settings.save(new ModelSettingsRequest("API", baseUrl, "", "test-model", 6, false, saved.revision()), admin.getUsername());
        assertEquals("private-test-api-key", settings.connection().apiKey());
        assertThrows(IllegalArgumentException.class, () -> settings.save(new ModelSettingsRequest("API", "https://different.example/v1", "", "test-model", 6, false, settings.view().revision()), admin.getUsername()));
        assertThrows(ConflictException.class, () -> settings.save(new ModelSettingsRequest("API", baseUrl, "", "test-model", 6, false, 0L), admin.getUsername()));
    }
    @Test void skillSnapshotKeepsOrderAndOldContentsAfterQueued() {
        configure(); var first = skill("First", "original-skill", admin.getUsername());
        var second = skill("Second", "second-skill", admin.getUsername());
        skills.bind(agentId, new SkillBindingRequest(List.of(second.id(), first.id())), owner);
        Long id = task(); tasks.enqueue(id, owner);
        skills.update(first.id(), new SkillRequest("First", "", "changed-skill", true, first.version()), owner);
        assertThrows(ConflictException.class, () -> skills.update(first.id(),
                new SkillRequest("First", "", "stale overwrite", true, first.version()), owner));
        var result = execution.runClaimed(tasks.claimQueuedTask().orElseThrow());
        assertEquals(TaskStatus.SUCCEEDED, result.status()); assertEquals("API", result.executionMode());
        assertEquals("test-model", result.executionModel()); assertTrue(result.executionSkills().contains("First · v1"));
        String prompt = JsonPath.read(body.get(), "$.messages[0].content");
        assertTrue(prompt.contains("original-skill")); assertFalse(prompt.contains("changed-skill"));
        assertTrue(prompt.indexOf("second-skill") < prompt.indexOf("original-skill"));
    }
    @Test void disabledSkillIsOmittedAndBoundSkillCannotBeDeleted() {
        var skill = skill("Optional", "omit-this", admin.getUsername());
        skills.bind(agentId, new SkillBindingRequest(List.of(skill.id())), owner);
        skills.update(skill.id(), new SkillRequest("Optional", "", "omit-this", false, skill.version()), owner);
        var snapshot = tasks.beginExecution(task(), owner);
        assertFalse(snapshot.systemPrompt().contains("omit-this"));
        assertThrows(ConflictException.class, () -> skills.delete(skill.id(), owner));
        skills.bind(agentId, new SkillBindingRequest(List.of(), agents.getAgent(agentId, owner).version()), owner); skills.delete(skill.id(), owner);
        assertTrue(skills.mine(admin.getUsername()).isEmpty());
    }
    @Test void skillsCannotBeReadEditedOrBoundAcrossUsers() throws Exception {
        var privateSkill = skill("Private", "private instructions", admin.getUsername());
        var foreign = skill("Foreign", "foreign instructions", member.getUsername());
        assertEquals(1, skills.mine(member.getUsername()).size());
        assertThrows(ForbiddenException.class, () -> skills.update(privateSkill.id(), new SkillRequest("Hack", "", "Hack", true, privateSkill.version()), outsider));
        assertThrows(ForbiddenException.class, () -> skills.bind(agentId, new SkillBindingRequest(List.of(foreign.id())), owner));
        mvc.perform(get("/api/agents/{id}/skills", agentId).header("Authorization", memberToken)).andExpect(status().isForbidden());
        mvc.perform(put("/api/agents/{id}/skills", agentId).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"skillIds\":[" + privateSkill.id() + "," + privateSkill.id() + "]}" )).andExpect(status().isBadRequest());
    }
    @Test void settingsChangePreventsQueuedTaskFromCallingDifferentProvider() {
        var saved = configure(); Long id = task(); tasks.enqueue(id, owner);
        settings.save(new ModelSettingsRequest("API", baseUrl, "replacement-key", "other-model", 5, false, saved.revision()), admin.getUsername());
        assertEquals(TaskStatus.FAILED, execution.runClaimed(tasks.claimQueuedTask().orElseThrow()).status());
        assertEquals(0, calls.get());
    }
    @Test void apiCanSwitchBackToDemoAndClearKeyWithoutAProviderCall() {
        var saved = configure();
        settings.save(new ModelSettingsRequest("DEMO", "", "", "demo-model", 5, true, saved.revision()), admin.getUsername());
        assertFalse(settings.view().hasKey());
        assertEquals("DEMO", execution.execute(task(), owner).executionMode()); assertEquals(0, calls.get());
    }
}
