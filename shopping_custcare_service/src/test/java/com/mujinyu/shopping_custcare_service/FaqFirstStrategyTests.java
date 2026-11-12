package com.mujinyu.shopping_custcare_service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mujinyu.shopping_common.pojo.Faq;
import com.mujinyu.shopping_common.service.AICustCareService;
import com.mujinyu.shopping_common.service.FaqService;
import com.mujinyu.shopping_custcare_service.mapper.FaqMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 【手动执行】验证「FAQ 优先、答不上来再问大模型」这条策略真的跑通。
 * 需要环境变量 DASHSCOPE_API_KEY（FAQ 检索要调 embedding，AI 兜底要调 chat）。
 * 会真实计费（一次 embedding + 一次对话），别放进日常回归。
 */
@SpringBootTest
class FaqFirstStrategyTests {

    @Autowired
    private FaqService faqService;

    @Autowired
    private AICustCareService aiCustCareService;

    @Autowired
    private FaqMapper faqMapper;

    /**
     * 库里已有的问句原样再问一次，必须命中它自己，且 useCount +1
     */
    @Test
    void faqHitReturnsStandardAnswerAndCounts() {
        List<Faq> enabled = faqMapper.selectList(new QueryWrapper<Faq>().eq("status", 1).last("limit 1"));
        assertFalse(enabled.isEmpty(), "bz_faq 里没有启用的数据，先灌几条 FAQ");

        Faq sample = enabled.get(0);
        int before = sample.getUseCount() == null ? 0 : sample.getUseCount();

        Faq hit = faqService.findBestAnswer(sample.getQuestion());

        assertNotNull(hit, "原句应该命中自己，检查 Qdrant 里是否已同步这批 FAQ");
        assertEquals(sample.getId(), hit.getId(), "命中的应该是同一条 FAQ");
        assertEquals(sample.getAnswer(), hit.getAnswer());

        int after = faqMapper.selectById(sample.getId()).getUseCount();
        assertEquals(before + 1, after, "命中一次，useCount 应该 +1");
        System.out.println("[FAQ 命中] " + hit.getQuestion() + " -> " + hit.getAnswer());
    }

    /**
     * 明显不在 FAQ 范围内的问题：FAQ 检索必须返回空，由大模型回答
     */
    @Test
    void faqMissFallsBackToAi() {
        String oddQuestion = "请帮我写一首关于春天的七言绝句";
        assertEquals(null, faqService.findBestAnswer(oddQuestion), "这种问题不该命中任何 FAQ");

        String answer = aiCustCareService.generateResponse(oddQuestion, 1L);
        System.out.println("[AI 兜底] " + answer);
        assertNotNull(answer);
        assertFalse(answer.isBlank(), "AI 兜底不能返回空");
    }
}