package com.agent.agentplatform;

import com.agent.agentplatform.dto.*;
import com.agent.agentplatform.entity.*;
import com.agent.agentplatform.repository.UserRepository;
import com.agent.agentplatform.service.*;
import com.agent.agentplatform.utils.JwtUtil;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("demo")
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:modernization;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop","app.worker.enabled=false","app.recovery.enabled=false","app.bootstrap.username="})
@AutoConfigureMockMvc
class ModernizationIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired JwtUtil jwt;
    @Autowired ModelSettingsService settings;
    @Autowired TaskService tasks;
    @Autowired TaskExecutionService execution;
    User owner,other;
    String token,otherToken;
    static HttpServer provider;
    static String url;
    static AtomicReference<String> payload=new AtomicReference<>(),key=new AtomicReference<>(),catalogMode=new AtomicReference<>("OK");
    static AtomicInteger chatCalls=new AtomicInteger(),redirectCalls=new AtomicInteger();
    @BeforeAll static void startProvider() throws Exception {
        provider=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        provider.createContext("/v1/",exchange->{
            String body;int status=200;
            if(exchange.getRequestURI().getPath().endsWith("/models")) {
                key.set(exchange.getRequestHeaders().getFirst("Authorization"));
                body="{\"data\":[{\"id\":\""+(key.get().contains("other-key")?"private-other-model":"private-owner-model")+"\"},{\"id\":\"\"},{\"id\":\"private-owner-model\"}]}";
                if(catalogMode.get().equals("REDIRECT")){status=302;exchange.getResponseHeaders().add("Location","/v1/trap");}
                if(catalogMode.get().equals("INVALID"))body="{\"api_key\":\"provider-secret-never-return\"}";
                if(catalogMode.get().equals("LARGE"))body="x".repeat(1024*1024+1);
            }else if(exchange.getRequestURI().getPath().endsWith("/trap")){redirectCalls.incrementAndGet();body="{}";}
            else {chatCalls.incrementAndGet();payload.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));body="{\"choices\":[{\"message\":{\"content\":\"answer\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":17,\"completion_tokens\":9}}";}
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });provider.start();url="http://127.0.0.1:"+provider.getAddress().getPort()+"/v1";
    }
    @AfterAll static void stopProvider(){provider.stop(0);}
    @BeforeEach void setup(){
        String suffix=UUID.randomUUID().toString().substring(0,8);owner=user("modern_"+suffix,"ADMIN");other=user("other_"+suffix,"USER");
        token="Bearer "+jwt.generateToken(owner);otherToken="Bearer "+jwt.generateToken(other);catalogMode.set("OK");chatCalls.set(0);redirectCalls.set(0);
        settings.savePersonal(new ModelSettingsRequest("API",url,"owner-key","private-owner-model",2,false,0L),owner.getUsername());
        settings.savePersonal(new ModelSettingsRequest("API",url,"other-key","private-other-model",2,false,0L),other.getUsername());
    }
    User user(String name,String role){User u=new User();u.setUsername(name);u.setEmail(name+"@example.com");u.setPassword("unused-test-hash");u.setRole(role);return users.saveAndFlush(u);}
    Map<String,Object> agentBody(){return new LinkedHashMap<>(Map.of("name","Modern agent","description","","systemPrompt","prompt","modelName","demo-model","status","ACTIVE"));}
    long agent(Map<String,Object> data) throws Exception{return mapper.readTree(mvc.perform(post("/api/agents").header("Authorization",token).contentType("application/json").content(mapper.writeValueAsString(data))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asLong();}
    long session(long agent) throws Exception{return mapper.readTree(mvc.perform(post("/api/chats").header("Authorization",token).contentType("application/json").content("{\"agentId\":"+agent+"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asLong();}
    @Test void optionalGenerationParametersReachChatAndAreRecorded() throws Exception {
        var body=agentBody();body.put("temperature",0.35);body.put("maxOutputTokens",512);long id=agent(body),chat=session(id);
        mvc.perform(post("/api/chats/"+chat+"/messages").header("Authorization",token).contentType("application/json").content("{\"requestId\":\""+UUID.randomUUID()+"\",\"input\":\"hello\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.temperature").value(0.35)).andExpect(jsonPath("$.maxOutputTokens").value(512));
        var sent=mapper.readTree(payload.get());assertEquals(0.35,sent.path("temperature").asDouble());assertEquals(512,sent.path("max_tokens").asInt());
    }
    @Test void absentGenerationParametersDoNotChangeProviderDefaults() throws Exception {
        long chat=session(agent(agentBody()));mvc.perform(post("/api/chats/"+chat+"/messages").header("Authorization",token).contentType("application/json").content("{\"requestId\":\""+UUID.randomUUID()+"\",\"input\":\"hello\"}")).andExpect(status().isOk());
        var sent=mapper.readTree(payload.get());assertFalse(sent.has("temperature"));assertFalse(sent.has("max_tokens"));
    }
    @Test void queuedTasksRetainTheirGenerationSnapshot() throws Exception {
        var body=agentBody();body.put("temperature",0.35);body.put("maxOutputTokens",512);long id=agent(body);
        long task=mapper.readTree(mvc.perform(post("/api/tasks").header("Authorization",token).contentType("application/json").content("{\"agentId\":"+id+",\"input\":\"queued\"}")).andReturn().getResponse().getContentAsString()).path("id").asLong();
        var auth=new UsernamePasswordAuthenticationToken(owner.getUsername(),null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));tasks.enqueue(task,auth);
        body.put("temperature",0.9);body.put("maxOutputTokens",256);body.put("version",0);
        mvc.perform(put("/api/agents/"+id).header("Authorization",token).contentType("application/json").content(mapper.writeValueAsString(body))).andExpect(status().isOk());
        execution.runClaimed(tasks.claimQueuedTask().orElseThrow());var sent=mapper.readTree(payload.get());assertEquals(0.35,sent.path("temperature").asDouble());assertEquals(512,sent.path("max_tokens").asInt());
    }
    @Test void invalidGenerationParametersAreRejected() throws Exception {
        for(var invalid:List.of(Map.of("temperature",-0.1),Map.of("temperature",2.1),Map.of("maxOutputTokens",0),Map.of("maxOutputTokens",65537))){var data=agentBody();data.putAll(invalid);mvc.perform(post("/api/agents").header("Authorization",token).contentType("application/json").content(mapper.writeValueAsString(data))).andExpect(status().isBadRequest());}
    }
    @Test void pinArchiveRestoreAndPrivacy() throws Exception {
        long chat=session(agent(agentBody()));String route="/api/chats/"+chat+"/preferences";
        mvc.perform(patch(route).header("Authorization",otherToken).contentType("application/json").content("{\"pinned\":true,\"archived\":false}")).andExpect(status().isNotFound());
        mvc.perform(patch(route).header("Authorization",token).contentType("application/json").content("{\"pinned\":true,\"archived\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.pinned").value(true));
        mvc.perform(get("/api/chats/page").header("Authorization",token)).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/chats/page?view=archived").header("Authorization",token)).andExpect(jsonPath("$.total").value(1));
        mvc.perform(post("/api/chats/"+chat+"/messages").header("Authorization",token).contentType("application/json").content("{\"requestId\":\""+UUID.randomUUID()+"\",\"input\":\"hello\"}")).andExpect(status().isConflict());assertEquals(0,chatCalls.get());
        mvc.perform(patch(route).header("Authorization",token).contentType("application/json").content("{\"pinned\":true,\"archived\":false}")).andExpect(status().isOk());
        mvc.perform(get("/api/chats/page?view=pinned").header("Authorization",token)).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/chats/page?view=invalid").header("Authorization",token)).andExpect(status().isBadRequest());
    }
    @Test void taskSearchAndStatusAreServerSideAndPrivate() throws Exception {
        long id=agent(agentBody());for(int i=0;i<24;i++)mvc.perform(post("/api/tasks").header("Authorization",token).contentType("application/json").content("{\"agentId\":"+id+",\"input\":\"needle "+i+"\"}")).andExpect(status().isCreated());
        mvc.perform(get("/api/tasks/page").param("q","needle 0").param("status","PENDING").header("Authorization",token)).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/tasks/page?q=needle&status=FAILED").header("Authorization",token)).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/tasks/page?q=needle").header("Authorization",otherToken)).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/tasks/page?status=INVALID").header("Authorization",token)).andExpect(status().isBadRequest());
    }
    @Test void modelCatalogUsesOnlySavedPersonalCredentialsAndNoGeneration() throws Exception {
        mvc.perform(post("/api/me/model-settings/models").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.models[0]").value("private-owner-model"));assertEquals("Bearer owner-key",key.get());
        mvc.perform(post("/api/me/model-settings/models").header("Authorization",otherToken)).andExpect(status().isOk());assertEquals("Bearer other-key",key.get());assertEquals(0,chatCalls.get());
        mvc.perform(post("/api/admin/model-settings/models").header("Authorization",otherToken)).andExpect(status().isForbidden());
        settings.inherit(other.getUsername(),settings.personalView(other.getUsername()).revision());mvc.perform(post("/api/me/model-settings/models").header("Authorization",otherToken)).andExpect(status().isBadRequest());
    }
    @Test void catalogRejectsRedirectInvalidAndOversizedBodiesWithoutLeakingProviderData() throws Exception {
        for(var mode:List.of("REDIRECT","INVALID","LARGE")){catalogMode.set(mode);String result=mvc.perform(post("/api/me/model-settings/models").header("Authorization",token)).andExpect(status().isBadGateway()).andReturn().getResponse().getContentAsString();assertFalse(result.contains("provider-secret-never-return"));}
        assertEquals(0,redirectCalls.get());assertEquals(0,chatCalls.get());
    }
}
