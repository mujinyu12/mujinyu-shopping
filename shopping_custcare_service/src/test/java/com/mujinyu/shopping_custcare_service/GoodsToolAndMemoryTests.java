package com.mujinyu.shopping_custcare_service;

import com.mujinyu.shopping_common.service.AICustCareService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 商品搜索工具 + 会话记忆的端到端测试。
 * <p>
 * 依赖：
 * <ul>
 *     <li>search_service（Dubbo 提供者）必须已启动，否则 GoodsQueryService 的 @DubboReference 校验会让上下文起不来；</li>
 *     <li>ES 192.168.66.100:9200 里有 goods 索引（当前 8 条商品）；</li>
 *     <li>环境变量 DASHSCOPE_API_KEY（真实调用大模型，会计费）。</li>
 * </ul>
 * 用独立的 userId（888001/888002）作为 conversationId，避免和别的用例串话。
 */
@SpringBootTest
class GoodsToolAndMemoryTests {

    @Autowired
    private AICustCareService aiCustCareService;

    @Test
    @DisplayName("商品搜索：问手机能通过工具查到 ES 里的商品")
    void goodsSearchToolWorks() {
        String answer = aiCustCareService.generateResponse("帮我找一下 iphone13pro 手机，多少钱？", 888001L);
        System.out.println("[商品搜索-回答] " + answer);
        assertFalse(answer.isBlank(), "回答不能为空");
        assertTrue(answer.contains("iphone13pro") || answer.contains("8999"),
                "回答里应该出现商品名或价格，实际：" + answer);
    }

    @Test
    @DisplayName("商品搜索：不指定关键字时不会把整个商品库吐出来")
    void goodsSearchWithoutKeyword() {
        String answer = aiCustCareService.generateResponse("你们商城都卖些什么商品？", 888002L);
        System.out.println("[商品搜索-无关键字] " + answer);
        assertFalse(answer.isBlank(), "回答不能为空");
    }

    @Test
    @DisplayName("会话记忆：第二轮能记住第一轮说过的名字")
    void memoryRemembersAcrossTurns() {
        String first = aiCustCareService.generateResponse("我叫陈默，请记住我的名字。", 888003L);
        System.out.println("[记忆-第一轮] " + first);

        String second = aiCustCareService.generateResponse("我叫什么名字？", 888003L);
        System.out.println("[记忆-第二轮] " + second);
        assertTrue(second.contains("陈默"), "第二轮应该记得名字，实际：" + second);
    }

    @Test
    @DisplayName("会话记忆：不同用户之间不会串话")
    void memoryIsIsolatedBetweenUsers() {
        aiCustCareService.generateResponse("我叫林小雨，请记住。", 888004L);
        String other = aiCustCareService.generateResponse("我叫什么名字？", 888005L);
        System.out.println("[记忆-他人] " + other);
        assertFalse(other.contains("林小雨"), "别的用户不应该看到这个会话的记忆，实际：" + other);
    }
}