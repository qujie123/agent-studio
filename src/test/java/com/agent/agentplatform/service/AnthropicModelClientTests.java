package com.agent.agentplatform.service;

import com.agent.agentplatform.exception.ModelCallException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import tools.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AnthropicModelClientTests {
    final ObjectMapper mapper = new ObjectMapper();
    HttpServer server; String body, key, authorization, version, path, mode="OK", stop="end_turn";
    int status=200;
    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1",exchange->{
            try {
                body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
                key=exchange.getRequestHeaders().getFirst("x-api-key");authorization=exchange.getRequestHeaders().getFirst("Authorization");
                version=exchange.getRequestHeaders().getFirst("anthropic-version");path=exchange.getRequestURI().getPath();
                String answer;
                if(status!=200)answer="secret-body-must-not-leak";
                else if(path.endsWith("/models"))answer="{\"data\":[{\"id\":\"claude-test\"}],\"has_more\":true}";
                else if(mapper.readTree(body).path("stream").asBoolean()) {
                    answer=event("{\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":10,\"cache_creation_input_tokens\":2,\"cache_read_input_tokens\":3,\"output_tokens\":0}}}")
                        +event("{\"type\":\"content_block_start\",\"content_block\":{\"type\":\"text\",\"text\":\"\"}}")
                        +event("{\"type\":\"ping\"}")
                        +event("{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"not visible\"}}")
                        +event("{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"中文\"}}")
                        +event("{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\" reply\"}}")
                        +event("{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\""+stop+"\"},\"usage\":{\"output_tokens\":7}}")
                        +(mode.equals("TRUNCATED")?"":event("{\"type\":\"message_stop\"}"));
                    if(mode.equals("ERROR"))answer=event("{\"type\":\"error\",\"error\":{\"message\":\"secret-body-must-not-leak\"}}");
                    exchange.getResponseHeaders().set("Content-Type","text/event-stream");
                } else answer="{\"type\":\"message\",\"content\":[{\"type\":\"thinking\",\"thinking\":\"not visible\"},{\"type\":\"text\",\"text\":\"中文 reply\"}],\"stop_reason\":\""+stop+"\",\"usage\":{\"input_tokens\":10,\"cache_creation_input_tokens\":2,\"cache_read_input_tokens\":3,\"output_tokens\":7}}";
                byte[] bytes=answer.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,bytes.length);
                // Split UTF-8 bytes and event boundaries deliberately.
                for(byte b:bytes){exchange.getResponseBody().write(b);exchange.getResponseBody().flush();}
            } finally {exchange.close();}
        });server.start();
    }
    @AfterEach void stop(){server.stop(0);}
    String event(String data){return "event: test\r\ndata: "+data+"\r\n\r\n";}
    ModelSettingsService.Connection config(){return new ModelSettingsService.Connection("API","http://127.0.0.1:"+server.getAddress().getPort()+"/v1","fake-anthropic-key","claude-test",3,1,1L,"ANTHROPIC");}
    List<Map<String,String>> messages(){return List.of(Map.of("role","system","content","instructions"),Map.of("role","user","content","hello"),Map.of("role","assistant","content","previous"),Map.of("role","user","content","continue"));}
    @Test void nativeRequestPreservesHistoryAndSeparatesSystemWithCorrectAuthentication(){
        var reply=new CompatibleModelClient(mapper).completeReply("claude-test",messages(),config(),0.5,512);
        assertEquals("/v1/messages",path);assertEquals("fake-anthropic-key",key);assertNull(authorization);assertEquals("2023-06-01",version);
        var request=mapper.readTree(body);assertEquals("instructions",request.path("system").asString());assertEquals(3,request.path("messages").size());assertEquals(512,request.path("max_tokens").asInt());assertEquals(0.5,request.path("temperature").asDouble());assertFalse(request.has("stream_options"));
        assertEquals("中文 reply",reply.content());assertEquals(15,reply.inputTokens());assertEquals(7,reply.outputTokens());
    }
    @Test void nativeDefaultHasRequiredOutputLimit(){new CompatibleModelClient(mapper).completeReply("claude-test",messages(),config());assertEquals(4096,mapper.readTree(body).path("max_tokens").asInt());}
    @Test void streamPreservesTextAndCumulativeUsage(){var chunks=new ArrayList<String>();var reply=new StreamingModelClient(mapper).stream("claude-test",messages(),config(),null,null,chunks::add);assertEquals(List.of("中文"," reply"),chunks);assertEquals("中文 reply",reply.content());assertEquals(15,reply.inputTokens());assertEquals(7,reply.outputTokens());assertFalse(body.contains("stream_options"));}
    @Test void truncatedOrToolRepliesAreNotSuccessful(){for(String reason:List.of("max_tokens","tool_use","pause_turn")){stop=reason;var error=assertThrows(ModelCallException.class,()->new CompatibleModelClient(mapper).completeReply("claude-test",messages(),config()));assertEquals("中文 reply",error.getReply().content());assertEquals(7,error.getReply().outputTokens());assertThrows(ModelCallException.class,()->new StreamingModelClient(mapper).stream("claude-test",messages(),config(),null,null,t->{}));}}
    @Test void streamRequiresTerminalMessageStop(){mode="TRUNCATED";var error=assertThrows(ModelCallException.class,()->new StreamingModelClient(mapper).stream("claude-test",messages(),config(),null,null,t->{}));assertEquals("中文 reply",error.getReply().content());}
    @Test void providerErrorsAndRedirectsNeverLeakOrFollow(){for(int code:List.of(401,429,302)){status=code;var error=assertThrows(ModelCallException.class,()->new CompatibleModelClient(mapper).completeReply("claude-test",messages(),config()));assertEquals("Model service returned HTTP "+code,error.getMessage());}status=200;mode="ERROR";var error=assertThrows(ModelCallException.class,()->new StreamingModelClient(mapper).stream("claude-test",messages(),config(),null,null,t->{}));assertFalse(error.getMessage().contains("secret-body"));}
    @Test void catalogUsesNativeAuthenticationAndReportsPagination(){var catalog=new ModelCatalogService(mapper).read(config());assertEquals(List.of("claude-test"),catalog.models());assertTrue(catalog.truncated());assertEquals("/v1/models",path);assertEquals("fake-anthropic-key",key);assertNull(authorization);}
}
