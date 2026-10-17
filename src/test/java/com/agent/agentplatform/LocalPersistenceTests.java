package com.agent.agentplatform;

import com.agent.agentplatform.dto.*;
import com.agent.agentplatform.dto.ChatDtos.Send;
import com.agent.agentplatform.service.*;
import com.agent.agentplatform.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class LocalPersistenceTests {
    @TempDir Path directory;
    ConfigurableApplicationContext boot() {
        return SpringApplication.run(AgentPlatformApplication.class,
                "--spring.profiles.active=local", "--server.port=0",
                "--spring.datasource.url=jdbc:h2:file:" + directory.resolve("workspace").toString().replace('\\', '/') + ";MODE=PostgreSQL;DB_CLOSE_ON_EXIT=FALSE",
                "--spring.datasource.password=test-local-db", "--spring.flyway.enabled=true", "--spring.jpa.hibernate.ddl-auto=validate",
                "--app.jwt.secret=localPersistenceTestSecret2026StableJwtKeyForRestart",
                "--app.settings.encryption-key=localPersistenceTestEncryptionKey2026StableKey",
                "--app.bootstrap.username=local_admin", "--app.bootstrap.password=LocalTestPassword_2026!",
                "--app.worker.enabled=false");
    }
    @Test void previousLocalSchemaUpgradesWithoutChangingExistingData() throws Exception {
        String url = "jdbc:h2:file:" + directory.resolve("legacy").toString().replace('\\', '/') + ";MODE=PostgreSQL;DB_CLOSE_ON_EXIT=FALSE";
        org.flywaydb.core.Flyway.configure().dataSource(url,"sa","test").locations("classpath:db/local").target("1").load().migrate();
        try (var connection = java.sql.DriverManager.getConnection(url,"sa","test"); var query = connection.createStatement()) {
            query.executeUpdate("insert into users(id,username,email,password,role) values(1,'legacy','legacy@example.com','preserved-hash','ADMIN')");
            query.executeUpdate("insert into model_settings(id,version,mode,base_url,encrypted_key,default_model,timeout_seconds) values(1,4,'API','https://example.com/v1','preserved-ciphertext','model',60)");
        }
        var flyway=org.flywaydb.core.Flyway.configure().dataSource(url,"sa","test").locations("classpath:db/local").load();
        assertEquals(6,flyway.migrate().migrationsExecuted);assertEquals(0,flyway.migrate().migrationsExecuted);
        try (var connection = java.sql.DriverManager.getConnection(url,"sa","test"); var query = connection.createStatement();
             var rows = query.executeQuery("select u.password,u.enabled,u.token_version,m.encrypted_key,m.version from users u join model_settings m on m.id=1 where u.id=1")) {
            assertTrue(rows.next());assertEquals("preserved-hash",rows.getString(1));assertTrue(rows.getBoolean(2));
            assertEquals(0,rows.getLong(3));assertEquals("preserved-ciphertext",rows.getString(4));assertEquals(4,rows.getLong(5));
        }
    }
    @Test void v2UsageIsBackfilledAndIndependentOfChatDeletion() throws Exception {
        String url="jdbc:h2:file:"+directory.resolve("v2").toString().replace('\\','/')+";MODE=PostgreSQL;DB_CLOSE_ON_EXIT=FALSE";
        org.flywaydb.core.Flyway.configure().dataSource(url,"sa","test").locations("classpath:db/local").target("2").load().migrate();
        try(var connection=java.sql.DriverManager.getConnection(url,"sa","test");var query=connection.createStatement()) {
            query.executeUpdate("insert into users(id,username,email,password,role) values(1,'old','old@example.com','hash','ADMIN')");
            query.executeUpdate("insert into agents(id,agent_name,system_prompt,model_name,status,creator_id,create_time,update_time) values(1,'old','prompt','model','ACTIVE',1,now(),now())");
            query.executeUpdate("insert into chat_sessions(id,owner_id,agent_id,title,updated_at) values(1,1,1,'old',now())");
            query.executeUpdate("insert into chat_turns(id,session_id,request_id,input,output,status,model,mode,skills,context_turns,created_at,deadline,input_tokens,output_tokens) values(1,1,'old-request','input','answer','SUCCEEDED','model','API','',0,now(),now(),17,9)");
        }
        var flyway=org.flywaydb.core.Flyway.configure().dataSource(url,"sa","test").locations("classpath:db/local").load();assertEquals(5,flyway.migrate().migrationsExecuted);
        try(var connection=java.sql.DriverManager.getConnection(url,"sa","test");var query=connection.createStatement()) {
            query.executeUpdate("delete from chat_turns");query.executeUpdate("delete from chat_sessions");
            try(var rows=query.executeQuery("select source_key,input_tokens,output_tokens from usage_records")) { assertTrue(rows.next());assertEquals("CHAT:1",rows.getString(1));assertEquals(17,rows.getLong(2));assertEquals(9,rows.getLong(3)); }
        }
    }
    @Test void v3AddsNullableGenerationOptionsAndUnarchivedConversations() throws Exception {
        String url="jdbc:h2:file:"+directory.resolve("v3").toString().replace('\\','/')+";MODE=PostgreSQL;DB_CLOSE_ON_EXIT=FALSE";
        org.flywaydb.core.Flyway.configure().dataSource(url,"sa","test").locations("classpath:db/local").target("3").load().migrate();
        try(var connection=java.sql.DriverManager.getConnection(url,"sa","test");var query=connection.createStatement()) {
            query.executeUpdate("insert into users(id,username,email,password,role) values(1,'old','old@example.com','preserved-hash','ADMIN')");
            query.executeUpdate("insert into agents(id,agent_name,system_prompt,model_name,status,creator_id,create_time,update_time) values(1,'old','prompt','model','ACTIVE',1,now(),now())");
            query.executeUpdate("insert into chat_sessions(id,owner_id,agent_id,title,updated_at) values(1,1,1,'preserved conversation',now())");
        }
        var flyway=org.flywaydb.core.Flyway.configure().dataSource(url,"sa","test").locations("classpath:db/local").load();assertEquals(4,flyway.migrate().migrationsExecuted);
        try(var connection=java.sql.DriverManager.getConnection(url,"sa","test");var query=connection.createStatement()) {
            try(var rows=query.executeQuery("select s.title,s.pinned,s.archived,a.temperature,a.max_output_tokens,u.password from chat_sessions s join agents a on s.agent_id=a.id join users u on s.owner_id=u.id")) {
                assertTrue(rows.next());assertEquals("preserved conversation",rows.getString(1));assertFalse(rows.getBoolean(2));assertFalse(rows.getBoolean(3));assertNull(rows.getObject(4));assertNull(rows.getObject(5));assertEquals("preserved-hash",rows.getString(6));
            }
            query.executeUpdate("update chat_sessions set pinned=true,archived=true where id=1");
            query.executeUpdate("update agents set temperature=0.35,max_output_tokens=512 where id=1");
        }
        assertEquals(0,flyway.migrate().migrationsExecuted);
    }

    @Test void v4UpgradesRoadmapWithoutChangingExistingSkills() throws Exception {
        String url="jdbc:h2:file:"+directory.resolve("v4").toString().replace('\\','/')+";MODE=PostgreSQL;DB_CLOSE_ON_EXIT=FALSE";
        org.flywaydb.core.Flyway.configure().dataSource(url,"sa","test").locations("classpath:db/local").target("4").load().migrate();
        try(var connection=java.sql.DriverManager.getConnection(url,"sa","test");var query=connection.createStatement()) {
            query.executeUpdate("insert into users(id,username,email,password,role) values(1,'old','old@example.com','preserved-hash','ADMIN')");
            query.executeUpdate("insert into skills(id,version,name,instructions,enabled,owner_id,updated_at) values(1,3,'preserved','old prompt',true,1,now())");
        }
        var flyway=org.flywaydb.core.Flyway.configure().dataSource(url,"sa","test").locations("classpath:db/local").load();assertEquals(3,flyway.migrate().migrationsExecuted);
        try(var connection=java.sql.DriverManager.getConnection(url,"sa","test");var query=connection.createStatement();var rows=query.executeQuery("select s.kind,s.code,s.instructions,s.version,u.password from skills s join users u on u.id=s.owner_id")) {
            assertTrue(rows.next());assertEquals("PROMPT",rows.getString(1));assertNull(rows.getString(2));assertEquals("old prompt",rows.getString(3));assertEquals(3,rows.getLong(4));assertEquals("preserved-hash",rows.getString(5));
        }
        assertEquals(0,flyway.migrate().migrationsExecuted);
    }
    @Test void v5DeliveryFlagsPreserveHistoricalOutputs() throws Exception {
        String url="jdbc:h2:file:"+directory.resolve("v5").toString().replace('\\','/')+";MODE=PostgreSQL;DB_CLOSE_ON_EXIT=FALSE";
        org.flywaydb.core.Flyway.configure().dataSource(url,"sa","test").locations("classpath:db/local").target("5").load().migrate();
        try(var connection=java.sql.DriverManager.getConnection(url,"sa","test");var query=connection.createStatement()) {
            query.executeUpdate("insert into users(id,username,email,password,role) values(1,'old','old@example.com','hash','ADMIN')");
            query.executeUpdate("insert into agents(id,agent_name,system_prompt,model_name,status,creator_id,create_time,update_time) values(1,'old','prompt','model','ACTIVE',1,now(),now())");
            query.executeUpdate("insert into tasks(id,agent_id,user_id,input,output,status,create_time,update_time) values(1,1,1,'input','preserved output','SUCCEEDED',now(),now())");
        }
        var flyway=org.flywaydb.core.Flyway.configure().dataSource(url,"sa","test").locations("classpath:db/local").load();assertEquals(2,flyway.migrate().migrationsExecuted);
        try(var connection=java.sql.DriverManager.getConnection(url,"sa","test");var query=connection.createStatement();var rows=query.executeQuery("select project_mode,output from tasks")) { assertTrue(rows.next());assertFalse(rows.getBoolean(1));assertEquals("preserved output",rows.getString(2)); }
    }
    @Test void restartPreservesDeliveryModeAndExactProjectFiles() {
        Long chatId,taskId;String output;
        var auth=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("local_admin","",java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")));
        try(var app=boot()) {
            var agent=app.getBean(AgentService.class).createAgent(new AgentRequest("Delivery persistence","","prompt","demo-model",com.agent.agentplatform.entity.AgentStatus.ACTIVE,0L),"local_admin");
            chatId=app.getBean(ChatService.class).create(agent.id(),"local_admin").id();
            output=app.getBean(ChatExecutionService.class).send(chatId,new Send(UUID.randomUUID(),"build project",true),"local_admin").output();
            taskId=app.getBean(TaskService.class).createTask(new CreateTaskRequest(agent.id(),"build project",true),"local_admin").id();
            app.getBean(TaskExecutionService.class).execute(taskId,auth);
        }
        try(var app=boot()) {
            var turn=app.getBean(ChatService.class).detail(chatId,"local_admin").turns().getFirst();assertTrue(turn.projectMode());assertEquals(output,turn.output());
            var task=app.getBean(TaskService.class).getTask(taskId,auth);assertTrue(task.projectMode());assertEquals(output,task.output());
            var delivery=app.getBean(ProjectDelivery.class);assertEquals(delivery.parse(output,"SUCCEEDED"),delivery.parse(task.output(),task.status().name()));
            assertNotNull(ProjectDelivery.find(delivery.parse(turn.output(),turn.status()).files(),"main.py"));
        }
    }
    @Test void restartPreservesKnowledgeBindingsWorkflowSnapshotsAndPythonSkills() {
        Long documentId,agentId,workflowId,runId,chatId,taskId;
        var auth=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("local_admin","",java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")));
        try(var app=boot()) {
            var knowledge=app.getBean(KnowledgeService.class);
            documentId=knowledge.create(new KnowledgeDtos.Save("Handbook","Launch code ORCHID-42",true,null),"local_admin").id();
            agentId=app.getBean(AgentService.class).createAgent(new AgentRequest("RAG","","Use references","demo-model",com.agent.agentplatform.entity.AgentStatus.ACTIVE,0L),"local_admin").id();
            knowledge.bind(agentId,new KnowledgeDtos.Bind(java.util.List.of(documentId),0L),"local_admin");
            chatId=app.getBean(ChatService.class).create(agentId,"local_admin").id();
            assertTrue(app.getBean(ChatExecutionService.class).send(chatId,new Send(UUID.randomUUID(),"launch code"),"local_admin").knowledge().contains("ORCHID-42"));
            var tasks=app.getBean(TaskService.class);taskId=tasks.createTask(new CreateTaskRequest(agentId,"launch code"),"local_admin").id();
            tasks.enqueue(taskId,auth);
            app.getBean(SkillService.class).create(new SkillRequest("Python","","Explicit only",true,null,"PYTHON","print(input_text.upper())"),"local_admin");
            var workflows=app.getBean(WorkflowService.class);
            workflowId=workflows.create(new WorkflowDtos.Save("Persistent workflow",true,null,java.util.List.of(new WorkflowDtos.Step("TEMPLATE",null,"prefix {{input}}"))),auth).id();
            runId=workflows.run(workflowId,new WorkflowDtos.Request(UUID.randomUUID(),"hello"),auth).id();
            knowledge.update(documentId,new KnowledgeDtos.Save("Handbook","Launch code changed",true,0L),"local_admin");
        }
        try(var app=boot()) {
            var knowledge=app.getBean(KnowledgeService.class);
            assertEquals("Launch code changed",knowledge.get(documentId,"local_admin").content());
            assertEquals(documentId,knowledge.binding(agentId,"local_admin").documents().getFirst().id());
            assertTrue(app.getBean(ChatService.class).detail(chatId,"local_admin").turns().getFirst().knowledge().contains("ORCHID-42"));
            assertTrue(app.getBean(TaskService.class).getTask(taskId,auth).knowledge().contains("ORCHID-42"));
            var run=app.getBean(WorkflowService.class).getRun(runId,"local_admin");assertEquals(workflowId,run.workflowId());assertEquals("prefix hello",run.output());assertEquals(1,run.steps().size());
            var skill=app.getBean(SkillExecutionService.class).available("local_admin","",0,20).items().getFirst();assertEquals("print(input_text.upper())",skill.code());assertEquals("PYTHON",skill.kind());
        }
    }
    @Test void restartPreservesChatsTasksSkillsAndEncryptedCredential() {
        Long id;
        try (var app = boot()) {
            var agent = app.getBean(AgentService.class).createAgent(new AgentRequest("Generation persistence","","prompt","demo-model",com.agent.agentplatform.entity.AgentStatus.ACTIVE,0L,0.35,512),"local_admin");
            id = app.getBean(ChatService.class).create(agent.id(), "local_admin").id();
            app.getBean(ChatExecutionService.class).send(id, new Send(UUID.randomUUID(), "remember after restart"), "local_admin");
            app.getBean(ChatService.class).preferences(id,new ChatDtos.Preferences(true,true),"local_admin");
            app.getBean(TaskService.class).createTask(new CreateTaskRequest(agent.id(), "persistent task"), "local_admin");
            app.getBean(SkillService.class).create(new SkillRequest("Persistent skill", "", "instructions", true, null), "local_admin");
            app.getBean(ModelSettingsService.class).save(new ModelSettingsRequest("DEMO", "", "fake-persistent-key", "demo-model", 60, false, 0L), "local_admin");
            app.getBean(ModelSettingsService.class).savePersonal(new ModelSettingsRequest("DEMO", "", "fake-personal-key", "demo-model", 60, false, 0L, "ANTHROPIC"), "local_admin");
            var user=app.getBean(UserService.class).getCurrentUser("local_admin");
            app.getBean(UserService.class).updateProfile(user.getId(),new AccountRequests.Profile("Persistent name",user.getEmail()));
        }
        try (var app = boot()) {
            var detail = app.getBean(ChatService.class).detail(id, "local_admin");
            assertEquals("remember after restart", detail.turns().getFirst().input());
            assertTrue(detail.session().pinned());assertTrue(detail.session().archived());
            assertEquals(0.35,detail.turns().getFirst().temperature());assertEquals(512,detail.turns().getFirst().maxOutputTokens());
            var savedAgent=app.getBean(AgentService.class).getMyAgents("local_admin").stream().filter(a->a.name().equals("Generation persistence")).findFirst().orElseThrow();
            assertEquals(0.35,savedAgent.temperature());assertEquals(512,savedAgent.maxOutputTokens());
            assertEquals(1, app.getBean(TaskRepository.class).count()); assertEquals(1, app.getBean(SkillRepository.class).count());
            assertEquals(1, app.getBean(UserRepository.class).count());
            assertEquals("fake-persistent-key", app.getBean(ModelSettingsService.class).connection().apiKey());
            assertEquals("fake-personal-key", app.getBean(ModelSettingsService.class).connectionForUser("local_admin").apiKey());
            assertEquals("ANTHROPIC", app.getBean(ModelSettingsService.class).connectionForUser("local_admin").protocol());
            assertEquals("Persistent name", app.getBean(UserService.class).getCurrentUser("local_admin").getDisplayName());
            app.getBean(ChatService.class).preferences(id,new ChatDtos.Preferences(true,false),"local_admin");
            assertEquals(1, app.getBean(ChatExecutionService.class).send(id, new Send(UUID.randomUUID(), "continue"), "local_admin").contextTurns());
        }
    }
}
