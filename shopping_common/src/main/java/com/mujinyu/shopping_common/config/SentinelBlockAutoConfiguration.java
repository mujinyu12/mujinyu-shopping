package com.mujinyu.shopping_common.config;

import com.alibaba.csp.sentinel.adapter.spring.webmvc_v6x.callback.BlockExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mujinyu.shopping_common.result.MyBlockExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * Sentinel 相关自动配置，单独成一个类。
 *
 * <p>为什么不能写在 {@link CommonAutoConfiguration} 里：
 * <ul>
 *     <li>Spring Boot 处理自动配置时要<b>内省配置类的所有方法</b>，只要某个 @Bean 方法的
 *         签名里出现 Sentinel 的类（BlockExceptionHandler），就会触发类加载；</li>
 *     <li>类加载失败抛 NoClassDefFoundError → 整个自动配置类报废 →
 *         ApplicationContext 启动失败，报的却是“Error processing condition on ... globalExceptionHandler”，
 *         看起来完全不像“缺 jar”。</li>
 * </ul>
 * 放到独立类 + {@code @ConditionalOnClass} 之后：没有 Sentinel 的模块连这个类都不会被装配，
 * 有 Sentinel 的模块（秒杀两个服务）照常注册限流处理器。
 */
@AutoConfiguration(after = CommonAutoConfiguration.class)
// 用字符串写类名：即使该类不存在也只是条件不成立，不会抛 ClassNotFoundException
@ConditionalOnClass(name = "com.alibaba.csp.sentinel.adapter.spring.webmvc_v6x.callback.BlockExceptionHandler")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SentinelBlockAutoConfiguration {

    /**
     * Sentinel 限流处理器：缺了它 Sentinel 会退回默认实现，前端拿不到统一的业务码。
     * 业务模块若自定义了 BlockExceptionHandler，以业务模块的为准（Sentinel 只注入一个）。
     */
    @Bean
    @ConditionalOnMissingBean(BlockExceptionHandler.class)
    public MyBlockExceptionHandler myBlockExceptionHandler(ObjectMapper objectMapper) {
        return new MyBlockExceptionHandler(objectMapper);
    }
}