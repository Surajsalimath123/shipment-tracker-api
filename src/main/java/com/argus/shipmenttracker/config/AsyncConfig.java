package com.argus.shipmenttracker.config;

import com.argus.shipmenttracker.webhook.WebhookProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Bounded thread pool for asynchronous webhook delivery. The pool is
 * intentionally small — webhook delivery is mostly I/O (waiting on the
 * subscriber), so a few threads with a backing queue is the right shape.
 *
 * Rejection policy is CallerRunsPolicy: if the queue is full and the pool
 * is saturated, the calling thread runs the task itself. This applies
 * back-pressure on the API rather than dropping events.
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(WebhookProperties.class)
@RequiredArgsConstructor
public class AsyncConfig {

    private final WebhookProperties properties;

    @Bean(name = "webhookExecutor")
    public Executor webhookExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getPoolCoreSize());
        executor.setMaxPoolSize(properties.getPoolMaxSize());
        executor.setQueueCapacity(properties.getPoolQueueCapacity());
        executor.setThreadNamePrefix("webhook-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
