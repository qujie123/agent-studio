package com.agent.agentplatform;

import com.agent.agentplatform.entity.User;
import com.agent.agentplatform.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agent_api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "app.llm.base-url=",
        "app.llm.api-key="
})
@AutoConfigureMockMvc
class PlatformApiIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void demotedAdministratorCannotKeepAdminAccessWithOldToken() throws Exception {
        register("demoted", "demoted@example.com");
        User user = userRepository.findByUsername("demoted").orElseThrow();
        user.setRole("ADMIN");
        userRepository.saveAndFlush(user);
        String token = login("demoted");
        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        user.setRole("USER");
        userRepository.saveAndFlush(user);
        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletedIdentityCannotAuthenticateEvenAfterUsernameIsReused() throws Exception {
        register("reused", "reused@example.com");
        String token = login("reused");
        userRepository.delete(userRepository.findByUsername("reused").orElseThrow());
        mockMvc.perform(get("/api/agents").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
        register("reused", "reused@example.com");
        mockMvc.perform(get("/api/users/me").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void databaseRejectsDuplicateIdentityOutsideRegistrationPrecheck() throws Exception {
        register("uniqueuser", "unique@example.com");
        User duplicate = new User();
        duplicate.setUsername("uniqueuser");
        duplicate.setEmail("different@example.com");
        duplicate.setPassword("unused-test-hash");
        duplicate.setRole("USER");
        assertThrows(DataIntegrityViolationException.class, () -> userRepository.saveAndFlush(duplicate));
        User duplicateEmail = new User();
        duplicateEmail.setUsername("differentuser");
        duplicateEmail.setEmail("unique@example.com");
        duplicateEmail.setPassword("unused-test-hash");
        duplicateEmail.setRole("USER");
        assertThrows(DataIntegrityViolationException.class, () -> userRepository.saveAndFlush(duplicateEmail));
    }

    @Test
    void normalizedShortUsernameAndOversizedUtf8PasswordReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/users/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":" x ","email":"short@example.com","password":"secret123"}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/users/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"utf8password\",\"email\":\"utf8@example.com\",\"password\":\""
                                + "密".repeat(25) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void authenticationAuthorizationAndAgentTaskFlow() throws Exception {
        mockMvc.perform(get("/api/agents"))
                .andExpect(status().isUnauthorized());

        register("alice", "alice@example.com");
        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"secret123","email":"other@example.com"}
                                """))
                .andExpect(status().isConflict());

        String aliceToken = login("alice");
        mockMvc.perform(get("/api/users/me").header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(aliceToken)))
                .andExpect(status().isForbidden());

        MvcResult agentResult = mockMvc.perform(post("/api/agents")
                        .header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Course Assistant","description":"Answers course questions",
                                "systemPrompt":"Be concise and accurate","modelName":"gpt-compatible",
                                "status":"ACTIVE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creatorUsername").value("alice"))
                .andReturn();
        long agentId = numberField(agentResult, "id");

        register("bob", "bob@example.com");
        String bobToken = login("bob");
        mockMvc.perform(put("/api/agents/{id}", agentId)
                        .header("Authorization", bearer(bobToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Changed","description":"No permission",
                                "systemPrompt":"Changed","modelName":"gpt-compatible","status":"ACTIVE"}
                                """))
                .andExpect(status().isForbidden());

        MvcResult taskResult = mockMvc.perform(post("/api/tasks")
                        .header("Authorization", bearer(bobToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":" + agentId + ",\"input\":\"Explain JWT roles\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        long taskId = numberField(taskResult, "id");

        mockMvc.perform(post("/api/tasks/{id}/execute", taskId)
                        .header("Authorization", bearer(bobToken)))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/api/tasks/{id}", taskId)
                        .header("Authorization", bearer(bobToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        mockMvc.perform(get("/api/tasks/assigned")
                        .header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(taskId));

        mockMvc.perform(patch("/api/tasks/{id}/result", taskId)
                        .header("Authorization", bearer(bobToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"output\":\"Unauthorized result\",\"success\":true}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/tasks/{id}/result", taskId)
                        .header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"output\":\"JWT roles become Spring authorities\",\"success\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        mockMvc.perform(patch("/api/tasks/{id}/result", taskId)
                        .header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"output\":\"second result\",\"success\":false}"))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/agents/{id}", agentId)
                        .header("Authorization", bearer(aliceToken)))
                .andExpect(status().isConflict());

        User alice = userRepository.findByUsername("alice").orElseThrow();
        alice.setRole("ADMIN");
        userRepository.save(alice);
        String adminToken = login("alice");
        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].password").doesNotExist());
        mockMvc.perform(get("/api/admin/tasks").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
    }

    private void register(String username, String email) throws Exception {
        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username
                                + "\",\"password\":\"secret123\",\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private long numberField(MvcResult result, String field) throws Exception {
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$." + field)).longValue();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
