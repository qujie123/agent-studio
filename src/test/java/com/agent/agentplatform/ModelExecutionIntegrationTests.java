package com.agent.agentplatform;

import com.agent.agentplatform.dto.AgentRequest;
import com.agent.agentplatform.dto.CompleteTaskRequest;
import com.agent.agentplatform.dto.CreateTaskRequest;
import com.agent.agentplatform.entity.AgentStatus;
import com.agent.agentplatform.entity.TaskStatus;
import com.agent.agentplatform.entity.User;
import com.agent.agentplatform.exception.ConflictException;
import com.agent.agentplatform.repository.UserRepository;
import com.agent.agentplatform.service.AgentService;
import com.agent.agentplatform.service.TaskExecutionService;
import com.agent.agentplatform.service.TaskService;
import com.agent.agentplatform.utils.JwtUtil;
import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:model_api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
        "app.llm.api-key=local-test-key", "app.llm.timeout-seconds=1"
})
@AutoConfigureMockMvc
class ModelExecutionIntegrationTests {
    private static HttpServer provider;
    private static final ExecutorService serverExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private static final AtomicInteger calls = new AtomicInteger();
    private static final AtomicReference<String> receivedBody = new AtomicReference<>();
    private static final AtomicReference<String> receivedAuthorization = new AtomicReference<>();
    private static volatile String mode = "success";
    private static volatile CountDownLatch entered;
    private static volatile CountDownLatch release;

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AgentService agents;
    @Autowired TaskService tasks;
    @Autowired TaskExecutionService execution;
    @Autowired JwtUtil jwt;
    @Autowired JdbcTemplate jdbc;

    private Authentication owner;
    private String token;
    private Long agentId;
    private Long taskId;

