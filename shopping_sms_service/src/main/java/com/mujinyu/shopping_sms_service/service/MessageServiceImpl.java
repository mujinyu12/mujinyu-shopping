package com.mujinyu.shopping_sms_service.service;

import com.mujinyu.shopping_common.result.BaseResult;
import com.mujinyu.shopping_common.result.CodeEnum;
import com.mujinyu.shopping_common.service.MessageService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;

import java.util.Arrays;

/**
 * 消息服务实现（课程原版结构）
 * <p>
 * 只做一件事：把调用方传进来的验证码发出去。
 * 验证码由 Controller 用 RandomUtil 生成，存到 shopping_user_service 的 Redis 里。
 * <p>
 * sms.mock=true：不调第三方接口，返回成功（验证码只在显式开启 sms.log-code 的 dev 环境落日志）。
 * sms.mock=false：走阿里云短信，未配置时直接返回失败（不要假装成功）。
 * <p>
 * 安全约定：默认关闭 mock，日志中的手机号统一脱敏；验证码明文只允许在 dev 且显式配置
 * sms.log-code=true 时输出，生产日志里不应出现 6 位验证码。
 */
@Slf4j
@DubboService
public class MessageServiceImpl implements MessageService {

    private final Environment environment;

    public MessageServiceImpl(Environment environment) {
        this.environment = environment;
    }

    /** 是否使用模拟通道：默认关闭，必须在 Nacos 里显式打开 */
    @Value("${sms.mock:false}")
    private boolean mock;

    /** 是否允许在日志中输出验证码：默认关闭，且只在 dev 环境生效 */
    @Value("${sms.log-code:false}")
    private boolean logCode;

    @Override
    public BaseResult sendMessage(String phone, String checkCode) {
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
            return new BaseResult<>(CodeEnum.PARAMETER_ERROR.getCode(), "手机号格式不正确", null);
        }
        if (checkCode == null || checkCode.isEmpty()) {
            return new BaseResult<>(CodeEnum.PARAMETER_ERROR.getCode(), "验证码不能为空", null);
        }

        if (mock) {
            // 手机号永远脱敏：只保留前 3 位和后 4 位
            String masked = maskPhone(phone);
            // 验证码明文只在 dev 且显式开启 log-code 时输出，避免测试/生产日志泄露
            if (logCode && isDev()) {
                log.info("【Mock 短信】手机号 {} 的验证码是 {}（5 分钟内有效）", masked, checkCode);
            } else {
                log.info("【Mock 短信】手机号 {} 已生成验证码（内容不落日志）", masked);
            }
            return BaseResult.ok();
        }

        // ↓↓↓ 接阿里云短信时把这段补全，其余代码不用动 ↓↓↓
        // DefaultProfile profile = DefaultProfile.getProfile("cn-hangzhou", accessKeyId, accessKeySecret);
        // IAcsClient client = new DefaultAcsClient(profile);
        // SendSmsRequest request = new SendSmsRequest();
        // request.setPhoneNumbers(phone);
        // request.setSignName(signName);
        // request.setTemplateCode(templateCode);
        // request.setTemplateParam("{\"code\":\"" + checkCode + "\"}");
        // client.getAcsResponse(request);
        log.error("短信真实通道尚未接入，phone={}。请在 Nacos 里开启 sms.mock，或先完成阿里云短信接入", maskPhone(phone));
        return new BaseResult<>(CodeEnum.SYSTEM_ERROR.getCode(), "短信发送失败：真实短信通道未接入", null);
    }

    /**
     * 手机号脱敏：138****8000
     */
    private String maskPhone(String phone) {
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    /**
     * 是否开发环境：必须显式激活 dev
     */
    private boolean isDev() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> profile.equalsIgnoreCase("dev"));
    }
}