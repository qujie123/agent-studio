package com.agent.agentplatform.service;
import com.agent.agentplatform.exception.ModelCallException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import tools.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ModelClientRegressionTests {
    HttpServer server;
    AtomicReference<String> payload=new AtomicReference<>();
    CompatibleModelClient client=new CompatibleModelClient(new ObjectMapper());
    ModelSettingsService.Connection config;
    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/chat/completions",exchange->{try{exchange.getRequestBody().readAllBytes();byte[] data=payload.get().getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,data.length);exchange.getResponseBody().write(data);}catch(java.io.IOException ignored){}finally{exchange.close();}});
        server.start();config=new ModelSettingsService.Connection("API","http://127.0.0.1:"+server.getAddress().getPort()+"/v1","dummy-key","test-model",2,0);
    }
    @AfterEach void stop(){server.stop(0);}
    @Test void truncatedAnswerCarriesReportedUsage() {
        payload.set("{\"choices\":[{\"message\":{\"content\":\"partial\"},\"finish_reason\":\"length\"}],\"usage\":{\"prompt_tokens\":17,\"completion_tokens\":9}}");
        var failure=assertThrows(ModelCallException.class,()->client.completeReply("test-model",List.of(Map.of("role","user","content","hello")),config));
        assertEquals("partial",failure.getReply().content());assertEquals(17L,failure.getReply().inputTokens());assertEquals(9L,failure.getReply().outputTokens());
    }
    @Test void responseLimitCountsBytesRatherThanCharacters() {
        payload.set("{\"choices\":[{\"message\":{\"content\":\""+"大".repeat(400000)+"\"},\"finish_reason\":\"stop\"}]}");
        var failure=assertThrows(ModelCallException.class,()->client.completeReply("test-model",List.of(Map.of("role","user","content","hello")),config));
        assertEquals("Model response exceeds the 1 MiB size limit",failure.getMessage());
    }
}
