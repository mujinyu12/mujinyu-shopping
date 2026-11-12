package com.mujinyu.shopping_custcare_service.service;

import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import com.mujinyu.shopping_common.pojo.Faq;
import com.mujinyu.shopping_common.service.AICustCareService;
import com.mujinyu.shopping_common.service.FaqService;
import com.mujinyu.shopping_custcare_service.context.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

/**
 * AI客服实现类
 * 回答策略（先便宜后贵）：
 * 1. 先走 FAQ 向量检索，命中且分数过阈值 → 直接返回标准答案（不花 token、答案可控）；
 * 2. 没命中 → 交给大模型，用系统提示词 + 用户问题生成回答；
 * 3. 检索异常或模型调用异常 → 返回兜底话术，绝不把 500 抛给前端。
 */
@DubboService
@RequiredArgsConstructor
@Slf4j
public class AICustCareServiceImpl implements AICustCareService {

    /**
     * 出现这些词就认为用户在问自己的订单，跳过 FAQ 直接走订单查询工具
     */
    private static final List<String> ORDER_QUERY_HINTS = List.of(
            "订单号", "我的订单", "待支付", "订单详情", "订单查询", "查订单", "查询订单", "订单状态", "我的待");

    /**
     * 记忆类问题的关键词：这类问题要结合上文才能回答（FAQ 里没有标准答案）
     */
    private static final List<String> MEMORY_QUERY_HINTS = List.of(
            "我叫", "我叫什么", "记住", "名字", "我喜欢", "颜色");

    /**
     * 商品/推荐类问题的关键词：要按实时库存和价格回答，FAQ 的发货/退货话术只会答非所问
     */
    private static final List<String> GOODS_QUERY_HINTS = List.of(
            "推荐", "买", "商品", "多少钱", "手机");

    private final ChatClient chatClient; // 大模型客户端

    private final FaqService faqService; // FAQ 服务

    private final SensitiveWordBs sensitiveWordBs; // 敏感词库

    /**
     * 客服人设提示词，存在 Nacos 的 custcare.system-prompt
     */
    @Value ("${custcare.system-prompt}")
    private String prompt;

    /**
     * 检索不到 FAQ、或模型调用失败时的兜底话术
     */
    @Value ("${custcare.rag.fallback-reply:抱歉，这个问题我暂时没有找到答案，已为您转接人工客服，请稍等。}")
    private String fallbackReply;

    /**
     * 生成 AI 回答
     *
     * @param userMessage 用户消息
     * @return FAQ 最佳答案
     */
    @Override
    public String generateResponse(String userMessage, Long userId) {
        // 把认证后的用户身份放进线程上下文：订单工具从里面取 userId，
        // 模型无法通过参数指定用户，也就无法越权查询别人的订单。
        UserContext.setUserId(userId);
        try {
            return doGenerateResponse(userMessage, userId);
        } finally {
            // Dubbo/Web 线程是复用的，必须清理，否则会把上一个用户带进下一个请求
            UserContext.clear();
        }
    }

