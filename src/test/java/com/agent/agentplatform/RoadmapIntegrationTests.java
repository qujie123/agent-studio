package com.agent.agentplatform;

import com.agent.agentplatform.entity.User;
import com.agent.agentplatform.repository.UserRepository;
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

@SpringBootTest(properties={"spring.profiles.active=demo","spring.datasource.url=jdbc:h2:mem:roadmap;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","app.worker.enabled=false","app.recovery.enabled=false","app.bootstrap.username="})
@AutoConfigureMockMvc
class RoadmapIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired JwtUtil jwt;
    String token,otherToken;
    @BeforeEach void setup(){token=user();otherToken=user();}
    String user(){String name="road_"+UUID.randomUUID().toString().substring(0,8);User u=new User();u.setUsername(name);u.setEmail(name+"@example.com");u.setPassword("unused-test-hash");u.setRole("USER");return "Bearer "+jwt.generateToken(users.saveAndFlush(u));}
    long create(String route,Map<String,Object> body) throws Exception {return mapper.readTree(mvc.perform(post("/api"+route).header("Authorization",token).contentType("application/json").content(mapper.writeValueAsString(body))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asLong();}
    Map<String,Object> document(){return new LinkedHashMap<>(Map.of("title","Project handbook","content","The launch code is ORCHID-42. 产品代号是青松。","enabled",true));}
    long agent() throws Exception {return create("/agents",Map.of("name","RAG agent","description","","systemPrompt","Use provided references.","modelName","demo-model","status","ACTIVE"));}
    @Test void knowledgeIsPrivateVersionedAndSearchable() throws Exception {
        long id=create("/knowledge",document());
        mvc.perform(get("/api/knowledge/"+id).header("Authorization",otherToken)).andExpect(status().isNotFound());
        mvc.perform(get("/api/knowledge/page").header("Authorization",otherToken)).andExpect(jsonPath("$.total").value(0));
        mvc.perform(post("/api/knowledge/"+id+"/retrieve").header("Authorization",token).contentType("application/json").content("{\"query\":\"launch code\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].text").value(org.hamcrest.Matchers.containsString("ORCHID-42")));
        var data=document();data.put("version",0);data.put("content","Updated handbook");String json=mapper.writeValueAsString(data);
        mvc.perform(put("/api/knowledge/"+id).header("Authorization",token).contentType("application/json").content(json)).andExpect(status().isOk());
        mvc.perform(put("/api/knowledge/"+id).header("Authorization",token).contentType("application/json").content(json)).andExpect(status().isConflict());
    }
    @Test void boundKnowledgeReachesConversationButNotAnotherUser() throws Exception {
        long doc=create("/knowledge",document()),agent=agent();
        mvc.perform(put("/api/agents/"+agent+"/knowledge").header("Authorization",token).contentType("application/json").content("{\"documentIds\":["+doc+"],\"version\":0}")).andExpect(status().isOk());
        long chat=mapper.readTree(mvc.perform(post("/api/chats").header("Authorization",token).contentType("application/json").content("{\"agentId\":"+agent+"}")).andReturn().getResponse().getContentAsString()).path("id").asLong();
        mvc.perform(post("/api/chats/"+chat+"/messages").header("Authorization",token).contentType("application/json").content("{\"requestId\":\""+UUID.randomUUID()+"\",\"input\":\"launch code\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.knowledge").value(org.hamcrest.Matchers.containsString("ORCHID-42")));
        long otherChat=mapper.readTree(mvc.perform(post("/api/chats").header("Authorization",otherToken).contentType("application/json").content("{\"agentId\":"+agent+"}")).andReturn().getResponse().getContentAsString()).path("id").asLong();
        mvc.perform(post("/api/chats/"+otherChat+"/messages").header("Authorization",otherToken).contentType("application/json").content("{\"requestId\":\""+UUID.randomUUID()+"\",\"input\":\"launch code\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.knowledge").value(""));
        mvc.perform(delete("/api/knowledge/"+doc).header("Authorization",token)).andExpect(status().isConflict());
    }
    @Test void invalidKnowledgeAndCrossOwnerBindingAreRejected() throws Exception {
        mvc.perform(post("/api/knowledge").header("Authorization",token).contentType("application/json").content("{\"title\":\"empty\",\"content\":\"\"}")).andExpect(status().isBadRequest());
        long doc=create("/knowledge",document());String mine=token;token=otherToken;long agent=agent();token=mine;
        mvc.perform(put("/api/agents/"+agent+"/knowledge").header("Authorization",otherToken).contentType("application/json").content("{\"documentIds\":["+doc+"],\"version\":0}")).andExpect(status().isNotFound());
    }
    @Test void visualWorkflowRunsOnceAndRetainsSnapshot() throws Exception {
        long id=create("/workflows",Map.of("name","Template workflow","enabled",true,"steps",List.of(Map.of("type","TEMPLATE","value","prefix {{input}}"),Map.of("type","TEMPLATE","value","{{input}} suffix"))));
        String body=mapper.writeValueAsString(Map.of("requestId",UUID.randomUUID(),"input","hello"));
        String first=mvc.perform(post("/api/workflows/"+id+"/runs").header("Authorization",token).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCEEDED")).andExpect(jsonPath("$.output").value("prefix hello suffix")).andReturn().getResponse().getContentAsString();
        long run=mapper.readTree(first).path("id").asLong();
        mvc.perform(post("/api/workflows/"+id+"/runs").header("Authorization",token).contentType("application/json").content(body)).andExpect(jsonPath("$.id").value(run));
        mvc.perform(get("/api/workflows/"+id+"/runs/page").header("Authorization",token)).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/workflows/"+id).header("Authorization",otherToken)).andExpect(status().isNotFound());
        mvc.perform(get("/api/workflow-runs/"+run).header("Authorization",otherToken)).andExpect(status().isNotFound());
    }
    @Test void workflowValidationRejectsUnknownStepsAndPrivateDocuments() throws Exception {
        mvc.perform(post("/api/workflows").header("Authorization",token).contentType("application/json").content("{\"name\":\"bad\",\"enabled\":true,\"steps\":[{\"type\":\"SHELL\",\"value\":\"anything\"}]}")).andExpect(status().isBadRequest());
        long doc=create("/knowledge",document());token=otherToken;
        mvc.perform(post("/api/workflows").header("Authorization",token).contentType("application/json").content("{\"name\":\"private\",\"enabled\":true,\"steps\":[{\"type\":\"RETRIEVE\",\"resourceId\":"+doc+"}]}")).andExpect(status().isNotFound());
    }
    @Test void executableSkillsAreExplicitAndDoNotExposeHostExecution() throws Exception {
        long skill=create("/skills",Map.of("name","Python skill","instructions","Explicit execution only","enabled",true,"kind","PYTHON","code","print(input_text.upper())"));
        mvc.perform(get("/api/skills/"+skill).header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("PYTHON"));
        mvc.perform(post("/api/skills/"+skill+"/run").header("Authorization",otherToken).contentType("application/json").content("{\"requestId\":\""+UUID.randomUUID()+"\",\"input\":\"hello\"}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/skills/sandbox-status").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.available").isBoolean());
        mvc.perform(post("/api/skills").header("Authorization",token).contentType("application/json").content("{\"name\":\"bad\",\"instructions\":\"x\",\"kind\":\"SHELL\",\"code\":\"x\"}")).andExpect(status().isBadRequest());
    }
}