    @DynamicPropertySource
    static void configureProvider(DynamicPropertyRegistry registry) throws Exception {
        provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.setExecutor(serverExecutor);
        provider.createContext("/v1/chat/completions", exchange -> {
            String currentMode = mode;
            CountDownLatch requestEntered = entered;
            CountDownLatch requestRelease = release;
            calls.incrementAndGet();
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            receivedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestEntered.countDown();
            try {
                if ("blocked".equals(currentMode)) {
                    requestRelease.await(5, TimeUnit.SECONDS);
                } else if ("timeout".equals(currentMode)) {
                    Thread.sleep(1800);
                }
                int status = "http-error".equals(currentMode) ? 429 : 200;
                String body = switch (currentMode) {
                    case "http-error" -> "{\"error\":\"secret-from-provider\"}";
                    case "malformed" -> "{broken-json";
                    case "empty" -> "{\"choices\":[]}";
                    case "truncated" -> "{\"choices\":[{\"message\":{\"content\":\"partial\"},\"finish_reason\":\"length\"}]}";
                    default -> "{\"choices\":[{\"message\":{\"content\":\"Model answer\"},\"finish_reason\":\"stop\"}]}";
                };
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        provider.start();
        registry.add("app.llm.base-url", () -> "http://127.0.0.1:" + provider.getAddress().getPort() + "/v1");
    }

    @AfterAll
    static void stopProvider() {
        provider.stop(0);
        serverExecutor.close();
    }

    @BeforeEach
    void prepareTask() {
        mode = "success";
        calls.set(0);
        entered = new CountDownLatch(1);
        release = new CountDownLatch(1);
        User user = new User();
        user.setUsername("model_" + UUID.randomUUID());
        user.setEmail(user.getUsername() + "@example.com");
        user.setPassword("unused");
        user.setRole("USER");
        user = users.saveAndFlush(user);
        token = jwt.generateToken(user);
        owner = new UsernamePasswordAuthenticationToken(user.getUsername(), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        agentId = agents.createAgent(new AgentRequest("Model test", "", "Original instructions",
                "provider-model", AgentStatus.ACTIVE), user.getUsername()).id();
        taskId = tasks.createTask(new CreateTaskRequest(agentId, "Hello model"), user.getUsername()).id();
    }

    @Test
    void successfulExecutionPersistsResultAndSendsCompatibleRequest() throws Exception {
        mvc.perform(post("/api/tasks/{id}/execute", taskId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.output").value("Model answer"))
                .andExpect(jsonPath("$.executionModel").value("provider-model"));
        assertEquals("Bearer local-test-key", receivedAuthorization.get());
        assertEquals("provider-model", JsonPath.read(receivedBody.get(), "$.model"));
        assertEquals("system", JsonPath.read(receivedBody.get(), "$.messages[0].role"));
        assertEquals("Original instructions", JsonPath.read(receivedBody.get(), "$.messages[0].content"));
        assertEquals("Hello model", JsonPath.read(receivedBody.get(), "$.messages[1].content"));
        mvc.perform(post("/api/tasks/{id}/execute", taskId).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
        assertEquals(1, calls.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"http-error", "malformed", "empty", "truncated", "timeout"})
    void providerFailuresAreTerminalAndSanitized(String failure) {
        mode = failure;
        var result = execution.execute(taskId, owner);
        assertEquals(TaskStatus.FAILED, result.status());
        assertNotNull(result.output());
        assertFalse(result.output().contains("secret-from-provider"));
        assertFalse(result.output().contains("local-test-key"));
        assertEquals(TaskStatus.FAILED, tasks.getTask(taskId, owner).status());
        assertEquals(1, calls.get());
    }

    @Test
    void concurrentExecutionCallsProviderOnceAndPreventsManualOverwrite() throws Exception {
        mode = "blocked";
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> execution.execute(taskId, owner));
            try {
                assertTrue(entered.await(3, TimeUnit.SECONDS));
                assertEquals(TaskStatus.RUNNING, tasks.getTask(taskId, owner).status());
                assertThrows(ConflictException.class, () -> execution.execute(taskId, owner));
                assertThrows(ConflictException.class, () ->
                        tasks.completeTask(taskId, new CompleteTaskRequest("overwrite", true), owner));
                agents.updateAgent(agentId, new AgentRequest("Edited", "", "New instructions",
                        "new-model", AgentStatus.ACTIVE), owner);
            } finally {
                release.countDown();
            }
            assertEquals(TaskStatus.SUCCEEDED, first.get(3, TimeUnit.SECONDS).status());
            assertEquals("provider-model", tasks.getTask(taskId, owner).executionModel());
            assertEquals(1, calls.get());
        }
    }

    @Test
    void unrelatedUserCannotExecuteOrViewTask() throws Exception {
        User other = new User();
        other.setUsername("outsider_" + UUID.randomUUID());
        other.setEmail(other.getUsername() + "@example.com");
        other.setPassword("unused");
        other.setRole("USER");
        String otherToken = jwt.generateToken(users.saveAndFlush(other));
        mvc.perform(post("/api/tasks/{id}/execute", taskId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/tasks/{id}", taskId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
        assertEquals(0, calls.get());
        assertEquals(TaskStatus.PENDING, tasks.getTask(taskId, owner).status());
    }

    @Test
    void stoppedAgentCannotExecutePreviouslySubmittedTask() {
        agents.updateAgent(agentId, new AgentRequest("Stopped", "", "Instructions",
                "provider-model", AgentStatus.STOPPED), owner);
        assertThrows(ConflictException.class, () -> execution.execute(taskId, owner));
        assertEquals(0, calls.get());
        assertEquals(TaskStatus.PENDING, tasks.getTask(taskId, owner).status());
    }

    @Test
    void interruptedTaskCanBeRecoveredOnlyAfterDeadline() {
        tasks.beginExecution(taskId, owner);
        assertThrows(ConflictException.class, () -> tasks.recoverInterruptedTask(taskId, owner));
        jdbc.update("update tasks set update_time = ? where id = ?",
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)), taskId);
        assertEquals(TaskStatus.FAILED, tasks.recoverInterruptedTask(taskId, owner).status());
        assertThrows(ConflictException.class, () -> tasks.finishExecution(taskId, "late result", true));
    }
}
