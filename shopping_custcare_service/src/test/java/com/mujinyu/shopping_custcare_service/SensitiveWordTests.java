package com.mujinyu.shopping_custcare_service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import com.mujinyu.shopping_common.pojo.Faq;
import com.mujinyu.shopping_common.pojo.SensitiveWord;
import com.mujinyu.shopping_common.service.FaqService;
import com.mujinyu.shopping_custcare_service.config.SensitiveWordConfig;
import com.mujinyu.shopping_custcare_service.mapper.SensitiveWordMapper;
import com.mujinyu.shopping_custcare_service.service.AICustCareServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 敏感词功能的单元测试（不启动 Spring 容器，不花钱、不依赖 Nacos/MySQL/Qdrant）。
 * <p>
 * 覆盖三件事：
 * 1. {@link SensitiveWordConfig} 建出来的 {@link SensitiveWordBs} 能正常加载词库并命中（缺陷 12 之后内置词库已弃用，
 *    这里用 mock 出来的数据库词表模拟 bz_sensitive_word）；
 * 2. 正常咨询、手机型号不会被误判；
 * 3. {@link AICustCareServiceImpl} 的敏感词检查确实在 FAQ 检索和大模型调用之前拦下来。
 */
class SensitiveWordTests {

    /**
     * 模拟 bz_sensitive_word 的 deny 词：修复缺陷 12 后只加载数据库词表
     */
    private static final List<String> DB_DENY_WORDS = List.of("傻逼", "草泥马", "fuck");

    private SensitiveWordBs sensitiveWordBs;

    @BeforeEach
    void setUp() {
        // 直接复用生产配置类，测的就是线上那个 Bean。
        // 缺陷 12 之后不再加载 houbb 内置词库（里面有个 "3p" 会把 iphone13pro 误杀），
        // 所以这里用 mock Mapper 造一份数据库词表，让用例不依赖 MySQL；
        // 真实数据库词表的加载/热更新由 SensitiveWordDbTests 覆盖。
        SensitiveWordMapper mapper = mock(SensitiveWordMapper.class);
        when(mapper.selectList(any())).thenAnswer(invocation -> {
            QueryWrapper<SensitiveWord> wrapper = invocation.getArgument(0);
            // MyBatis-Plus 的参数名值对是懒加载的：先取一次 SQL 片段，paramNameValuePairs 才会被填充
            wrapper.getSqlSegment();
            // 生产代码用 eq("type", "deny") / eq("type", "allow") 区分两类词表
            boolean isAllow = wrapper.getParamNameValuePairs().containsValue("allow");
            List<String> words = isAllow ? List.of() : DB_DENY_WORDS;
            return words.stream().map(SensitiveWordTests::word).toList();
        });
        this.sensitiveWordBs = new SensitiveWordConfig(mapper).sensitiveWordBs();
    }

    private static SensitiveWord word(String value) {
        SensitiveWord sensitiveWord = new SensitiveWord();
        sensitiveWord.setWord(value);
        return sensitiveWord;
    }

    @Test
    @DisplayName("数据库词库生效：命中脏话")
    void dbDenyWordsRejectAbuse() {
        assertTrue(sensitiveWordBs.contains("你这个傻逼"), "数据库词表里应该有『傻逼』");
        List<String> words = sensitiveWordBs.findAll("草泥马，客服在吗");
        assertFalse(words.isEmpty(), "findAll 应该能列出命中的词");
        System.out.println("[敏感词] 命中：" + words);
    }

    @Test
    @DisplayName("正常咨询不误杀")
    void normalQuestionPasses() {
        assertFalse(sensitiveWordBs.contains("我的订单什么时候发货？"));
        assertFalse(sensitiveWordBs.contains("七天无理由退货怎么申请"));
        assertFalse(sensitiveWordBs.contains("你好，我想问下优惠券怎么用"));
    }

