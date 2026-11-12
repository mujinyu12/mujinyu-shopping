package com.mujinyu.shopping_pay_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信支付参数
 * <p>
 * 值全部写在 Nacos 的 shopping_pay_service-dev.yaml 里，前缀 wxpay。
 * 用 @Component + @ConfigurationProperties 而不是在配置类里写 @Bean，
 * 避免 Bean 创建早于属性绑定导致参数为 null。
 */
@Data
@Component
@ConfigurationProperties(prefix = "wxpay")
public class WxPayProperties {

    /** 商户号 */
    private String mchId;

    /** 商户 API 证书序列号 */
    private String mchSerialNo;

    /** 商户 API 私钥文件（apiclient_key.pem），必须是绝对路径，指向仓库外的文件 */
    private String privateKeyPath;

    /** APIv3 密钥，用于回调解密 */
    private String apiV3Key;

    /** 公众号/小程序/APP 的 appid */
    private String appid;

    /** 微信支付服务地址 */
    private String domain = "https://api.mch.weixin.qq.com";

    /** 接收结果通知的域名（必须是公网可访问的地址） */
    private String notifyDomain;
}
