package com.mujinyu.shopping_common.service;

import com.mujinyu.shopping_common.result.BaseResult;

/**
 * 消息服务
 * <p>
 * 课程原版的设计：验证码由<b>调用方</b>生成（RandomUtil.buildCheckCode），
 * 短信服务只负责把传进来的验证码发出去，不生成、不存储、不校验。
 * 验证码的存储和校验由 shopping_user_service 基于 Redis 完成。
 */
public interface MessageService {

    /**
     * 发送验证码短信
     *
     * @param phone     手机号
     * @param checkCode 调用方生成好的验证码
     * @return 发送结果，code=200 表示发送成功
     */
    BaseResult sendMessage(String phone, String checkCode);
}
