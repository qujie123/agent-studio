package com.agent.agentplatform;

import com.agent.agentplatform.dto.*;
import com.agent.agentplatform.entity.User;
import com.agent.agentplatform.repository.*;
import com.agent.agentplatform.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"spring.profiles.active=demo","spring.datasource.url=jdbc:h2:mem:skill_execution;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","app.worker.enabled=false","app.recovery.enabled=false","app.bootstrap.username="})
class SkillExecutionIntegrationTests {
    @Autowired SkillService skills;
    @Autowired SkillExecutionService execution;
    @Autowired UserRepository users;
    @MockitoBean PythonSandbox sandbox;
    String username;
    @BeforeEach void setup(){
        username="execute_"+UUID.randomUUID().toString().substring(0,8);var user=new User();user.setUsername(username);user.setEmail(username+"@example.com");user.setPassword("test-only");user.setRole("USER");users.saveAndFlush(user);
        when(sandbox.status()).thenReturn(new PythonSandbox.Status(true,"Test sandbox"));
    }
    SkillResponse skill(){return skills.create(new SkillRequest("Python","","Explicit",true,null,"PYTHON","print(input_text)"),username);}
    @Test void concurrentDuplicateRunsExecuteSavedCodeOnlyOnce() throws Exception {
        var skill=skill();var request=new SkillRunDtos.Request(UUID.randomUUID(),"hello");var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(sandbox.run(anyString(),anyString())).thenAnswer(invocation->{entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));return new PythonSandbox.Result("hello",null);});
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var first=pool.submit(()->execution.run(skill.id(),request,username));assertTrue(entered.await(5,TimeUnit.SECONDS));
            var duplicate=execution.run(skill.id(),request,username);assertEquals("RUNNING",duplicate.status());release.countDown();
            var result=first.get(5,TimeUnit.SECONDS);assertEquals(result.id(),duplicate.id());assertEquals("SUCCEEDED",result.status());
            verify(sandbox,times(1)).run("print(input_text)","hello");assertEquals(1,execution.page(username,0,20).total());
        }finally{release.countDown();}
    }
    @Test void storedResultCanBeConfirmedAfterSkillIsDisabledButInputCannotChange(){
        var skill=skill();var request=new SkillRunDtos.Request(UUID.randomUUID(),"hello");when(sandbox.run(anyString(),anyString())).thenReturn(new PythonSandbox.Result("saved",null));
        var first=execution.run(skill.id(),request,username);skills.update(skill.id(),new SkillRequest(skill.name(),"",skill.instructions(),false,skill.version(),"PYTHON",skill.code()),new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(username,"",java.util.List.of()));
        when(sandbox.status()).thenReturn(new PythonSandbox.Status(false,"Docker unavailable"));
        assertEquals(first.id(),execution.run(skill.id(),request,username).id());
        assertThrows(com.agent.agentplatform.exception.ConflictException.class,()->execution.run(skill.id(),new SkillRunDtos.Request(request.requestId(),"changed"),username));
        verify(sandbox,times(1)).run(anyString(),anyString());
    }
}
