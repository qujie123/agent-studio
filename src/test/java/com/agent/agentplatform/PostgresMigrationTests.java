package com.agent.agentplatform;

import org.flywaydb.core.Flyway;
import org.springframework.boot.SpringApplication;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

// Opt-in only. Uses a unique schema in the explicitly supplied test database.
@EnabledIfEnvironmentVariable(named = "PG_TEST_URL", matches = ".+")
class PostgresMigrationTests {
    String schema;
    Connection connection;
    String url = System.getenv("PG_TEST_URL");
    String username = System.getenv().getOrDefault("PG_TEST_USERNAME", "postgres");
    String password = System.getenv().getOrDefault("PG_TEST_PASSWORD", "");

    @BeforeEach void setup() throws Exception {
        schema = "agent_test_" + UUID.randomUUID().toString().replace("-", "");
        connection = DriverManager.getConnection(url, username, password);
        sql("CREATE SCHEMA " + schema);
        sql("SET search_path TO " + schema);
    }
    @AfterEach void cleanup() throws Exception {
        if (connection != null) {
            try { sql("DROP SCHEMA " + schema + " CASCADE"); } finally { connection.close(); }
        }
    }
    Flyway flyway(boolean baseline) {
        return Flyway.configure().dataSource(url, username, password).schemas(schema)
                .defaultSchema(schema).baselineOnMigrate(baseline).baselineVersion("0")
                .cleanDisabled(true).locations("classpath:db/migration").load();
    }
    void sql(String sql) throws Exception {
        try (Statement statement = connection.createStatement()) { statement.execute(sql); }
    }
    void insertData() throws Exception {
        sql("INSERT INTO users (username,email,password,role) VALUES ('preserved','old@example.com','existing-hash','USER')");
        sql("INSERT INTO agents (agent_name,system_prompt,model_name,status,creator_id,create_time,update_time) "
                + "VALUES ('old-agent','prompt','model','ACTIVE',1,now(),now())");
        sql("INSERT INTO tasks (agent_id,user_id,input,output,status,create_time,update_time) "
                + "VALUES (1,1,'old-input','old-output','SUCCEEDED',now(),now())");
    }
    @Test void freshDatabaseMigratesAndSecondRunIsNoOp() throws Exception {
        assertEquals(9, flyway(false).migrate().migrationsExecuted);
        insertData();
        sql("UPDATE tasks SET status='QUEUED' WHERE id=1");
        sql("UPDATE tasks SET status='CANCELLED' WHERE id=1");
        assertThrows(SQLException.class, () -> sql("UPDATE tasks SET status='INVALID' WHERE id=1"));
        assertEquals(0, flyway(false).migrate().migrationsExecuted);
    }
    @Test void bootApplicationValidatesMigratedPostgresSchema() {
        String separator = url.contains("?") ? "&" : "?";
        String schemaUrl = url + separator + "currentSchema=" + schema;
        try (var application = SpringApplication.run(AgentPlatformApplication.class,
                "--server.address=127.0.0.1",
                "--server.port=0",
                "--spring.datasource.url=" + schemaUrl,
                "--spring.datasource.username=" + username,
                "--spring.datasource.password=" + password,
                "--spring.datasource.driver-class-name=org.postgresql.Driver",
                "--spring.flyway.enabled=true",
                "--spring.flyway.default-schema=" + schema,
                "--spring.flyway.schemas=" + schema,
                "--spring.jpa.hibernate.ddl-auto=validate",
                "--app.worker.enabled=false",
                "--app.bootstrap.username=",
                "--app.jwt.secret=testAgentPlatformJwtSecretKey2026TestAgentPlatformJwtSecretKey")) {
            assertNotNull(application.getBean("entityManagerFactory"));
        }
    }
    @Test void v3UpgradesAccountsAndRetainsEncryptedConfiguration() throws Exception {
        Flyway.configure().dataSource(url, username, password).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target("3").load().migrate();
        insertData();
        sql("INSERT INTO model_settings(id,version,mode,base_url,encrypted_key,default_model,timeout_seconds) VALUES(1,3,'API','https://example.com/v1','preserved-ciphertext','model',60)");
        assertEquals(6,flyway(false).migrate().migrationsExecuted);
        try(var query=connection.createStatement();var rows=query.executeQuery("SELECT u.enabled,u.token_version,m.encrypted_key,m.version FROM users u JOIN model_settings m ON m.id=1 WHERE u.id=1")){
            assertTrue(rows.next());assertTrue(rows.getBoolean(1));assertEquals(0,rows.getLong(2));
            assertEquals("preserved-ciphertext",rows.getString(3));assertEquals(3,rows.getLong(4));
        }
        sql("INSERT INTO model_settings(id,owner_id,version,mode,base_url,default_model,timeout_seconds) VALUES(2,1,0,'DEMO','','demo-model',60)");
    }
    @Test void existingV1InstallationUpgradesToV2WithoutLosingTasks() throws Exception {
        Flyway.configure().dataSource(url, username, password).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target("1").load().migrate();
        insertData();
        assertEquals(8, flyway(false).migrate().migrationsExecuted);
        try (var query = connection.createStatement(); var rows = query.executeQuery(
                "SELECT input,output,execution_skills FROM tasks WHERE id=1")) {
            assertTrue(rows.next()); assertEquals("old-input", rows.getString(1));
            assertEquals("old-output", rows.getString(2)); assertNull(rows.getString(3));
        }
        sql("INSERT INTO skills (version,name,instructions,enabled,owner_id,updated_at) VALUES (0,'skill','instructions',true,1,now())");
        sql("INSERT INTO agent_skills (agent_id,skill_id,position) VALUES (1,1,0)");
    }
    @Test void v4UsageIsBackfilledWithoutChangingBusinessData() throws Exception {
        Flyway.configure().dataSource(url,username,password).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target("4").load().migrate();
        insertData();sql("UPDATE tasks SET execution_model='old-model',execution_mode='API',input_tokens=17,output_tokens=9 WHERE id=1");
        assertEquals(5,flyway(false).migrate().migrationsExecuted);
        try(var query=connection.createStatement();var rows=query.executeQuery("SELECT r.source_key,r.input_tokens,r.output_tokens,a.version,t.input FROM usage_records r JOIN tasks t ON r.source_id=t.id JOIN agents a ON a.id=t.agent_id")) {
            assertTrue(rows.next());assertEquals("TASK:1",rows.getString(1));assertEquals(17,rows.getLong(2));assertEquals(9,rows.getLong(3));assertEquals(0,rows.getLong(4));assertEquals("old-input",rows.getString(5));
        }
        assertEquals(0,flyway(false).migrate().migrationsExecuted);
    }
    @Test void v5AddsProductivityOptionsWithoutChangingExistingRows() throws Exception {
        Flyway.configure().dataSource(url,username,password).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target("5").load().migrate();
        insertData();sql("INSERT INTO chat_sessions(id,owner_id,agent_id,title,updated_at) VALUES(1,1,1,'preserved conversation',now())");
        assertEquals(4,flyway(false).migrate().migrationsExecuted);
        try(var query=connection.createStatement();var rows=query.executeQuery("SELECT s.title,s.pinned,s.archived,a.temperature,a.max_output_tokens,t.input,t.execution_temperature,t.execution_max_output_tokens FROM chat_sessions s JOIN agents a ON s.agent_id=a.id JOIN tasks t ON t.agent_id=a.id")) {
            assertTrue(rows.next());assertEquals("preserved conversation",rows.getString(1));assertFalse(rows.getBoolean(2));assertFalse(rows.getBoolean(3));assertNull(rows.getObject(4));assertNull(rows.getObject(5));assertEquals("old-input",rows.getString(6));assertNull(rows.getObject(7));assertNull(rows.getObject(8));
        }
        sql("UPDATE chat_sessions SET pinned=true,archived=true WHERE id=1");
        sql("UPDATE agents SET temperature=0.35,max_output_tokens=512 WHERE id=1");
        assertEquals(0,flyway(false).migrate().migrationsExecuted);
    }

