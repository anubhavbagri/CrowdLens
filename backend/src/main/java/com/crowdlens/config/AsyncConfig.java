package com.crowdlens.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Enables Spring @Async and configures a shared thread pool.
 * Used for: parallel comment fetching, async DB persist.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "searchExecutor")
    public Executor searchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("crowdlens-async-");
        executor.setKeepAliveSeconds(60);
        executor.initialize();
        return executor;
    }
}
