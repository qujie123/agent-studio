package com.agent.agentplatform.service;
import com.agent.agentplatform.dto.ModelReply;
import com.agent.agentplatform.exception.ModelCallException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Component
public class StreamingModelClient {
    private final ObjectMapper mapper;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    public StreamingModelClient(ObjectMapper mapper){this.mapper=mapper;}
    public ModelReply stream(String model,List<Map<String,String>> messages,ModelSettingsService.Connection config,Double temperature,Integer maxTokens,Consumer<String> delta){
        ModelSettingsService.validate(config);StreamBody body=new StreamBody(delta,ModelProtocol.anthropic(config));CompletableFuture<HttpResponse<ModelReply>> requestFuture=null;
        try{
            var request=ModelProtocol.completion(mapper,config,model,messages,temperature,maxTokens,true);
            requestFuture=client.sendAsync(request,info->{body.success=info.statusCode()==200;return body;});
            var response=requestFuture.orTimeout(config.timeoutSeconds(),TimeUnit.SECONDS).join();
            if(response.statusCode()!=200)throw new ModelCallException("Model service returned HTTP "+response.statusCode());return response.body();
        }catch(ModelCallException failure){throw failure;}
        catch(CompletionException failure){Throwable cause=failure.getCause();if(cause instanceof ModelCallException modelFailure)throw modelFailure;throw new ModelCallException(cause instanceof TimeoutException?"Model stream timed out":"Model stream connection failed",body.reply());}
        catch(RuntimeException failure){throw new ModelCallException("Model service returned an invalid stream",body.reply());}
        finally{body.cancel();if(requestFuture!=null&&!requestFuture.isDone())requestFuture.cancel(true);}
    }
    private final class StreamBody implements HttpResponse.BodySubscriber<ModelReply> {
        final CompletableFuture<ModelReply> completed=new CompletableFuture<>();final Consumer<String> delta;final boolean anthropic;
        final ByteArrayOutputStream line=new ByteArrayOutputStream();final StringBuilder event=new StringBuilder(),text=new StringBuilder();
        Flow.Subscription subscription;long bytes;Long inputTokens,outputTokens;String finish;volatile boolean success;
        boolean messageStopped;
        StreamBody(Consumer<String> delta,boolean anthropic){this.delta=delta;this.anthropic=anthropic;}
        public CompletionStage<ModelReply> getBody(){return completed;}
        public synchronized void onSubscribe(Flow.Subscription value){subscription=value;subscription.request(1);}
        public synchronized void onNext(List<ByteBuffer> chunks){
            if(completed.isDone())return;
            try{for(var chunk:chunks){bytes+=chunk.remaining();if(bytes>1024*1024)throw new ModelCallException("Model stream exceeds the 1 MiB size limit",reply());if(!success)continue;
                while(chunk.hasRemaining()){byte b=chunk.get();if(b=='\n'){String value=line.toString(StandardCharsets.UTF_8);line.reset();if(value.endsWith("\r"))value=value.substring(0,value.length()-1);readLine(value);if(completed.isDone())return;}else line.write(b);}}
                subscription.request(1);
            }catch(ModelCallException failure){completed.completeExceptionally(failure);cancel();}
            catch(RuntimeException failure){completed.completeExceptionally(new ModelCallException("Model service returned an invalid stream",reply()));cancel();}
        }
        private void readLine(String value){if(value.isEmpty()){parseEvent();}else if(value.startsWith("data:")){if(!event.isEmpty())event.append('\n');event.append(value.substring(5).stripLeading());}}
        private void parseEvent(){
            if(event.isEmpty())return;String data=event.toString();event.setLength(0);if(data.equals("[DONE]")){finish();cancel();return;}
            var root=mapper.readTree(data);if(root.has("error"))throw new ModelCallException("Model service reported a stream error",reply());
            if(anthropic){
                switch(root.path("type").asString("")){
                    case "message_start" -> {var usage=root.path("message").path("usage");inputTokens=ModelProtocol.inputTokens(usage);outputTokens=count(usage.path("output_tokens"));}
                    case "content_block_start" -> {var block=root.path("content_block");if("text".equals(block.path("type").asString("")))append(block.path("text"));}
                    case "content_block_delta" -> {var part=root.path("delta");if("text_delta".equals(part.path("type").asString("")))append(part.path("text"));}
                    case "message_delta" -> {var reason=root.path("delta").path("stop_reason");if(reason.isString())finish=reason.asString();Long output=count(root.path("usage").path("output_tokens"));if(output!=null)outputTokens=output;}
                    case "message_stop" -> {messageStopped=true;finish();cancel();}
                    default -> { }
                }
                return;
            }
            var usage=root.path("usage");Long input=count(usage.path("prompt_tokens")),output=count(usage.path("completion_tokens"));if(input!=null)inputTokens=input;if(output!=null)outputTokens=output;
            var choice=root.path("choices").path(0);var part=choice.path("delta").path("content");if(part.isString()){String content=part.asString();text.append(content);delta.accept(content);}var reason=choice.path("finish_reason");if(reason.isString())finish=reason.asString();
        }
        private void append(tools.jackson.databind.JsonNode part){if(part.isString()&&!part.asString().isEmpty()){text.append(part.asString());delta.accept(part.asString());}}
        private Long count(tools.jackson.databind.JsonNode value){return ModelProtocol.count(value);}
        private void finish(){if(text.toString().isBlank())completed.completeExceptionally(new ModelCallException("Model stream returned no text answer",reply()));else if(!ModelProtocol.finished(finish,anthropic)||(anthropic&&!messageStopped))completed.completeExceptionally(new ModelCallException("Model stream was incomplete or requires unsupported tool calls",reply()));else completed.complete(reply());}
        public synchronized void onComplete(){if(completed.isDone())return;try{if(success){if(line.size()>0)readLine(line.toString(StandardCharsets.UTF_8));parseEvent();finish();}else completed.complete(ModelReply.text(null));}catch(RuntimeException failure){completed.completeExceptionally(new ModelCallException("Model service returned an invalid stream",reply()));}}
        public synchronized void onError(Throwable failure){completed.completeExceptionally(new ModelCallException("Model stream connection failed",reply()));}
        synchronized ModelReply reply(){return new ModelReply(text.isEmpty()?null:text.toString(),inputTokens,outputTokens);}
        synchronized void cancel(){if(subscription!=null)subscription.cancel();}
    }
}
