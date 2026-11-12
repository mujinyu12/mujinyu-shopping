package com.mujinyu.shopping_custcare_service;

import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import com.mujinyu.shopping_common.pojo.Faq;
import com.mujinyu.shopping_common.service.FaqService;
import com.mujinyu.shopping_custcare_service.service.AICustCareServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 缺陷 11 的单元测试：FAQ 阈值提高之后，还要保证「记忆类 / 商品推荐类」问题不被 FAQ 标准答案抢答。
 * <p>
 * 不启动 Spring、不连 Qdrant/MySQL、不调用大模型：
 * FAQ 服务用 Mockito 故意对任何问题都返回一条「商品什么时候发货」的标准答案，
 * 如果意图闸门失效，服务就会把这条答非所问的话术返回给用户。
 */
class FaqIntentGateTests {

    private static final String FAQ_ANSWER = "商品一般在付款后 48 小时内发货。";

    private FaqService faqService;

    private ChatClient chatClient;

    private AICustCareServiceImpl service;

    @BeforeEach
    void setUp() {
        faqService = mock(FaqService.class);
        chatClient = mock(ChatClient.class);

        Faq faq = new Faq();
        faq.setId("faq-ship");
        faq.setQuestion("商品什么时候发货");
        faq.setAnswer(FAQ_ANSWER);
        when(faqService.findBestAnswer(anyString())).thenReturn(faq);

        // 敏感词库给一个空的 deny 表，避免干扰本用例
        SensitiveWordBs sensitiveWordBs = SensitiveWordBs.newInstance()
                .wordDeny(() -> List.of())
                .init();

        service = new AICustCareServiceImpl(chatClient, faqService, sensitiveWordBs);
        ReflectionTestUtils.setField(service, "prompt", "你是客服小予");
        ReflectionTestUtils.setField(service, "fallbackReply", "转人工");
    }

    @Test
    @DisplayName("记忆类问题：即使 FAQ 有高分相似答案，也必须跳过 FAQ 走大模型")
    void memoryQuestionSkipsFaq() {
        String answer = service.generateResponse("我叫陈默，请记住我的名字。", 1L);

        // 大模型没被真正 mock 出来，调用会走兜底话术；关键是绝不能返回 FAQ 里的发货话术
        assertEquals("转人工", answer);
        verifyNoInteractions(faqService);
    }

    @Test
    @DisplayName("商品推荐类问题：FAQ 不得抢答「商品什么时候发货」")
    void goodsQuestionSkipsFaq() {
        String answer = service.generateResponse("你们商城都卖些什么商品？", 1L);

        assertEquals("转人工", answer);
        verifyNoInteractions(faqService);
    }

    @Test
    @DisplayName("政策类问题仍然优先走 FAQ，答案原样返回")
    void policyQuestionStillUsesFaq() {
        String answer = service.generateResponse("怎么退货", 1L);

        assertEquals(FAQ_ANSWER, answer);
        verify(faqService).findBestAnswer("怎么退货");
        verifyNoInteractions(chatClient);
    }
}