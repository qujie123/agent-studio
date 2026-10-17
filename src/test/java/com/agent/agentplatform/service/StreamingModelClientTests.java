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

class StreamingModelClientTests {
    HttpServer server;String mode="OK",body;
    StreamingModelClient client=new StreamingModelClient(new ObjectMapper());
    @BeforeEach void start() throws Exception {server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/v1/chat/completions",exchange->{body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","text/event-stream");exchange.sendResponseHeaders(200,0);try{
        if(mode.equals("INVALID")){exchange.getResponseBody().write("data: {secret-invalid}\n\n".getBytes(StandardCharsets.UTF_8));return;}
        if(mode.equals("LARGE")){exchange.getResponseBody().write((":"+"x".repeat(1024*1024+1)).getBytes(StandardCharsets.UTF_8));return;}
        String first="data: {\"choices\":[{\"delta\":{\"content\":\"中文\"},\"finish_reason\":null}]}\r\n\r\n";for(byte b:first.getBytes(StandardCharsets.UTF_8)){exchange.getResponseBody().write(b);exchange.getResponseBody().flush();}
        if(mode.equals("SLOW")){try{Thread.sleep(3000);}catch(InterruptedException ignored){}return;}
        String last="data: {\"choices\":[{\"delta\":{\"content\":\" answer\"},\"finish_reason\":\""+(mode.equals("LENGTH")?"length":"stop")+"\"}]}\n\ndata: {\"choices\":[],\"usage\":{\"prompt_tokens\":17,\"completion_tokens\":9}}\n\ndata: [DONE]\n\n";exchange.getResponseBody().write(last.getBytes(StandardCharsets.UTF_8));
    }finally{exchange.close();}});server.start();}
    @AfterEach void stop(){server.stop(0);}
    ModelSettingsService.Connection config(){return new ModelSettingsService.Connection("API","http://127.0.0.1:"+server.getAddress().getPort()+"/v1","test-stream-key","stream-model",1,0L,1L);}
    @Test void actualSseHandlesSplitUtf8AndTokenUsage(){var chunks=new ArrayList<String>();var reply=client.stream("stream-model",List.of(Map.of("role","user","content","hello")),config(),0.35,512,chunks::add);assertEquals("中文 answer",reply.content());assertEquals(List.of("中文"," answer"),chunks);assertEquals(17,reply.inputTokens());assertEquals(9,reply.outputTokens());assertTrue(body.contains("\"stream\":true"));assertTrue(body.contains("\"max_tokens\":512"));}
    @Test void incompleteStreamPreservesPartialAndUsage(){mode="LENGTH";var failure=assertThrows(ModelCallException.class,()->client.stream("m",List.of(),config(),null,null,t->{}));assertEquals("中文 answer",failure.getReply().content());assertEquals(9,failure.getReply().outputTokens());}
    @Test void timeoutCoversBodyAndKeepsReceivedText(){mode="SLOW";var chunks=new AtomicReference<String>("");long start=System.nanoTime();var failure=assertThrows(ModelCallException.class,()->client.stream("m",List.of(),config(),null,null,chunks::set));assertTrue((System.nanoTime()-start)/1e9<2.5);assertEquals("中文",chunks.get());assertEquals("中文",failure.getReply().content());}
    @Test void invalidAndOversizedStreamErrorsNeverEchoProviderBody(){for(String next:List.of("INVALID","LARGE")){mode=next;var failure=assertThrows(ModelCallException.class,()->client.stream("m",List.of(),config(),null,null,t->{}));assertFalse(failure.getMessage().contains("secret-invalid"));}}
}
