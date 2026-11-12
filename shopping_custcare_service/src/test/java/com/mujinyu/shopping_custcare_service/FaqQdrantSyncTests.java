package com.mujinyu.shopping_custcare_service;

import com.mujinyu.shopping_custcare_service.service.FaqServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 【手动执行】把 MySQL 里 bz_faq（status=1）的 FAQ 全量同步到 Qdrant 的 common_response 集合。
 *
 * 注意：
 * 1. 会真实调用 DashScope 的 Embedding 接口（按条数计费），所以不要放进日常回归；
 * 2. 需要当前 JUnit 运行配置里有环境变量 DASHSCOPE_API_KEY（Nacos 里只是个占位符）；
 * 3. 重复执行是安全的：Document 用 FAQ 主键当 id，Qdrant 按 id upsert，不会产生重复点。
 */
@SpringBootTest
class FaqQdrantSyncTests {

    @Autowired
    private FaqServiceImpl faqService;

    @Autowired
    private VectorStore vectorStore;

    @Test
    void syncFaqToQdrant() {
        // 1. 触发同步：查 status=1 的 FAQ，逐条 embedding 后写入 Qdrant
        faqService.syncToQdrant();

        // 2. 回查确认真的写进去了（不是只跑了循环没报错）
        List<Document> hits = vectorStore.similaritySearch(
                SearchRequest.builder().query("买了东西不想要了怎么退").topK(3).build());
        assertFalse(hits.isEmpty(), "Qdrant 里一条都没查到，同步没生效，先看日志里 Embedding 是否 401");

        hits.forEach(d -> System.out.println("[QDRANT] id=" + d.getId()
                + " | text=" + d.getText()
                + " | metadata=" + d.getMetadata()));
    }
}