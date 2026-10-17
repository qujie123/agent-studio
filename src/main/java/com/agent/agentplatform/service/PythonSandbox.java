package com.agent.agentplatform.service;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

@Service
public class PythonSandbox {
    public record Status(boolean available,String message){}
    public record Result(String output,String error){}
    private final boolean enabled;private final String docker,image;private final ObjectMapper mapper;
    public PythonSandbox(@Value("${app.sandbox.enabled:false}") boolean enabled,@Value("${app.sandbox.docker:}") String docker,@Value("${app.sandbox.image:python@sha256:2d9aefe2fef018a7eb2c13064c89c71929800fd2e5dccdbf52ea5da5bb8d929a}") String image,ObjectMapper mapper){
        this.enabled=enabled;this.image=image;this.mapper=mapper;String local=System.getenv("LOCALAPPDATA");Path candidate=local==null?null:Path.of(local,"Programs","DockerDesktop","resources","bin","docker.exe");this.docker=docker.isBlank()?(candidate!=null&&Files.isRegularFile(candidate)?candidate.toString():"docker"):docker;
    }
    public Status status(){if(!enabled)return new Status(false,"管理员未启用容器执行（SANDBOX_ENABLED）");try{var process=new ProcessBuilder(docker,"image","inspect",image).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();if(!process.waitFor(3,TimeUnit.SECONDS)){process.destroyForcibly();return new Status(false,"Docker 检查超时");}return process.exitValue()==0?new Status(true,"Python 隔离执行可用：禁网、无主机目录、10 秒、128 MiB、64 KiB 输出"):new Status(false,"请启动 Docker 并准备管理员指定的 Python 镜像");}catch(IOException failure){return new Status(false,"未找到 Docker，禁止回退为主机执行");}catch(InterruptedException failure){Thread.currentThread().interrupt();return new Status(false,"容器检查被中断");}}
    public Result run(String code,String input){
        if(!enabled)throw new IllegalStateException("容器执行未启用");String name="agent-skill-"+UUID.randomUUID().toString().replace("-","");Process process=null;var output=new ByteArrayOutputStream();
        try{
            String runner="import json,sys\np=json.load(sys.stdin)\ninput_text=p['input']\nexec(compile(p['code'],'<skill>','exec'),{'input_text':input_text,'__name__':'__main__'})";
            var command=new ArrayList<>(List.of(docker,"run","--rm","--pull=never","--name",name,"--network=none","--read-only","--cap-drop=ALL","--security-opt=no-new-privileges","--user=65534:65534","--memory=128m","--memory-swap=128m","--cpus=0.5","--pids-limit=64","--ulimit=nofile=128:128","--tmpfs=/tmp:rw,noexec,nosuid,size=16m","-i",image,"python","-I","-c",runner));
            process=new ProcessBuilder(command).redirectErrorStream(true).start();final Process child=process;
            byte[] payload=mapper.writeValueAsString(Map.of("code",code,"input",input)).getBytes(StandardCharsets.UTF_8);var reading=new CompletableFuture<Void>();
            Thread.ofVirtual().start(()->{try{try(var stdin=child.getOutputStream()){stdin.write(payload);}try(var stream=child.getInputStream()){byte[] block=new byte[4096];int size;while((size=stream.read(block))!=-1){synchronized(output){if(output.size()+size>65536)throw new IllegalStateException("Skill 输出超过 64 KiB");output.write(block,0,size);}}}reading.complete(null);}catch(IOException|RuntimeException failure){reading.completeExceptionally(failure);}});
            try{reading.get(10,TimeUnit.SECONDS);if(!process.waitFor(1,TimeUnit.SECONDS))return new Result(text(output),"Skill 运行超过 10 秒");return new Result(text(output),process.exitValue()==0?null:"Skill 执行失败（退出码 "+process.exitValue()+"）");}
            catch(TimeoutException failure){return new Result(text(output),"Skill 运行超过 10 秒");}
            catch(ExecutionException failure){return new Result(text(output),failure.getCause() instanceof IllegalStateException?"Skill 输出超过 64 KiB":"Skill 输出读取失败");}
        }catch(IOException failure){return new Result(text(output),"无法启动容器，请检查 Docker 与镜像；不会在主机执行");}
        catch(InterruptedException failure){Thread.currentThread().interrupt();return new Result(text(output),"Skill 执行已中断");}
        finally{
            if(process!=null)process.destroyForcibly();
            if(name.matches("agent-skill-[a-f0-9]{32}")){try{var cleanup=new ProcessBuilder(docker,"rm","-f",name).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();if(!cleanup.waitFor(3,TimeUnit.SECONDS))cleanup.destroyForcibly();}catch(IOException ignored){}catch(InterruptedException failure){Thread.currentThread().interrupt();}}
        }
    }
    private String text(ByteArrayOutputStream value){synchronized(value){return value.toString(StandardCharsets.UTF_8);}}
}
