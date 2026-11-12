package com.mujinyu.shopping_custcare_service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 向量库接线测试：验证 EmbeddingModel + QdrantVectorStore 装配，并真跑一次「写入 → 检索 → 删除」。
 * 用临时集合 wiring_test，不污染正式的 common_response。
 * 需要环境变量 DASHSCOPE_API_KEY（IDEA Run Configuration 里那个）。
 */
@SpringBootTest(properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "spring.ai.vectorstore.qdrant.collection-name=wiring_test"
})
class QdrantWiringTests {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private VectorStore vectorStore;

    @Test
    void embeddingAndVectorStoreRoundTrip() {
        // 维度必须和集合一致：common_response 是 1024 维，所以 embedding 也必须是 1024
        assertEquals(1024, embeddingModel.dimensions(),
                "text-embedding-v4 + dimensions=1024 才对得上 1024 维的集合");

        Document doc = new Document("退款说明：签收后7天内可无理由退货，运费由买家承担。",
                Map.of("faqId", "1"));
        vectorStore.add(List.of(doc));

        List<Document> hits = vectorStore.similaritySearch(
                SearchRequest.builder().query("买了不想要了怎么退").topK(1).build());
        assertFalse(hits.isEmpty(), "应该能检索回刚写入的文档");
        assertTrue(hits.get(0).getText().contains("7天"), "命中的应该是退货那条");

        vectorStore.delete(List.of(doc.getId()));
    }
}