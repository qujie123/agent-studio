package com.agent.agentplatform.service;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SANDBOX_TEST_DOCKER",matches=".+")
class PythonSandboxTests {
    PythonSandbox sandbox=new PythonSandbox(true,System.getenv("SANDBOX_TEST_DOCKER"),"python@sha256:2d9aefe2fef018a7eb2c13064c89c71929800fd2e5dccdbf52ea5da5bb8d929a",new ObjectMapper());
    @Test void realContainerRunsWithoutHostFilesKeysOrNetwork(){assertTrue(sandbox.status().available());var result=sandbox.run("import os,socket\nprint(input_text.upper())\nassert os.getuid()==65534\nassert not os.path.exists('/var/run/docker.sock')\nassert not os.environ.get('LLM_API_KEY')\ntry:\n socket.create_connection(('1.1.1.1',443),timeout=1)\n raise Exception('network unexpectedly available')\nexcept OSError: pass\ntry:\n open('/host-file','w').write('forbidden')\n raise Exception('root filesystem unexpectedly writable')\nexcept OSError: pass\nprint('ISOLATION_OK')","hello");assertNull(result.error(),result.error()+" "+result.output());assertTrue(result.output().contains("HELLO"));assertTrue(result.output().contains("ISOLATION_OK"));}
    @Test void oversizedOutputIsFailed(){var result=sandbox.run("print('x'*100000)","");assertNotNull(result.error());assertTrue(result.output().getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=65536);}
    @Test void infiniteCodeIsTerminated(){long started=System.nanoTime();var result=sandbox.run("while True: pass","");assertNotNull(result.error());assertTrue((System.nanoTime()-started)/1e9<16);}
}
