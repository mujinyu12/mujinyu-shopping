package com.mujinyu.shopping_custcare_service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 只验证「接线」是否成功：Spring AI 自动装配出的 Bean 能不能注入。
 * 不会真的调用大模型（不消耗 token）。
 */
@SpringBootTest(properties = {
        // 测试时不需要注册到 Nacos、也不需要真的连数据库，避免启动依赖外部环境
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        // 测试环境给个假 key 即可，只要不为空 OpenAiChatModel 就能装配
        "spring.ai.openai.api-key=test-key"
})
class CustcareWiringTests {

    @Autowired
    private ApplicationContext context;

    @Test
    void openAiChatModelAndChatClientAreWired() {
        OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);
        assertNotNull(chatModel, "OpenAiChatModel 没装配上，检查 spring.ai.openai.api-key 与 starter 依赖");

        ChatClient.Builder builder = context.getBean(ChatClient.Builder.class);
        assertNotNull(builder, "ChatClient.Builder 没装配上，检查 spring-ai-autoconfigure-model-chat-client 依赖");
    }
}