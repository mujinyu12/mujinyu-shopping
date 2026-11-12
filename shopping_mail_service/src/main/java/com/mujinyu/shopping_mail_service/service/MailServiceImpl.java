package com.mujinyu.shopping_mail_service.service;

import com.mujinyu.shopping_common.result.BaseResult;
import com.mujinyu.shopping_common.result.BusException;
import com.mujinyu.shopping_common.result.CodeEnum;
import com.mujinyu.shopping_common.service.MailService;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Properties;

@Slf4j
@DubboService
@Service
public class MailServiceImpl implements MailService {

    @Value("${mail.user}")
    private String USER; // 发件人邮箱地址

    @Value("${mail.password}")
    private String PASSWORD; // 发件人邮箱密码

    // 以下三项都有默认值，Nacos 不配置也能按原来的 QQ 邮箱方式工作
    @Value("${mail.host:smtp.qq.com}")
    private String host;

    @Value("${mail.port:465}")
    private int port;

    @Value("${mail.timeout:5000}")
    private int timeout;

    @Override
    public BaseResult sendMail(String to, String text, String title) {
        if (to == null || to.isBlank()) {
            throw new BusException(CodeEnum.PARAMETER_ERROR.getCode(), "收件人邮箱不能为空");
        }
        try {
            // 程序化创建 JavaMailSender，不依赖 spring.mail.* 自动配置，
            // 这样 Nacos 里只需要保留原有的 mail.user / mail.password 即可
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(host);
            sender.setPort(port);
            sender.setUsername(USER);
            sender.setPassword(PASSWORD);
            sender.setDefaultEncoding("UTF-8");

            Properties props = sender.getJavaMailProperties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.auth", "true");
            // QQ 邮箱走 SSL，端口 465
            props.put("mail.smtp.ssl.enable", "true");
            // 安全修复：不再使用 MailSSLSocketFactory#setTrustAllHosts(true)，
            // 由 JVM 默认信任库校验服务端证书，避免被中间人劫持后仍“发送成功”
            // 连接/读取/写入都设置超时，防止 SMTP 卡死拖住调用线程
            props.put("mail.smtp.connectiontimeout", String.valueOf(timeout));
            props.put("mail.smtp.timeout", String.valueOf(timeout));
            props.put("mail.smtp.writetimeout", String.valueOf(timeout));

            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(USER);
            helper.setTo(to);
            helper.setSubject(title);
            // true 表示正文按 HTML 解析，保持原实现 text/html 的语义
            helper.setText(text, true);

            sender.send(message);
            return BaseResult.ok();
        } catch (Exception e) {
            log.error("邮件发送失败，收件人={}，标题={}", to, title, e);
            throw new BusException(CodeEnum.MAIL_SEND_ERROR);
        }
    }
}