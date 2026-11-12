package com.mujinyu.shopping_custcare_service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import com.mujinyu.shopping_common.pojo.SensitiveWord;
import com.mujinyu.shopping_common.service.SensitiveWordService;
import com.mujinyu.shopping_custcare_service.mapper.SensitiveWordMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 敏感词「数据库接入」的集成测试。
 *
 * <p>跟 {@link SensitiveWordTests} 的区别：那个不启动 Spring，用 mock 出来的数据库词表验证配置类与
 * AICustCareServiceImpl 的短路逻辑；这个要真的连 MySQL（baizhanshopping.bz_sensitive_word），验证
 * <ol>
 *   <li>{@code SensitiveWordConfig} 里的匿名 IWordDeny/IWordAllow 确实查了库；</li>
 *   <li>库里新加的 10 个默认词库没有的词，初始化后能被 contains 命中；</li>
 *   <li>addSensitiveWord / deleteSensitiveWord 能热更新词库（不重启服务）；</li>
 *   <li>白名单能把黑名单词"洗白"（WordAllows 的语义）。</li>
 * </ol>
 *
 * <p>要求：Nacos（192.168.66.100:8848）与 MySQL（127.0.0.1:3306/baizhanshopping）可连。
 * 本测试不会调用大模型，不产生 token 费用。
 */
@SpringBootTest
class SensitiveWordDbTests {

    /** 这 10 个词已确认不在 sensitive-word 0.29.5 的默认词库里，只能靠 DB 生效 */
    private static final List<String> DB_ONLY_WORDS = List.of(
            "职业打假", "恶意退款", "刷好评", "山寨货", "高仿",
            "敲诈勒索", "差评师", "恶意差评", "黑产", "薅羊毛");

    @Autowired
    private SensitiveWordBs sensitiveWordBs;

    @Autowired
    private SensitiveWordMapper sensitiveWordMapper;

    @Autowired
    private SensitiveWordService sensitiveWordService;

    @Test
    void dbDenyWordsAreLoadedIntoDictionary() {
        long denyCount = sensitiveWordMapper
                .selectList(new QueryWrapper<SensitiveWord>().eq("type", "deny")).size();
        assertTrue(denyCount >= DB_ONLY_WORDS.size(),
                "bz_sensitive_word 里 deny 词只有 " + denyCount + " 条，先执行 insert-sensitive-words.sql");

        for (String word : DB_ONLY_WORDS) {
            assertTrue(sensitiveWordBs.contains("请问" + word + "这个说法对吗"),
                    "数据库黑名单词没有被加载进词库：" + word);
        }
    }

    @Test
    void brandWordsAreNotBlocked() {
        // 缺陷 12 修复后不再加载 houbb 内置词库（其中有 "3p"，会把 iphone13pro 误杀），
        // 这里验证真实 Redis/MySQL 词表环境下手机型号可以正常搜索
        assertFalse(sensitiveWordBs.contains("帮我找一下 iphone13pro 手机，多少钱？"));
        assertFalse(sensitiveWordBs.contains("iphone14pro 还有货吗"));
        assertFalse(sensitiveWordBs.contains("iphone16pro 什么时候降价"));
        assertFalse(sensitiveWordBs.findAll("帮我找一下 iphone13pro 手机，多少钱？").contains("3p"),
                "iphone13pro 仍被当成违禁词 3p");
    }

    @Test
    void normalBusinessWordsAreNotBlocked() {
        // 正品 / 退货 是 allow 名单里的词，正常咨询不能被误杀
        assertFalse(sensitiveWordBs.contains("这个商品是正品吗"));
        assertFalse(sensitiveWordBs.contains("我想申请退货，怎么操作"));
        assertFalse(sensitiveWordBs.contains("订单什么时候发货"));
    }

    @Test
    void paginationActuallyWorks() {
        Page<SensitiveWord> page = sensitiveWordService.findSensitiveWords(1, 3, null, null);
        assertEquals(3, page.getRecords().size(), "分页没生效：一页 3 条却返回了 " + page.getRecords().size() + " 条");
        assertTrue(page.getTotal() >= DB_ONLY_WORDS.size(), "total 不对：" + page.getTotal());

        Page<SensitiveWord> page2 = sensitiveWordService.findSensitiveWords(2, 3, null, null);
        assertEquals(3, page2.getRecords().size());
        assertNotNull(page2.getRecords().get(0));
    }

    @Test
    void addAndDeleteRefreshDictionaryAtRuntime() {
        String word = "测试占位词甲";
        assertFalse(sensitiveWordBs.contains("这是" + word), "前提不成立：这个词本来就在词库里");

        SensitiveWord pojo = new SensitiveWord();
        pojo.setWord(word);
        pojo.setType("deny");
        sensitiveWordService.addSensitiveWord(pojo);
        assertNotNull(pojo.getId(), "插入后 MyBatis-Plus 没有回填自增主键");

        try {
            assertTrue(sensitiveWordBs.contains("这是" + word), "addSensitiveWord 没有热更新词库");
        } finally {
            sensitiveWordService.deleteSensitiveWord(pojo.getId());
        }
        assertFalse(sensitiveWordBs.contains("这是" + word), "deleteSensitiveWord 没有从词库里移除");
        assertNull(sensitiveWordMapper.selectById(pojo.getId()), "deleteSensitiveWord 没有删库");
    }

    @Test
    void allowWordCanWhitelistADenyWord() {
        // 直接操作 Bean，不写库，避免污染 bz_sensitive_word。
        // 缺陷 12 之后没有内置词库，这里必须挑一个「数据库 deny 表里确实有」的词（DB_ONLY_WORDS 之一）
        String denyWord = "职业打假";
        assertTrue(sensitiveWordBs.contains("这里有" + denyWord + "内容"), "前提不成立：" + denyWord + " 不在数据库黑名单里");
        sensitiveWordBs.addWordAllow(denyWord);
        try {
            assertFalse(sensitiveWordBs.contains("这里有" + denyWord + "内容"),
                    "白名单没有覆盖黑名单词");
        } finally {
            sensitiveWordBs.removeWordAllow(denyWord);
        }
        assertTrue(sensitiveWordBs.contains("这里有" + denyWord + "内容"), "白名单移除后黑名单没恢复");
    }
}