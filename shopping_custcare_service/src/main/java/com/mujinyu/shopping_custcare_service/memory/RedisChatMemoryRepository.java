package com.mujinyu.shopping_custcare_service.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 基于 Redis 的会话记忆仓库
 * <p>
 * Spring AI 1.0.0 把会话记忆拆成两层：
 * <ul>
 *     <li>{@link ChatMemory}（MessageWindowChatMemory）负责「保留最近 N 条」；</li>
 *     <li>{@link ChatMemoryRepository}（本类）负责「消息存哪里」。</li>
 * </ul>
 * 这里把每个会话的 {@code List<Message>} 序列化成 JSON 存进 Redis，key 为
 * {@code custcare:memory:{conversationId}}，每次写入刷新 7 天 TTL；
 * 多实例部署时共享同一份记忆，重启不丢。
 * <p>
 * 为什么不用 Jackson 直接序列化 {@link Message}：它是 sealed 接口，实现类（UserMessage/
 * AssistantMessage…）带 media、metadata、ToolCall 等复杂结构，直接序列化会写出实现类字段，
 * 反序列化时又不知道该还原成哪个子类。所以这里用只含 type/text/toolCalls/toolResponses 的
 * 轻量 DTO，读的时候按 type 还原成对应的 Message 实现。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    /**
     * Redis key 前缀，完整 key：custcare:memory:990003（990003 即 conversationId，当前按 userId 隔离）
     */
    private static final String KEY_PREFIX = "custcare:memory:";

    /**
     * 会话 TTL：7 天不活跃自动过期，避免 Redis 里堆永远没人读的会话
     */
    private static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    /**
     * 根据会话ID生成完整的键名
     * 该方法用于将会话ID与特定前缀组合，形成完整的键名
     *
     * @param conversationId 会话的唯一标识符
     * @return 返回组合后的完整键名，格式为"前缀+会话ID"
     */
    private static String key(String conversationId) {
        return KEY_PREFIX + conversationId; // 将预设的前缀与传入的会话ID拼接并返回
    }

    /**
     * 列出所有会话 ID。
     * 注意：生产环境不要用 KEYS（大 key 空间会阻塞 Redis），这里只是为了满足接口，
     * 业务侧（MessageChatMemoryAdvisor）只会用 findByConversationId / saveAll / deleteByConversationId。
     */
    @Override
    public List<String> findConversationIds() {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*"); // 获取所有以"custcare:memory:"开头的键
        if (keys == null || keys.isEmpty()) {
            return new ArrayList<>(); // 如果键集合为空，返回空列表
        }
        return keys.stream()
                .filter(k -> k != null && k.length() > KEY_PREFIX.length()) // 过滤掉长度不足的键
                .map(k -> k.substring(KEY_PREFIX.length())) // 截取键值中"custcare:memory:"前缀部分
                .toList();
    }

    /**
     * 根据会话ID查找消息列表
     *
     * @param conversationId 会话ID，用于标识特定的会话
     * @return 返回该会话下的所有消息列表，如果会话ID无效或不存在消息，则返回空列表
     */
    @Override
    public List<Message> findByConversationId(String conversationId) {
        // 检查会话ID是否为空或空白字符串，如果是则直接返回空列表
        if (!StringUtils.hasText(conversationId)) {
            return new ArrayList<>();
        }
        // 从Redis中获取存储的消息JSON数据
        String json = redisTemplate.opsForValue().get(key(conversationId));
        // 检查获取的JSON数据是否为空或空白字符串，如果是则直接返回空列表
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            // 将JSON数据反序列化为StoredMessage对象列表
            List<StoredMessage> stored = objectMapper.readValue(json,
                    new TypeReference<List<StoredMessage>>() {
                    });
            // 创建Message列表，初始大小与stored列表相同，以优化性能
            List<Message> messages = new ArrayList<>(stored.size());
            // 遍历stored列表，将每个StoredMessage转换为Message对象
            for (StoredMessage item : stored) {
                Message message = toMessage(item);
                // 如果转换后的message不为null，则添加到messages列表中
                if (message != null) {
                    messages.add(message);
                }
            }
            return messages;
        } catch (Exception e) {
            // 反序列化失败（版本升级导致结构变化等）按空记忆处理：宁可少一轮上下文，也不能让客服接口 500
            log.warn("会话记忆反序列化失败，按空记忆处理。conversationId={}", conversationId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 保存指定会话的所有消息到Redis缓存中
     *
     * @param conversationId 会话ID，用于标识不同的会话
     * @param messages       需要保存的消息列表
     */
    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        // 检查会话ID和消息列表是否有效
        if (!StringUtils.hasText(conversationId) || messages == null) {
            return;
        }
        // 创建存储消息的列表，初始容量与输入消息列表相同
        List<StoredMessage> stored = new ArrayList<>(messages.size());
        // 遍历消息列表，将每个消息转换为存储格式
        for (Message message : messages) {
            if (message != null) {
                stored.add(StoredMessage.from(message));
            }
        }
        try {
            // 将消息列表序列化为JSON字符串并存储到Redis中，设置TTL（生存时间）
            redisTemplate.opsForValue().set(key(conversationId),
                    objectMapper.writeValueAsString(stored), TTL);
        } catch (Exception e) {
            // 异常处理：记录错误日志但不影响当前会话的继续
            // 写记忆失败不能让本轮回答失败：本轮照常返回，只是下一轮少了这段上下文
            log.error("会话记忆写入 Redis 失败，conversationId={}", conversationId, e);
        }
    }

    /**
     * 根据会话ID删除对应的缓存数据
     *
     * @param conversationId 会话ID，用于标识要删除的缓存数据
     */
    @Override
    public void deleteByConversationId(String conversationId) {
        // 检查会话ID是否为空或空白字符串
        if (!StringUtils.hasText(conversationId)) {
            return;
        }
        // 使用Redis模板删除对应会话ID的缓存数据
        // key方法用于生成完整的缓存键
        redisTemplate.delete(key(conversationId));
    }

    /**
     * 按 type 还原成 Spring AI 的 Message 实现
     * 该方法将存储的消息对象转换为Spring AI框架中的Message接口实现
     * 根据消息类型的不同，创建相应的消息实例
     */
    private Message toMessage(StoredMessage stored) {
        // 如果输入的存储消息为null，直接返回null
        if (stored == null) {
            return null;
        }
        // 处理消息文本，如果为null则设为空字符串
        String text = stored.text() == null ? "" : stored.text();
        // 处理消息类型，如果为null则默认为用户消息类型
        String type = stored.type() == null ? MessageType.USER.getValue() : stored.type();

        // 处理助手类型消息
        if (MessageType.ASSISTANT.getValue().equalsIgnoreCase(type)) {
            // 助手消息可能带 toolCalls，必须一起还原：否则下一轮模型看不到「上一轮调用过工具」，
            // 遇到工具调用链路时会报 tool_call_id 对不上
            List<AssistantMessage.ToolCall> toolCalls = stored.toolCalls() == null
                    ? List.of()
                    : stored.toolCalls().stream()
                    .map(call -> new AssistantMessage.ToolCall(
                            call.id(), call.type(), call.name(), call.arguments()))
                    .toList();
            if (toolCalls.isEmpty()) {
                return new AssistantMessage(text);
            }
            return new AssistantMessage(text, Map.of(), toolCalls);
        }
        if (MessageType.SYSTEM.getValue().equalsIgnoreCase(type)) {
            return new SystemMessage(text);
        }
        if (MessageType.TOOL.getValue().equalsIgnoreCase(type)) {
            List<ToolResponseMessage.ToolResponse> responses = stored.toolResponses() == null
                    ? List.of()
                    : stored.toolResponses().stream()
                    .map(resp -> new ToolResponseMessage.ToolResponse(
                            resp.id(), resp.name(), resp.responseData()))
                    .toList();
            if (responses.isEmpty()) {
                // 没有响应体的 tool 消息还原出来没有意义，直接丢掉
                log.debug("丢弃空的 tool 消息，conversationId 对应会话的历史有脏数据");
                return null;
            }
            return new ToolResponseMessage(responses);
        }
        // USER 以及无法识别的类型一律按用户消息处理（历史数据兼容）
        return new UserMessage(text);
    }

    /**
     * 落 Redis 的轻量消息结构。
     * <p>
     * toolCalls / toolResponses 只在对应类型上有值，序列化时写成 null，反序列化后按需还原。
     */
    public record StoredMessage(String type, String text,
                                List<ToolCallDto> toolCalls,
                                List<ToolResponseDto> toolResponses) {

        static StoredMessage from(Message message) {
            List<ToolCallDto> toolCalls = null;
            if (message instanceof AssistantMessage assistant && assistant.hasToolCalls()) {
                toolCalls = assistant.getToolCalls().stream()
                        .map(call -> new ToolCallDto(call.id(), call.type(), call.name(), call.arguments()))
                        .toList();
            }
            List<ToolResponseDto> toolResponses = null;
            if (message instanceof ToolResponseMessage toolResponse && !toolResponse.getResponses().isEmpty()) {
                toolResponses = toolResponse.getResponses().stream()
                        .map(resp -> new ToolResponseDto(resp.id(), resp.name(), resp.responseData()))
                        .toList();
            }
            return new StoredMessage(message.getMessageType().getValue(), textOf(message),
                    toolCalls, toolResponses);
        }

        /**
         * ToolResponseMessage 的 getText() 返回空串，这里统一兜一层 null，避免落库出 "null"
         */
        private static String textOf(Message message) {
            try {
                return message.getText() == null ? "" : message.getText();
            } catch (Exception e) {
                return "";
            }
        }
    }

    /**
     * 对应 {@link AssistantMessage.ToolCall}（record：id/type/name/arguments）
     */
    public record ToolCallDto(String id, String type, String name, String arguments) {
    }

    /**
     * 对应 {@link ToolResponseMessage.ToolResponse}（record：id/name/responseData）
     */
    public record ToolResponseDto(String id, String name, String responseData) {
    }
}