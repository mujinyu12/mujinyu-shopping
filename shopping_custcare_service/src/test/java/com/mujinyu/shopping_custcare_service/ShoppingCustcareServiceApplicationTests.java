package com.mujinyu.shopping_custcare_service;

import com.mujinyu.shopping_custcare_service.service.FaqServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 只验证 Spring 上下文与 FaqServiceImpl（含 VectorStore 依赖）能装配。
 * 真正「写入 → 检索 → 删除」向量库的验证在 QdrantWiringTests，这里不要调用 syncToQdrant()，
 * 否则每次跑测试都会真实调用 Embedding API 并把全量 FAQ 灌进正式集合。
 */
@SpringBootTest(properties = {
        // 测试时不需要注册到 Nacos、也不需要真的连数据库，避免启动依赖外部环境
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        // 依赖链 faqServiceImpl -> vectorStore(Qdrant) -> openAiEmbeddingModel，
        // 而 OpenAiEmbeddingModel 只要 key 非空就能装配。这里给假 key，
        // 避免 Nacos 配置没拉到 / 当前 JVM 没注入 DASHSCOPE_API_KEY 时报
        // "OpenAI API key must be set"
        "spring.ai.openai.api-key=${DASHSCOPE_API_KEY}"
})
class ShoppingCustcareServiceApplicationTests {

    @Autowired
    private FaqServiceImpl faqService;

    @Test
    void contextLoads() {
        assertNotNull(faqService, "FaqServiceImpl 没装配上，检查 FaqMapper 与 VectorStore");
    }

}
