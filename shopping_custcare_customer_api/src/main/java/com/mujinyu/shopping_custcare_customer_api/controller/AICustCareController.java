package com.mujinyu.shopping_custcare_customer_api.controller;

import com.mujinyu.shopping_common.result.BaseResult;
import com.mujinyu.shopping_common.security.LoginUser;
import com.mujinyu.shopping_common.service.AICustCareService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI客服控制器
 */
@RestController
@RequestMapping("/user/custcare")
public class AICustCareController {
    @DubboReference(timeout = 300000) // 指定超时时间
    private AICustCareService aiCustCareService;

    /**
     * 回答用户问题
     * @param message 问题
     * @return 答案
     */
    @GetMapping("/answer")
    public BaseResult<String> answer(@RequestParam("message") String message, @LoginUser Long userId) {
        String answer = aiCustCareService.generateResponse(message, userId);
        return BaseResult.ok(answer);
    }
}
