package com.meistudio.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步线程池配置。
 *
 * 用于 @Async 注解标注的异步方法。
 * 将文档向量化等重 IO 操作放入独立线程池，避免阻塞 Tomcat 的 HTTP 请求线程。
 *
 * 核心参数说明：
 * - corePoolSize: 核心线程数，常驻线程（即使空闲也不回收）
 * - maxPoolSize:  最大线程数，高峰期可扩展到这个数量
 * - queueCapacity: 等待队列容量，超过核心线程数时排队的任务数
 * - threadNamePrefix: 线程名前缀，便于在日志中定位异步任务
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "vectorTaskExecutor")
    public Executor vectorTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);        // 核心线程 4 个
        executor.setMaxPoolSize(10);        // 最大 10 个线程
        executor.setQueueCapacity(50);      // 队列容量 50
        executor.setThreadNamePrefix("vector-task-");  // 日志中的线程名前缀
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
