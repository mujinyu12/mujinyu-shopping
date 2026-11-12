package com.mujinyu.shopping_common.config;

import com.mujinyu.shopping_common.result.GlobalExceptionHandler;
import com.mujinyu.shopping_common.security.LoginUserArgumentResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * shopping_common 的自动配置入口
 * 本模块的包名（com.mujinyu.shopping_common）不在各微服务的启动类扫描范围内，
 * 因此 @RestControllerAdvice 之类的组件不会自动生效。这里以自动配置的方式注册，
 * 并在 META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
 * 中登记本类，引用 common 的服务启动后即可使用。
 */
@AutoConfiguration
// 纯 Dubbo 提供者等非 Web 应用没有 DispatcherServlet，注册异常处理器毫无意义，直接跳过
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommonAutoConfiguration {

    /**
     * 全局异常处理器：业务模块若自定义了同类型 Bean，则以业务模块的为准
     */
    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler(Environment environment) {
        // Environment 用于判断当前是不是开发环境（决定是否把异常详情返回给前端）
        return new GlobalExceptionHandler(environment);
    }

    /**
     * 参数解析器：从令牌中解析出用户 id
     */
    @Bean
    @ConditionalOnMissingBean
    public LoginUserArgumentResolver loginUserArgumentResolver() {
        return new LoginUserArgumentResolver();
    }

    /**
     * 把 @LoginUser 解析器注册进 Spring MVC
     * 业务模块未开启 @EnableWebMvc 时 Boot 会保留默认配置，追加自定义解析器不影响其它参数的解析
     */
    @Bean
    public WebMvcConfigurer loginUserWebMvcConfigurer(LoginUserArgumentResolver loginUserArgumentResolver) {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(loginUserArgumentResolver);
            }
        };
    }
}
