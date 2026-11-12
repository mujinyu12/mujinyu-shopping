package com.mujinyu.shopping_common.service;

/**
 * AI客服服务接口
 */
public interface AICustCareService {
    /**
     * 根据用户消息生成回复
     * @param userMessage 用户消息
     * @return 回复消息
     */
    String generateResponse(String userMessage, Long userId);
}