    @Test void v6UpgradesRoadmapWithoutChangingPromptsOrCredentials() throws Exception {
        Flyway.configure().dataSource(url,username,password).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target("6").load().migrate();
        insertData();sql("INSERT INTO skills(id,version,name,instructions,enabled,owner_id,updated_at) VALUES(1,3,'preserved','old prompt',true,1,now())");
        assertEquals(3,flyway(false).migrate().migrationsExecuted);
        try(var query=connection.createStatement();var rows=query.executeQuery("SELECT s.kind,s.code,s.instructions,s.version,t.execution_knowledge,u.password FROM skills s JOIN users u ON u.id=s.owner_id JOIN tasks t ON t.user_id=u.id")) {
            assertTrue(rows.next());assertEquals("PROMPT",rows.getString(1));assertNull(rows.getString(2));assertEquals("old prompt",rows.getString(3));assertEquals(3,rows.getLong(4));assertNull(rows.getString(5));assertEquals("existing-hash",rows.getString(6));
        }
        sql("INSERT INTO knowledge_documents(id,owner_id,title,content,updated_at) VALUES(1,1,'source','reference text',now())");
        sql("INSERT INTO agent_knowledge(agent_id,document_id,position) VALUES(1,1,0)");
        sql("INSERT INTO workflows(owner_id,name,definition,updated_at) VALUES(1,'flow','[]',now())");
        assertEquals(0,flyway(false).migrate().migrationsExecuted);
    }
    @Test void v7AddsDeliveryFlagsWithoutChangingHistoricalFiles() throws Exception {
        Flyway.configure().dataSource(url,username,password).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("7").load().migrate();
        insertData();
        sql("INSERT INTO chat_sessions(id,owner_id,agent_id,title,updated_at) VALUES(1,1,1,'preserved',now())");
        sql("INSERT INTO chat_turns(id,session_id,request_id,input,output,status,model,mode,skills,context_turns,created_at,deadline) VALUES(1,1,'old','question','old code','SUCCEEDED','model','DEMO','',0,now(),now())");
        assertEquals(2,flyway(false).migrate().migrationsExecuted);
        try(var query=connection.createStatement();var rows=query.executeQuery("SELECT t.project_mode,t.output,c.project_mode,c.output FROM tasks t JOIN chat_turns c ON c.id=t.id")) {
            assertTrue(rows.next());assertFalse(rows.getBoolean(1));assertEquals("old-output",rows.getString(2));assertFalse(rows.getBoolean(3));assertEquals("old code",rows.getString(4));
        }
        assertEquals(0,flyway(false).migrate().migrationsExecuted);
    }
    @Test void legacySchemaRequiresExplicitBaselineAndPreservesData() throws Exception {
        sql("CREATE TABLE users (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, username VARCHAR(255) NOT NULL, "
                + "email VARCHAR(255) NOT NULL, password VARCHAR(255) NOT NULL, role VARCHAR(255) NOT NULL)");
        sql("CREATE TABLE agents (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, agent_name VARCHAR(100) NOT NULL, "
                + "description VARCHAR(500), system_prompt TEXT NOT NULL, model_name VARCHAR(100) NOT NULL, status VARCHAR(20) NOT NULL, "
                + "creator_id BIGINT NOT NULL REFERENCES users(id), create_time TIMESTAMPTZ NOT NULL, update_time TIMESTAMPTZ NOT NULL)");
        sql("CREATE TABLE tasks (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, agent_id BIGINT NOT NULL REFERENCES agents(id), "
                + "user_id BIGINT NOT NULL REFERENCES users(id), input TEXT NOT NULL, output TEXT, status VARCHAR(20) NOT NULL "
                + "CHECK(status IN ('PENDING','RUNNING','SUCCEEDED','FAILED')), create_time TIMESTAMPTZ NOT NULL, update_time TIMESTAMPTZ NOT NULL)");
        insertData();
        assertThrows(org.flywaydb.core.api.FlywayException.class, () -> flyway(false).migrate());
        assertEquals(9, flyway(true).migrate().migrationsExecuted);
        try (var query = connection.createStatement(); var rows = query.executeQuery(
                "SELECT t.input,t.output,u.password FROM tasks t JOIN users u ON u.id=t.user_id WHERE t.id=1")) {
            assertTrue(rows.next());
            assertEquals("old-input", rows.getString(1));
            assertEquals("old-output", rows.getString(2));
            assertEquals("existing-hash", rows.getString(3));
        }
        sql("UPDATE tasks SET status='QUEUED',execution_model='model',execution_prompt='prompt' WHERE id=1");
        sql("UPDATE tasks SET status='CANCELLED' WHERE id=1");
        assertEquals(0, flyway(false).migrate().migrationsExecuted);
    }
}
