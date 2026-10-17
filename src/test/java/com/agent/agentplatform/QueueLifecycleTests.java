package com.agent.agentplatform;

import com.agent.agentplatform.dto.*;
import com.agent.agentplatform.entity.*;
import com.agent.agentplatform.exception.ConflictException;
import com.agent.agentplatform.exception.ForbiddenException;
import com.agent.agentplatform.repository.UserRepository;
import com.agent.agentplatform.service.*;
import com.agent.agentplatform.utils.JwtUtil;
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
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("demo")
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:queue_tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false", "app.worker.enabled=false", "app.bootstrap.username="
})
@AutoConfigureMockMvc
class QueueLifecycleTests {
    @Autowired TaskService tasks;
    @Autowired AgentService agents;
    @Autowired UserService userService;
    @Autowired UserRepository users;
    @Autowired TaskExecutionService execution;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtUtil jwt;
    @Autowired MockMvc mvc;
    Authentication owner;
    Long agentId;
    Long taskId;
    User admin;
    String token;

    @BeforeEach void setup() {
        jdbc.update("delete from tasks");
        jdbc.update("delete from agents");
        jdbc.update("delete from audit_events");
        jdbc.update("delete from users");
        admin = new User();
        admin.setUsername("queue_admin"); admin.setEmail("queue@example.com");
        admin.setPassword("unused"); admin.setRole("ADMIN");
        users.saveAndFlush(admin);
        token = "Bearer " + jwt.generateToken(admin);
        owner = new UsernamePasswordAuthenticationToken(admin.getUsername(), null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        agentId = agents.createAgent(new AgentRequest("Queue", "", "original prompt", "original-model",
                AgentStatus.ACTIVE), admin.getUsername()).id();
        taskId = tasks.createTask(new CreateTaskRequest(agentId, "private-input-marker"), admin.getUsername()).id();
    }

    @Test void queuedExecutionUsesSnapshotAndPersistsResult() {
        assertEquals(TaskStatus.QUEUED, tasks.enqueue(taskId, owner).status());
        agents.updateAgent(agentId, new AgentRequest("Changed", "", "changed prompt", "changed-model",
                AgentStatus.ACTIVE), owner);
        var claimed = tasks.claimQueuedTask().orElseThrow();
        assertEquals("original-model", claimed.model());
        assertEquals("original prompt", claimed.systemPrompt());
        assertEquals(TaskStatus.SUCCEEDED, execution.runClaimed(claimed).status());
        assertTrue(tasks.claimQueuedTask().isEmpty());
        assertThrows(ConflictException.class, () -> tasks.enqueue(taskId, owner));
    }

    @Test void simultaneousWorkersClaimTaskOnlyOnce() throws Exception {
        tasks.enqueue(taskId, owner);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new CountDownLatch(1);
            var first = pool.submit(() -> { start.await(); return tasks.claimQueuedTask(); });
            var second = pool.submit(() -> { start.await(); return tasks.claimQueuedTask(); });
            start.countDown();
            int claimed = (first.get(5, TimeUnit.SECONDS).isPresent() ? 1 : 0)
                    + (second.get(5, TimeUnit.SECONDS).isPresent() ? 1 : 0);
            assertEquals(1, claimed);
        }
    }

    @Test void cancelledTaskCannotRunAndUnrelatedUserCannotCancel() {
        Authentication other = new UsernamePasswordAuthenticationToken("other", null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        assertThrows(ForbiddenException.class, () -> tasks.cancel(taskId, other));
        tasks.enqueue(taskId, owner);
        assertEquals(TaskStatus.CANCELLED, tasks.cancel(taskId, owner).status());
        assertTrue(tasks.claimQueuedTask().isEmpty());
        assertThrows(ConflictException.class, () -> tasks.beginExecution(taskId, owner));
    }

    @Test void stoppingAgentFailsQueuedTaskWithoutCallingModel() {
        tasks.enqueue(taskId, owner);
        agents.updateAgent(agentId, new AgentRequest("Stopped", "", "prompt", "model",
                AgentStatus.STOPPED), owner);
        assertTrue(tasks.claimQueuedTask().isEmpty());
        assertEquals(TaskStatus.FAILED, tasks.getTask(taskId, owner).status());
    }

    @Test void recoveryKeepsLiveTasksAndClosesExpiredTasks() {
        tasks.beginExecution(taskId, owner);
        tasks.recoverExpiredExecutions();
        assertEquals(TaskStatus.RUNNING, tasks.getTask(taskId, owner).status());
        jdbc.update("update tasks set update_time = ? where id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(200)), taskId);
        tasks.recoverExpiredExecutions();
        assertEquals(TaskStatus.FAILED, tasks.getTask(taskId, owner).status());
        assertThrows(ConflictException.class, () -> tasks.finishExecution(taskId, "late", true));
    }

    @Test void lastAdminProtectionAndAuditAccess() throws Exception {
        assertThrows(ConflictException.class, () -> userService.updateRole(admin.getId(), "USER"));
        assertThrows(ConflictException.class, () -> userService.deleteUser(admin.getId()));
        tasks.cancel(taskId, owner);
        String audit = mvc.perform(get("/api/admin/audit").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].action").value("TASK_CANCELLED"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(audit.contains("private-input-marker"));
        assertFalse(audit.contains("original prompt"));
        mvc.perform(get("/api/admin/audit")).andExpect(status().isUnauthorized());
    }

    @Test void publicAssetsHaveCspAndPrivateStatusRequiresLogin() throws Exception {
        mvc.perform(get("/index.html")).andExpect(status().isOk())
                .andExpect(header().exists("Content-Security-Policy"));
        mvc.perform(get("/app.js")).andExpect(status().isOk());
        mvc.perform(get("/api/system/status")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/system/status").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.demo").value(true))
                .andExpect(jsonPath("$.workerEnabled").value(false));
        mvc.perform(post("/api/tasks/{id}/enqueue", taskId).header("Authorization", token))
                .andExpect(status().isServiceUnavailable());
    }
}
