package com.mujinyu.shopping_pay_service.config;

import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.http.DefaultHttpClientBuilder;
import com.wechat.pay.java.core.http.HttpClient;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.util.PemUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivateKey;

/**
 * 微信支付配置
 * 用「平台证书自动更新」模式，SDK 会自己下载并定时刷新平台证书，不用手工维护 platform_cert.pem。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WxPayConfig {

    private final WxPayProperties properties;

    /**
     * 商户 API 私钥
     * 先按文件系统路径找，找不到再去 classpath 找（默认放在 resources/apiclient_key.pem）。
     */
    @Bean
    public PrivateKey wxPayPrivateKey() {
        String path = properties.getPrivateKeyPath();
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("未配置 wxpay.private-key-path");
        }
        // 只允许绝对路径：classpath兜底会让密钥重新被打进jar，所以必须是绝对路径
        Path file = Paths.get(path);
        if (!file.isAbsolute()) {
            throw new IllegalStateException("wxpay.private-key-path 必须是绝对路径，当前：" + path);
        }
        if (!Files.isReadable(file)) {
            throw new IllegalStateException("微信支付私钥不可读：" + path);
        }
        try {
            String pem = Files.readString(file, StandardCharsets.UTF_8);
            if (!pem.contains("BEGIN PRIVATE KEY")) {
                throw new IllegalStateException("不是 PKCS#8 格式的私钥：" + path);
            }
            log.info("加载微信支付私钥：{}", file);
            return PemUtil.loadPrivateKeyFromString(pem);
        } catch (Exception e) {
            throw new IllegalStateException("微信支付私钥文件加载失败：" + path + "，请检查 wxpay.private-key-path", e);
        }
    }

    /**
     * 自动更新平台证书的配置（签名、验签、解密都靠它）
     */
    @Bean
    public RSAAutoCertificateConfig rsaAutoCertificateConfig(PrivateKey wxPayPrivateKey) {
        return new RSAAutoCertificateConfig.Builder()
                .merchantId(properties.getMchId())
                .privateKey(wxPayPrivateKey)
                .merchantSerialNumber(properties.getMchSerialNo())
                .apiV3Key(properties.getApiV3Key())
                .build();
    }

    /**
     * 带签名、验签能力的 HttpClient
     */
    @Bean
    public HttpClient wxPayHttpClient(RSAAutoCertificateConfig rsaAutoCertificateConfig) {
        return new DefaultHttpClientBuilder()
                .config(rsaAutoCertificateConfig)
                .build();
    }

    /**
     * 回调通知解析器：验签 + 解密
     */
    @Bean
    public NotificationParser notificationParser(RSAAutoCertificateConfig rsaAutoCertificateConfig) {
        return new NotificationParser(rsaAutoCertificateConfig);
    }

    /**
     * 退款服务。与NativePayService共用同一套商户配置（私钥+证书）
     */
    @Bean
    public RefundService refundService(RSAAutoCertificateConfig rsaAutoCertificateConfig) {
        return new RefundService.Builder()
                .config(rsaAutoCertificateConfig)
                .build();
    }
}