    @Test
    @DisplayName("防绕过：全角、大小写、特殊字符插字都拦得住")
    void evasionTricksAreBlocked() {
        assertTrue(sensitiveWordBs.contains("傻*逼"), "中间插 * 应仍能识别（charIgnore）");
        assertTrue(sensitiveWordBs.contains("F*U*C*K"), "英文插 * 应仍能识别");
        assertTrue(sensitiveWordBs.contains("ＦＵＣＫ"), "全角字母 + ignoreWidth 应能识别");
        assertTrue(sensitiveWordBs.contains("fuck"), "大小写应能识别");
    }

    @Test
    @DisplayName("缺陷 12 回归：iphone13pro / 14pro / 16pro 不能被误杀")
    void brandWordsAreNotBlocked() {
        // 内置词库的 "3p" 会把 iphone13pro 判成违禁，去掉内置词库后必须干净
        assertFalse(sensitiveWordBs.contains("帮我找一下 iphone13pro 手机，多少钱？"));
        assertFalse(sensitiveWordBs.contains("iphone14pro 有货吗"));
        assertFalse(sensitiveWordBs.contains("iphone16pro 什么时候降价"));
        assertFalse(sensitiveWordBs.contains("iphone15"));
        assertTrue(sensitiveWordBs.findAll("帮我找一下 iphone13pro 手机，多少钱？").isEmpty(),
                "findAll 也不应该有命中：" + sensitiveWordBs.findAll("帮我找一下 iphone13pro 手机，多少钱？"));
    }

    @Test
    @DisplayName("已知边界：用空格隔开的词当前识别不了（要更严就得自己加预处理）")
    void spaceSeparatedWordsAreNotDetected() {
        assertFalse(sensitiveWordBs.contains("傻 逼"), "库的 DFA 不跨空格，这里如实记录");
        assertFalse(sensitiveWordBs.contains("f u c k"));
    }

    @Test
    @DisplayName("AICustCareServiceImpl：敏感词在 FAQ 与大模型之前被拦下")
    void sensitiveWordShortCircuitsFaqAndAi() {
        FaqService faqService = mock(FaqService.class);
        ChatClient chatClient = mock(ChatClient.class);
        AICustCareServiceImpl service = new AICustCareServiceImpl(chatClient, faqService, sensitiveWordBs);
        ReflectionTestUtils.setField(service, "prompt", "你是客服小予，今天 {current_date}");
        ReflectionTestUtils.setField(service, "fallbackReply", "转人工");

        String answer = service.generateResponse("你这个傻逼", 1L);

        assertTrue(answer.contains("敏感词"), "应返回敏感词提示，实际：" + answer);
        verifyNoInteractions(faqService, chatClient);
    }

    @Test
    @DisplayName("AICustCareServiceImpl：空消息给引导语")
    void blankMessageGetsGreeting() {
        AICustCareServiceImpl service = new AICustCareServiceImpl(
                mock(ChatClient.class), mock(FaqService.class), sensitiveWordBs);
        ReflectionTestUtils.setField(service, "prompt", "你是客服小予");
        ReflectionTestUtils.setField(service, "fallbackReply", "转人工");

        assertEquals("请问有什么可以帮您？", service.generateResponse("   ", 1L));
        assertEquals("请问有什么可以帮您？", service.generateResponse(null, 1L));
    }

    @Test
    @DisplayName("AICustCareServiceImpl：干净问题正常走 FAQ，并把答案原样返回")
    void cleanQuestionGoesToFaq() {
        FaqService faqService = mock(FaqService.class);
        ChatClient chatClient = mock(ChatClient.class);
        Faq faq = new Faq();
        faq.setId("faq-1");
        faq.setQuestion("怎么退货");
        faq.setAnswer("在订单详情点申请售后，7 天无理由。");
        when(faqService.findBestAnswer("怎么退货")).thenReturn(faq);

        AICustCareServiceImpl service = new AICustCareServiceImpl(chatClient, faqService, sensitiveWordBs);
        ReflectionTestUtils.setField(service, "prompt", "你是客服小予");
        ReflectionTestUtils.setField(service, "fallbackReply", "转人工");

        String answer = service.generateResponse("怎么退货", 1L);

        assertEquals(faq.getAnswer(), answer);
        verifyNoInteractions(chatClient);
        assertNotNull(answer);
    }
}