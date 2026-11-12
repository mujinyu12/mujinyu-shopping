package com.mujinyu.shopping_custcare_service.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mujinyu.shopping_common.pojo.Faq;
import com.mujinyu.shopping_common.service.FaqService;
import com.mujinyu.shopping_custcare_service.mapper.FaqMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * FAQ服务实现类
 * 提供FAQ问答相关的业务功能
 */
@DubboService
@RequiredArgsConstructor
@Service
@Slf4j
public class FaqServiceImpl implements FaqService {
    private final FaqMapper faqMapper;
    // spring ai提供的操作连接数据库的工具
    private final VectorStore vectorStore;

    /**
     * 相似度阈值（0~1，越大越严格）。
     * 注意：这不是 Spring AI 的自动配置项，必须从 Nacos 读出来再传给 SearchRequest。
     * <p>
     * 默认值从 0.45 提到 0.62：0.45 这条线太松，实测「你们商城都卖些什么商品？」
     * 对「商品什么时候发货」的相似度有 0.6161，会被标准答案抢先命中，AI 根本没机会回答。
     * 真正的 FAQ（「怎么退货」「什么时候发货」）分数普遍在 0.75 以上，0.62 能挡住闲聊和商品类问题。
     * 线上以 Nacos 的 custcare.rag.score-threshold 为准，改完必须重启（本类没有 @RefreshScope）。
     */
    @Value("${custcare.rag.score-threshold:0.62}")
    private double scoreThreshold;

    /**
     * 一次取几条候选。阈值只保证"像"，不保证"对"，多取几条再回表校验状态更稳。
     */
    @Value("${custcare.rag.top-k:3}")
    private int topK;

    /**
     * 分页查询FAQ列表
     * @param page 当前页码
     * @param size 每页条数
     * @param categoryId 分类ID（可选）
     * @return FAQ分页列表
     */
    @Override
    public Page<Faq> getFaqPage(int page, int size, Integer categoryId) {
        QueryWrapper<Faq> queryWrapper = new QueryWrapper<>();
        if (categoryId != null) {
            queryWrapper.eq("categoryId", categoryId);
        }
        Page<Faq> faqPage = new Page<>(page, size);
        return faqMapper.selectPage(faqPage, queryWrapper);
    }

    /**
     * 根据ID查询FAQ
     * @param id FAQ主键
     * @return FAQ对象
     */
    @Override
    public Faq getFaqById(String id) {
        return faqMapper.selectById(id) ;
    }

    /**
     * 创建新的Faq
     * @param faq FAQ对象
     * @return 创建的FAQ的ID
     */
    @Override
    public String createFaq(Faq faq) {
        //  将FAQ保存到mysql数据库
        // 设置默认值
        if (faq.getId() == null) {
            faq.setId(UUID.randomUUID().toString());
        }
        if (faq.getStatus() == null) {
            faq.setStatus(1);
        }
        if (faq.getUseCount() == null) {
            faq.setUseCount(0);
        }
        faqMapper.insert(faq);
        // 将FAQ保存到向量数据库
        if (faq.getStatus() == 1) {
            insertFaqToQdrant(faq);
        }
        return faq.getId();
    }

    /**
     * 将FAQ插入到向量数据库
     * @param faq FAQ对象
     */
    public void insertFaqToQdrant(Faq faq) {
        // 构建元数据
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("faq_id", faq.getId());
        metadata.put("category_id", faq.getCategoryId());
        metadata.put("question", faq.getQuestion());
        metadata.put("answer", faq.getAnswer());
        // 构建Document
        Document document = new Document(faq.getId(), faq.getQuestion(), metadata);
        List<Document> documents = new ArrayList<>();
        documents.add(document);
        vectorStore.add(documents);
    }

    /**
     * 更新FAQ
     * @param faq FAQ对象
     */
    @Override
    public void updateFaq(Faq faq) {
        // 更新mysql数据库
        faqMapper.updateById(faq);
        // 以库里的最终数据为准（只传了部分字段时，faq.getStatus() 可能是 null，判断会错）
        Faq saved = faqMapper.selectById(faq.getId());
        if (saved == null) {
            // 记录已经被删掉了，向量库里的旧点也要清掉
            deleteFaqFromQdrant(faq.getId());
            return;
        }
        // 更新Qdrant数据库：先删旧点，再按新内容写入（点 id 就是 FAQ 主键，等价于覆盖）
        deleteFaqFromQdrant(saved.getId());
        if (saved.getStatus() != null && saved.getStatus() == 1) {
            insertFaqToQdrant(saved);
        }
    }

