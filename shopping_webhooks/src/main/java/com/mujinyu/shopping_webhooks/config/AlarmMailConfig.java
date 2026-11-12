package com.mujinyu.shopping_webhooks.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 告警邮件线程池
 *
 * <p>SkyWalking 回调 /alarm 时不能同步等待 SMTP；单独建池，
 * 与 Web 容器线程隔离，队列满时由调用线程执行形成背压，避免静默丢弃告警。</p>
 */
@Configuration
public class AlarmMailConfig {

    @Bean(name = "alarmMailExecutor", destroyMethod = "shutdown")
    public ExecutorService alarmMailExecutor() {
        AtomicInteger index = new AtomicInteger(1);
        return new ThreadPoolExecutor(
                2, 4, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(200),
                runnable -> {
                    Thread thread = new Thread(runnable, "alarm-mail-" + index.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
    }
}