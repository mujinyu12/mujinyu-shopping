package com.mujinyu.shopping_webhooks.controller;

import com.mujinyu.shopping_common.result.BusException;
import com.mujinyu.shopping_common.result.CodeEnum;
import com.mujinyu.shopping_common.service.MailService;
import com.mujinyu.shopping_webhooks.dto.AlarmMessageDto;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * 告警控制器
 */
@RestController
@Slf4j
public class WebHooksController {

    /** 单次回调最多接受的告警条数，防止一个请求塞爆邮件正文/线程池 */
    private static final int MAX_MESSAGES = 50;

    /** 邮件正文长度上限（字符），超出部分截断 */
    private static final int MAX_BODY = 8 * 1024;

    private static final String ALARM_SUBJECT = "shopping项目的SkyWalking告警信息";

    @DubboReference
    private MailService mailService;

    /** 告警邮件异步发送线程池，见 AlarmMailConfig */
    @Resource(name = "alarmMailExecutor")
    private Executor alarmMailExecutor;

    /** 与 SkyWalking 侧约定的共享 token；未配置时拒绝所有请求 */
    @Value("${webhooks.alarm-token:}")
    private String alarmToken;

    /** 告警收件人，多个用逗号分隔；未配置时只记录日志不发信 */
    @Value("${webhooks.alarm-to:}")
    private String alarmTo;

    /**
     * SkyWalking告警的回调方法，也就是该接口是暴露给SkyWalking的，用于接收SkyWalking推送的告警信息
     * 我们接收到SkyWalking传来的报警信息，也可以处理告警信息
     *
     * @param token               共享 token，由网关注入或监控系统直接携带
     * @param alarmMessageDtoList 告警信息列表
     */
    @PostMapping("/alarm")
    public void alarm(@RequestHeader(value = "X-Alarm-Token", required = false) String token,
                      @RequestBody(required = false) List<AlarmMessageDto> alarmMessageDtoList) {
        // 接口暴露在公网时，没有 token 一律拒绝；token 为空说明服务端未配置，同样拒绝
        if (!isTokenValid(token)) {
            throw new BusException(CodeEnum.PERMISSION_DENIED);
        }
        if (alarmMessageDtoList == null || alarmMessageDtoList.isEmpty()
                || alarmMessageDtoList.size() > MAX_MESSAGES) {
            throw new BusException(CodeEnum.PARAMETER_ERROR);
        }

        StringBuilder builder = new StringBuilder(1024);
        for (AlarmMessageDto info : alarmMessageDtoList) {
            if (info == null) {
                continue;
            }
            builder.append("\nscopeId:").append(info.getScopeId())
                    .append("\nScope实体:").append(info.getScope())
                    .append("\n告警消息:").append(info.getAlarmMessage())
                    .append("\n告警规则:").append(info.getRuleName())
                    .append("\n\n------------------------\n\n");
            // 正文达到上限就截断，避免超长告警撑爆邮件体
            if (builder.length() >= MAX_BODY) {
                builder.setLength(MAX_BODY - 12);
                builder.append("\n...(已截断)");
                break;
            }
        }

        if (alarmTo == null || alarmTo.isBlank()) {
            // 未配置收件人时不发信，但保留日志便于排查；不能回退到硬编码邮箱
            log.warn("未配置 webhooks.alarm-to，告警邮件未发送：{}", builder);
            return;
        }

        String content = builder.toString();
        for (String to : alarmTo.split(",")) {
            String recipient = to.trim();
            if (recipient.isEmpty()) {
                continue;
            }
            // 异步发送：SMTP 可能耗时数秒，不能阻塞 SkyWalking 的回调请求
            alarmMailExecutor.execute(() -> {
                try {
                    mailService.sendMail(recipient, content, ALARM_SUBJECT);
                } catch (Exception e) {
                    log.error("告警邮件发送失败，收件人={}", recipient, e);
                }
            });
        }
    }

    /**
     * 常量时间比较 token，避免逐字符比较带来的时序侧信道
     */
    private boolean isTokenValid(String token) {
        // 配置为空视为“未启用鉴权配置”，直接拒绝所有请求，避免忘记配置时接口裸奔
        String expected = alarmToken == null ? "" : alarmToken.trim();
        if (expected.isEmpty() || token == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                token.trim().getBytes(StandardCharsets.UTF_8));
    }
}