package com.mujinyu.shopping_seckill_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置
 */
@Configuration
public class RedisConfig {

    /**
     * 统一RedisTemplate键的序列化方式为字符串
     * 1、避免在业务代码运行期临时修改序列化器，导致同一个逻辑键在Redis中出现两份物理键
     *    （JDK序列化字节串和普通字符串），定时任务写入与用户端查询读到的不是同一份数据
     * 2、订单过期事件中只能拿到字符串格式的键名，需要依靠它拼出订单副本的键
     * 注意：hash的field存的是秒杀商品id（Long），不能使用StringRedisSerializer，保持默认序列化器即可
     *
     * @param redisTemplate Spring Boot自动配置的RedisTemplate
     */
    public RedisConfig(RedisTemplate<Object, Object> redisTemplate) {
        redisTemplate.setKeySerializer(new StringRedisSerializer());
    }
}