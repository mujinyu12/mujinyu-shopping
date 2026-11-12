package com.mujinyu.shopping_custcare_service;

import com.mujinyu.shopping_custcare_service.memory.RedisChatMemoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableDubbo
@EnableDiscoveryClient
@RefreshScope
@MapperScan("com.mujinyu.shopping_custcare_service.mapper")
@Slf4j
public class ShoppingCustcareServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShoppingCustcareServiceApplication.class, args);
        log.info("**************** 客服微服务启动成功 ****************");
    }

    /**
     * 会话记忆
     * <p>
     * Spring AI 1.0.0 把记忆拆成了两层：ChatMemoryRepository（存什么）和 ChatMemory（怎么取）。
     * MessageWindowChatMemory 负责「滑动窗口」（只保留最近 20 条，防止 token 超限），
     * 存储交给 {@link RedisChatMemoryRepository}：一个会话一个 Redis key，TTL 7 天，
     * 服务重启、多实例部署都不会丢记忆（缺陷 34 之前用的是 InMemoryChatMemoryRepository，重启即丢）。
     *
     * @param redisChatMemoryRepository Redis 会话记忆仓库（key 前缀 custcare:memory:）
     * @return 会话记忆
     */
    @Bean
    public ChatMemory chatMemory(RedisChatMemoryRepository redisChatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(redisChatMemoryRepository)
                .maxMessages(20) // 每个会话最多保留最近 20 条消息，防止 token 超限
                .build();
    }

    /**
     * AI聊天客户端
     * <p>
     * 会话记忆必须通过 Advisor 才能生效：ChatMemory Bean 本身只是存储，
     * 只有注册了 MessageChatMemoryAdvisor，调用时才去取历史消息、并把本轮对话写回去。
     * 具体用哪个会话（conversationId）在调用处传：
     * {@code .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, userId))}
     *
     * @param builder    聊天客户端构建器
     * @param chatMemory 会话记忆
     * @return 聊天客户端
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory) {
        return builder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultToolNames("getOrderQueryService", "getGoodsQueryService")
                .build();
    }
}
