package com.agent.agentplatform.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class WorkerConfiguration {
    @org.springframework.context.annotation.Bean(name = "taskExecutionPool", destroyMethod = "close")
    @ConditionalOnProperty(name = "app.worker.enabled", havingValue = "true", matchIfMissing = true)
    public java.util.concurrent.ExecutorService taskExecutionPool(@org.springframework.beans.factory.annotation.Value("${app.worker.concurrency:3}") int concurrency) {
        if (concurrency < 1 || concurrency > 8) { throw new IllegalArgumentException("Worker concurrency must be between 1 and 8"); }
        return java.util.concurrent.Executors.newFixedThreadPool(concurrency);
    }
}

