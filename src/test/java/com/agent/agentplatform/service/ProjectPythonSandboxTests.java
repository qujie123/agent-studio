package com.agent.agentplatform.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SANDBOX_TEST_GUI",matches=".+")
class ProjectPythonSandboxTests {
    final ProjectPythonSandbox sandbox = new ProjectPythonSandbox(true,System.getenv("SANDBOX_TEST_DOCKER"),"python@sha256:2d9aefe2fef018a7eb2c13064c89c71929800fd2e5dccdbf52ea5da5bb8d929a",System.getenv("SANDBOX_TEST_GUI"),new ObjectMapper());
    ProjectDelivery.Project project(String entry,ProjectDelivery.File...files){return new ProjectDelivery.Project("test",entry,List.of(files),true,List.of());}
    @Test void realConsolePreservesSubdirectoriesImportsInputAndIsolation(){
        var project=project("src/main.py",new ProjectDelivery.File("src/main.py","import helper,os,socket,sys\nassert os.getuid()==65534\nassert not os.environ.get('LLM_API_KEY')\nassert len(sys.argv)==1\nassert not os.path.exists('/var/run/docker.sock')\ntry:\n socket.create_connection(('1.1.1.1',443),timeout=.3)\n raise Exception('network allowed')\nexcept OSError: pass\ntry:\n open('/host-file','w')\n raise Exception('root writable')\nexcept OSError: pass\nprint(helper.message + input_text)"),new ProjectDelivery.File("src/helper.py","message='青松：'"));
        var result=sandbox.run(UUID.randomUUID(),"one",project,"src/main.py","hello",false);assertNull(result.error(),result.error()+" "+result.output());assertTrue(result.output().contains("青松：hello"));assertNull(result.video());
    }
    @Test void realTkWindowIsRecordedAndRetryDoesNotRunAgain(){
        var p=project("main.py",new ProjectDelivery.File("main.py","import tkinter as tk\nr=tk.Tk()\nr.title('REAL_TK_PREVIEW')\nr.geometry('500x300')\nr.configure(bg='#ff4080')\nprint('REAL_TK_WINDOW')\nr.mainloop()"));UUID id=UUID.randomUUID();
        var result=sandbox.run(id,"one",p,"main.py","",true);assertNull(result.error(),result.error()+" "+result.output());assertTrue(result.output().contains("REAL_TK_WINDOW"));assertNotNull(result.video());byte[] video=Base64.getDecoder().decode(result.video());assertTrue(video.length>1000);assertTrue(new String(video,0,Math.min(32,video.length),StandardCharsets.ISO_8859_1).contains("ftyp"));assertEquals(result,sandbox.run(id,"one",p,"main.py","",true));assertThrows(com.agent.agentplatform.exception.ConflictException.class,()->sandbox.run(id,"two",p,"main.py","",true));
    }
    @Test void realPygameWindowIsRecorded(){
        var p=project("main.py",new ProjectDelivery.File("main.py","import pygame,time\npygame.init()\ns=pygame.display.set_mode((500,300))\ns.fill((30,180,130))\npygame.display.flip()\nprint('REAL_PYGAME_WINDOW')\nwhile True:\n pygame.event.pump()\n time.sleep(.02)"));var result=sandbox.run(UUID.randomUUID(),"one",p,"main.py","",true);assertNull(result.error(),result.error()+" "+result.output());assertNotNull(result.video());assertTrue(result.output().contains("REAL_PYGAME_WINDOW"));
    }
    @Test void pythonErrorsAndTimeoutsAreNotClaimedAsSuccess(){
        var broken=project("main.py",new ProjectDelivery.File("main.py","import nonexistent_project_dependency"));var failed=sandbox.run(UUID.randomUUID(),"one",broken,"main.py","",false);assertNotNull(failed.error());assertTrue(failed.output().contains("ModuleNotFoundError"));
        var loop=project("main.py",new ProjectDelivery.File("main.py","while True: pass"));long started=System.nanoTime();var timed=sandbox.run(UUID.randomUUID(),"one",loop,"main.py","",false);assertNotNull(timed.error());assertTrue((System.nanoTime()-started)/1e9<24);
    }
}
