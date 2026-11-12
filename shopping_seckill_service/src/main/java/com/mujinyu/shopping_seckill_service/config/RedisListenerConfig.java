package com.mujinyu.shopping_seckill_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 配置Redis监听器
 */
@Configuration
public class RedisListenerConfig {

    /**
     * 向容器放入Redis监听器，监听Redis过期事件
     * @param connectionFactory Redis连接工厂
     * @return Redis监听器
     */
    @Bean
    public RedisMessageListenerContainer container(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer(); // 创建Redis监听器容器
        container.setConnectionFactory(connectionFactory); // 设置Redis连接工厂
        return container; // 返回Redis监听器
    }
}