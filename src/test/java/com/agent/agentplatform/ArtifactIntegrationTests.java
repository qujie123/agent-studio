package com.agent.agentplatform;

import com.agent.agentplatform.entity.User;
import com.agent.agentplatform.repository.*;
import com.agent.agentplatform.service.*;
import com.agent.agentplatform.dto.*;
import com.agent.agentplatform.utils.JwtUtil;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.profiles.active=demo","spring.datasource.url=jdbc:h2:mem:artifacts;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","app.worker.enabled=false","app.recovery.enabled=false","app.bootstrap.username=","app.sandbox.enabled=false"})
@AutoConfigureMockMvc
class ArtifactIntegrationTests {
    @Autowired MockMvc mvc; @Autowired ObjectMapper mapper; @Autowired UserRepository users; @Autowired TaskRepository tasks;
    @Autowired JwtUtil jwt; @Autowired ChatService chats; @Autowired ProjectDelivery delivery;
    String token,otherToken,adminToken,username; long userId;
    String user(String role){User u=new User();u.setUsername("art_"+UUID.randomUUID().toString().substring(0,8));u.setEmail(u.getUsername()+"@example.com");u.setPassword("unused-test-hash");u.setRole(role);u=users.saveAndFlush(u);if(token==null){username=u.getUsername();userId=u.getId();}return "Bearer "+jwt.generateToken(u);}
    @BeforeEach void setup(){token=user("USER");otherToken=user("USER");adminToken=user("ADMIN");}
    tools.jackson.databind.JsonNode postJson(String path,Object body,int status) throws Exception {var response=mvc.perform(post("/api"+path).header("Authorization",token).contentType("application/json").content(mapper.writeValueAsString(body))).andExpect(status().is(status)).andReturn().getResponse();return mapper.readTree(response.getContentAsString());}
    long agent() throws Exception{return postJson("/agents",Map.of("name","Delivery "+UUID.randomUUID().toString().substring(0,8),"description","","systemPrompt","Be helpful.","modelName","demo-model","status","ACTIVE"),201).path("id").asLong();}
    long task() throws Exception{long id=postJson("/tasks",Map.of("agentId",agent(),"input","Build a project","projectMode",true),201).path("id").asLong();postJson("/tasks/"+id+"/execute",Map.of(),200);return id;}
    @Test void taskFilesZipAndPreviewUseExistingTaskPermissions() throws Exception {
        long id=task();String path="/api/artifacts/tasks/"+id;
        mvc.perform(get(path).header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.complete").value(true)).andExpect(jsonPath("$.entrypoint").value("index.html"));
        mvc.perform(get(path)).andExpect(status().isUnauthorized());mvc.perform(get(path).header("Authorization",otherToken)).andExpect(status().isForbidden());mvc.perform(get(path).header("Authorization",adminToken)).andExpect(status().isOk());
        mvc.perform(get(path+"/archive").header("Authorization",token)).andExpect(status().isOk()).andExpect(content().contentType("application/zip")).andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString("demo-heart.zip")));
        mvc.perform(get(path+"/files").param("path","main.py").header("Authorization",token)).andExpect(status().isOk()).andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString("main.py"))).andExpect(content().string(org.hamcrest.Matchers.containsString("import tkinter")));
        mvc.perform(get(path+"/files").param("path","../main.py").header("Authorization",token)).andExpect(status().isBadRequest());
        String url=postJson("/artifacts/tasks/"+id+"/preview",Map.of("entrypoint","index.html"),200).path("url").asString();
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(header().string("Content-Security-Policy",ArtifactPreview.CSP)).andExpect(header().string("X-Frame-Options","SAMEORIGIN")).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get("/artifact-preview/invalid")).andExpect(status().isNotFound());
        var user=users.findById(userId).orElseThrow();user.revokeTokens();users.saveAndFlush(user);mvc.perform(get(url)).andExpect(status().isNotFound());
    }
    @Test void chatDeliveryRemainsPrivateEvenFromAnotherAdministrator() throws Exception {
        long id=postJson("/chats",Map.of("agentId",agent()),200).path("id").asLong();UUID requestId=UUID.randomUUID();
        var request=new ChatDtos.Send(requestId,"Build a project",true);var turn=postJson("/chats/"+id+"/messages",request,200);long turnId=turn.path("id").asLong();assertTrue(turn.path("projectMode").asBoolean());
        String path="/api/artifacts/chats/"+id+"/turns/"+turnId;
        mvc.perform(get(path).header("Authorization",token)).andExpect(status().isOk());mvc.perform(get(path).header("Authorization",adminToken)).andExpect(status().isNotFound());mvc.perform(get(path).header("Authorization",otherToken)).andExpect(status().isNotFound());
        assertEquals(turnId,postJson("/chats/"+id+"/messages",request,200).path("id").asLong());postJson("/chats/"+id+"/messages",new ChatDtos.Send(requestId,"Build a project",false),409);
        String url=postJson("/artifacts/chats/"+id+"/turns/"+turnId+"/preview",Map.of("entrypoint","index.html"),200).path("url").asString();
        mvc.perform(delete("/api/chats/"+id).header("Authorization",token)).andExpect(status().isNoContent());mvc.perform(get(url)).andExpect(status().isNotFound());
    }
    @Test void projectModeReachesTaskAndChatPromptsWithoutChangingOrdinaryChat() throws Exception {
        long agent=agent();var ordinary=chats.begin(chats.create(agent,username).id(),new ChatDtos.Send(UUID.randomUUID(),"ordinary question"),username);assertFalse(ordinary.messages().getFirst().get("content").contains(ProjectDelivery.MARKER));
        var project=chats.begin(chats.create(agent,username).id(),new ChatDtos.Send(UUID.randomUUID(),"build",true),username);assertTrue(project.messages().getFirst().get("content").contains(ProjectDelivery.MARKER));
        long id=task();assertTrue(tasks.findById(id).orElseThrow().getExecutionPrompt().contains(ProjectDelivery.MARKER));
    }
    @Test void incompleteOutputsCannotBeDownloadedOrExecutedAndNoHostFallbackExists() throws Exception {
        long id=postJson("/tasks",Map.of("agentId",agent(),"input","pending"),201).path("id").asLong();String path="/api/artifacts/tasks/"+id;
        mvc.perform(get(path+"/archive").header("Authorization",token)).andExpect(status().isConflict());
        long completed=task();postJson("/artifacts/tasks/"+completed+"/python-preview",Map.of("requestId",UUID.randomUUID(),"entrypoint","main.py","input","","gui",true),503);
    }
}