    /**
     * 删除FAQ
     * @param id FAQ主键ID
     */
    @Override
    public void deleteFaq(String id) {
        // 从mysql数据库中删除
        faqMapper.deleteById(id);
        // 从向量数据库中删除
        deleteFaqFromQdrant(id);
    }

    /**
     * 使用向量搜索，智能匹配最佳答案
     * @param question 用户问题
     * @return 最佳匹配的FAQ对象，如果没有匹配则返回Null
     */
    @Override
    public Faq findBestAnswer(String question) {
        if (!StringUtils.hasText(question)) {
            return null;
        }
        // 1、向量检索：没命中就直接返回 null，由上层降级到 AI
        Faq faq = findByVectorSearch(question);
        if (faq == null) {
            return null;
        }
        // 2、命中后累加使用次数（统计用，失败不影响回答）
        increaseFaqUseCount(faq.getId());
        return faq;
    }

    /**
     * 使用向量数据库搜索匹配FAQ答案
     *
     * @param question 用户问题
     * @return 匹配到的启用状态 FAQ；没有匹配则返回 null
     */
    private Faq findByVectorSearch(String question) {
        SearchRequest searchRequest = SearchRequest.builder()
                .query(question) // 查询文本或向量
                .topK(Math.max(1, topK)) // 返回结果数量限制
                .similarityThreshold(scoreThreshold) // 相似度阈值
                .build();
        List<Document> results = vectorStore.similaritySearch(searchRequest); // 相似度查询
        if (results == null || results.isEmpty()) {
            log.info("FAQ 未命中（阈值 {}），交给大模型兜底。question={}", scoreThreshold, question);
            return null;
        }

        // 按分数从高到低逐条回表校验：可能命中已删除/已禁用的 FAQ，跳过继续看下一条
        for (Document document : results) {
            String faqId = resolveFaqId(document);
            if (!StringUtils.hasText(faqId)) {
                continue;
            }
            Faq faq = faqMapper.selectById(faqId);
            if (faq == null) {
                log.warn("向量库命中了 MySQL 里已不存在的 FAQ，id={}，建议重新同步向量库", faqId);
                continue;
            }
            if (faq.getStatus() == null || faq.getStatus() != 1) {
                log.info("命中的 FAQ 已禁用，跳过。id={}", faqId);
                continue;
            }
            log.info("FAQ 命中：score={} id={} question={}", document.getScore(), faqId, faq.getQuestion());
            return faq;
        }
        return null;
    }

    /**
     * 从命中的向量文档里取出 FAQ 主键：优先 metadata.faq_id，兜底用 point id
     * （同步时 Document 的 id 就是 FAQ 主键，两种方式都能取到）
     */
    private String resolveFaqId(Document document) {
        Object faqId = document.getMetadata().get("faq_id");
        return faqId != null ? faqId.toString() : document.getId();
    }

    /**
     * 增加FAQ使用次数
     * @param faqId FAQ主键ID
     */
    private void increaseFaqUseCount(String faqId) {
        try {
            // 用 SQL 自增而不是「查出来 +1 再写回」，避免并发下读到同一个旧值导致少计数
            faqMapper.update(null, new UpdateWrapper<Faq>()
                    .eq("id", faqId)
                    .setSql("useCount = useCount + 1"));
        } catch (Exception e) {
            // 计数失败不该影响用户拿答案
            log.warn("FAQ 使用次数自增失败，id={}：{}", faqId, e.getMessage());
        }
    }

    /**
     * 从向量数据库删除FAQ
     * @param id FAQ主键ID
     */
    private void deleteFaqFromQdrant(String id) {
        List<String> docs = new ArrayList<>();
        docs.add(id);
        vectorStore.delete(docs);
    }

    /**
     * 同步所有FAQ到向量数据库
     */
    public void syncToQdrant() {
        // 1、查询所有启用的FAQ
        QueryWrapper<Faq> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", 1);
        List<Faq> faqList = faqMapper.selectList(queryWrapper);
        log.info("同步FAQ到Qdrant：待同步 {} 条", faqList.size());

        // 2、插入到向量数据库
        for (Faq faq : faqList) {
            insertFaqToQdrant(faq);
        }
        log.info("同步FAQ到Qdrant完成：已写入 {} 条", faqList.size());
    }
}
