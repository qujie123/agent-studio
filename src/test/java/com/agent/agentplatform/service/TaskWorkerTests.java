package com.agent.agentplatform.service;
import com.agent.agentplatform.dto.TaskExecution;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskWorkerTests {
    @Test void slowTaskDoesNotBlockNextTaskAndConcurrencyIsBounded() throws Exception {
        TaskService tasks=mock(TaskService.class);TaskExecutionService execution=mock(TaskExecutionService.class);
        var slow=new TaskExecution(1L,"model","prompt","slow","DEMO",0L);var fast=new TaskExecution(2L,"model","prompt","fast","DEMO",0L);
        when(tasks.claimQueuedTask()).thenReturn(Optional.of(slow),Optional.of(fast),Optional.empty());
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var finished=new CountDownLatch(1);var active=new AtomicInteger();var peak=new AtomicInteger();
        when(execution.runClaimed(any())).thenAnswer(call->{TaskExecution task=call.getArgument(0);int running=active.incrementAndGet();peak.accumulateAndGet(running,Math::max);
            try{if(task.taskId()==1L){entered.countDown();release.await(3,TimeUnit.SECONDS);}else{entered.await(3,TimeUnit.SECONDS);finished.countDown();}}finally{active.decrementAndGet();}return null;});
        var pool=Executors.newFixedThreadPool(2);
        try{var worker=new TaskWorker(tasks,execution,pool,2);worker.poll();assertTrue(finished.await(2,TimeUnit.SECONDS));assertEquals(1,release.getCount());assertTrue(peak.get()<=2);verify(tasks,times(2)).claimQueuedTask();}
        finally{release.countDown();pool.shutdown();assertTrue(pool.awaitTermination(3,TimeUnit.SECONDS));}
    }
}
