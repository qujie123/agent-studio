package com.agent.agentplatform.service;
import com.agent.agentplatform.dto.ChatDtos.*;
import com.agent.agentplatform.dto.ModelReply;
import com.agent.agentplatform.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Semaphore;

@Service
public class ChatStreamingService {
    private final ChatService chats;private final StreamingModelClient client;private final Semaphore slots=new Semaphore(3);
    public ChatStreamingService(ChatService chats,StreamingModelClient client){this.chats=chats;this.client=client;}
    public SseEmitter send(Long id,Send request,String username){
        if(!slots.tryAcquire())throw new ModelUnavailableException("流式生成繁忙，请稍后再试");
        ChatService.Pending pending;try{pending=chats.begin(id,request,username);}catch(RuntimeException failure){slots.release();throw failure;}
        var emitter=new SseEmitter(150000L);
        Thread.ofVirtual().start(()->{try{
            emit(emitter,"turn",pending.turn());if(pending.connection()==null){emit(emitter,"done",pending.turn());return;}
            ModelReply answer=ModelReply.text(null);String error=null;
            try{if("DEMO".equals(pending.connection().mode())){String text=pending.turn().projectMode()?ProjectDelivery.demo():"【模拟流式回复 · 未调用真实模型】\n你的消息："+pending.turn().input();int chunk=pending.turn().projectMode()?512:8;for(int i=0;i<text.length();i+=chunk){emit(emitter,"delta",Map.of("text",text.substring(i,Math.min(text.length(),i+chunk))));Thread.sleep(25);}answer=ModelReply.text(text);}
                else answer=client.stream(pending.turn().model(),pending.messages(),pending.connection(),pending.temperature(),pending.maxOutputTokens(),delta->emit(emitter,"delta",Map.of("text",delta)));
            }catch(ModelCallException failure){answer=failure.getReply();error=failure.getMessage();}
            catch(InterruptedException failure){Thread.currentThread().interrupt();error="流式生成已中断";}
            catch(RuntimeException failure){error="流式生成失败，请检查服务商是否支持流式接口";}
            emit(emitter,"done",chats.finishReply(id,pending.turn().id(),answer,error,username));
        }finally{slots.release();emitter.complete();}});return emitter;
    }
    private void emit(SseEmitter emitter,String event,Object value){try{emitter.send(SseEmitter.event().name(event).data(value));}catch(IOException|IllegalStateException ignored){/* Persist the provider result even after a browser disconnect; never retry the paid call. */}}
}