    /**
     * 真正的回答逻辑（外层 {@link #generateResponse} 只负责管理用户上下文）
     */
    private String doGenerateResponse(String userMessage, Long userId) {
        // === 前置敏感词检查 ===
        String check = check(userMessage);
        if (check != null) {
            return check;
        }
        // ===== 第一步：FAQ 优先（订单 / 记忆 / 商品推荐类问题除外）=====
        // FAQ 只适合「政策、流程」类标准问答，下面三类必须先判意图、跳过 FAQ：
        // 1) 订单查询要的是「这个用户自己的数据」，FAQ 里那些「如何查看订单状态」之类的标准答案
        //    只会抢在工具调用之前把问题挡掉（实测问「我的待支付订单」直接回了一句「去我的订单页面查看」）；
        // 2) 记忆类问题（我叫什么 / 记住 / 我喜欢什么颜色）要看上文，FAQ 里没有这种标准答案；
        // 3) 商品推荐类问题（推荐 / 买 / 商品 / 多少钱 / 手机）要按实时库存和价格回答，
        //    实测「你们商城都卖些什么商品？」对「商品什么时候发货」相似度 0.6161，
        //    阈值再高也可能被相似问句误命中。
        boolean skipFaq = looksLikeOrderQuery(userMessage) || looksLikeMemoryOrGoodsQuery(userMessage);
        if (skipFaq) {
            log.info("意图命中订单/记忆/商品推荐类问题，跳过 FAQ，交给大模型（必要时走工具）。message={}", userMessage);
        } else {
            try {
                Faq bestAnswer = faqService.findBestAnswer(userMessage);
                // 命中后再复核一次意图：即使以后有人把向量检索挪到闸门之前，或 FaqService 换了实现，
                // 记忆类 / 商品推荐类问题也不允许被 FAQ 的标准答案抢答。
                if (bestAnswer != null && StringUtils.hasText(bestAnswer.getAnswer())
                        && !looksLikeMemoryOrGoodsQuery(userMessage)) {
                    log.info("命中 FAQ，直接返回标准答案。id={} question={}", bestAnswer.getId(), bestAnswer.getQuestion());
                    return bestAnswer.getAnswer();
                }
            } catch (Exception e) {
                // FAQ 检索坏了不能挡住 AI 兜底，降级继续
                log.warn("FAQ 检索异常，降级到大模型回答：{}", e.getMessage());
            }
        }

        // ===== 第二步：大模型回答 =====
        try {
            String orderToolPrompt = """
                    你是慕今予商城的AI客服小予，可以帮用户查询自己的订单。
                    用户可能会用自然语言提出查询请求，例如“帮我查一下我的待支付订单”“我的订单到哪了”。
                    遇到订单相关的问题，请优先调用工具函数 `getOrderQueryService`，不要在回复里让用户自己去订单页面看。
                    调用工具时注意：
                    - 不要传用户ID，系统已经绑定了当前登录用户，你无权指定别人；
                    - status 可以传订单状态（如「待支付」「已发货」），不传则查询该用户的全部订单；
                    - size 是查询条数，用户没说就传 1，最多 10 条；
                    - 工具返回空列表表示该用户没有符合条件的订单，请如实告知，不要编造订单。
                    如果工具报错（例如订单服务不可用），请用一句人话把原因转述给用户，不要暴露技术细节。
                    当前登录用户ID（仅用于你判断是否已登录，不要传给工具）：{user_id}
                    """;
            String goodsToolPrompt = """
                      你是一个专业的电商客服助手，专门处理订单查询和商品查询请求。请严格按照以下规则执行：
                      1. **意图识别**：
                      - 当用户提及"订单"、"我的订单"、"购买记录"、"待支付"、"已发货"、"最近订单"等关键词时，识别为订单查询意图
                      - 当用户提及"商品"、"查找"、"搜索"、"手机"、"衣服"、"价格"、"库存"等关键词时，识别为商品查询意图
                      2. **服务调用**：
                      - 订单查询意图 → 调用 `getOrderQueryService` 方法
                      - 商品查询意图 → 调用 `getGoodsQueryService` 方法
                      请确保准确识别用户意图并正确调用相应服务，提取所有可用参数填充到函数调用中，用户ID从prompt获取。
                      仅当用户明确要求其他操作或工具无法处理时，才直接回复。
                      当前用户的id: {user_id}
                    """;
            String resolvedPrompt = (prompt + "\n" + orderToolPrompt + "\n" + goodsToolPrompt)
                    .replace("{current_date}", LocalDate.now().toString()) // 替换提示词中的时间
                    .replace("{user_id}", userId == null ? "未登录" : userId.toString());

            // 会话ID：按用户隔离；未登录时落回默认会话，
            // 因为 MessageChatMemoryAdvisor 取上下文里的值时直接 .toString()，传 null 会 NPE
            String conversationId = userId == null ? ChatMemory.DEFAULT_CONVERSATION_ID : userId.toString();
            String content = chatClient.prompt()
                    .system(resolvedPrompt) // 系统提示词
                    .user(userMessage)  // 用户消息
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId)) // 使用用户ID作为会话ID
                    .call() // 调用大模型
                    .content(); // 模型生成的内容
            if (StringUtils.hasText(content)) {
                return content;
            }
            log.warn("大模型返回空内容，使用兜底话术");
            return fallbackReply;
        } catch (Exception e) {
            log.error("调用大模型失败", e);
            return fallbackReply;
        }
    }

    /**
     * 判断是不是「想查自己订单」这类问题。
     * <p>
     * 命中的问题要绕过 FAQ 去走大模型 + 订单工具，因为它要的是用户自己的数据，
     * 而 FAQ 里的「如何查看订单状态」只会把问题挡掉。
     * <p>
     * 只收窄到订单语境：像「商品如何退换货」「忘记密码怎么办」这类仍然走 FAQ。
     */
    private boolean looksLikeOrderQuery(String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
            return false;
        }
        for (String hint : ORDER_QUERY_HINTS) {
            if (userMessage.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是不是「记忆类」或「商品推荐类」问题。
     * <p>
     * 这两类问题都不该由 FAQ 回答：
     * <ul>
     *     <li>记忆类（「我叫什么名字」「请记住」「我最喜欢颜色」）答案在上文里，FAQ 没有标准答案，
     *         阈值低的时候「我叫林小雨，请记住。」实测 0.4633 会命中「忘记密码怎么办」；</li>
     *     <li>商品推荐类（「有什么推荐」「我想买点东西」「多少钱」「手机」）要按实时库存和价格回答，
     *         FAQ 只会把「商品什么时候发货」这类话术甩给用户。</li>
     * </ul>
     * 判断风格与 {@link #looksLikeOrderQuery(String)} 保持一致：命中任意关键词就跳过 FAQ。
     */
    private boolean looksLikeMemoryOrGoodsQuery(String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
            return false;
        }
        for (String hint : MEMORY_QUERY_HINTS) {
            if (userMessage.contains(hint)) {
                return true;
            }
        }
        for (String hint : GOODS_QUERY_HINTS) {
            if (userMessage.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查用户消息是否包含敏感词
     *
     * @param userMessage 用户消息
     * @return 如果包含敏感词，返回提示消息，否则返回null
     */
    private String check(String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
            return "请问有什么可以帮您？";
        }
        if (sensitiveWordBs.contains(userMessage)) {
            return "抱歉，您的问题包含敏感词，这个问题我暂时无法回答，让我们换个话题再聊吧！";
        }
        return null;
    }
}