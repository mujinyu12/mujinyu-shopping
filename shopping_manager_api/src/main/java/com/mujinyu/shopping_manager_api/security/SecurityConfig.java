package com.mujinyu.shopping_manager_api.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

// Security配置类
@Configuration
// 开启鉴权配置注解
@EnableMethodSecurity
public class SecurityConfig {
    // Spring Security配置
    @Bean
    protected SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // 自定义表单登录
        http.formLogin(
           form -> {
               form.usernameParameter("username")  // 用户名项
                       .passwordParameter("password")  // 密码项
                       .loginProcessingUrl("/admin/login") // 登录提交项
                       .successHandler(new MyLoginSuccessHandler()) // 登录成功处理器
                       .failureHandler(new MyLoginFailureHandler()); // 登录失败处理器
           }
        );

        // 权限拦截配置
        http.authorizeHttpRequests(
            resp -> {
                resp.requestMatchers("/login", "/admin/login").permitAll(); // 登录请求不需要认证

                // 管理员管理接口按权限点精确授权，避免“登录即拥有全部权限”
                // 权限串与 bz_permission.url 一致，方法上的 @PreAuthorize 再做一次兜底校验
                resp.requestMatchers(HttpMethod.POST, "/admin/add").hasAnyAuthority("/admin/add");
                resp.requestMatchers(HttpMethod.PUT, "/admin/update").hasAnyAuthority("/admin/update");
                resp.requestMatchers(HttpMethod.PUT, "/admin/updateRoleToAdmin").hasAnyAuthority("/admin/updateRoleToAdmin");
                resp.requestMatchers(HttpMethod.DELETE, "/admin/delete").hasAnyAuthority("/admin/delete");
                resp.requestMatchers(HttpMethod.GET, "/admin/findById").hasAnyAuthority("/admin/findById");
                resp.requestMatchers(HttpMethod.GET, "/admin/search").hasAnyAuthority("/admin/search");

                // 其余接口（品牌/分类/商品等）只要求登录：它们并未统一挂在 /admin/** 下，
                // 若使用 anyRequest().denyAll() 会把整个后台误伤为 403
                resp.anyRequest().authenticated(); // 其他请求都需要认证
            }
        );

        // 退出登录配置
        http.logout(
                logout -> {
                    logout.logoutUrl("/admin/logout") // 退出登录请求
                            .logoutSuccessHandler(new MyLogoutSuccessHandler()) // 退出登录成功处理器
                            .clearAuthentication(true) // 清除认证信息
                            .invalidateHttpSession(true); // 清除session
                }
        );

        // 异常处理
        http.exceptionHandling(
                exception -> {
                    exception.authenticationEntryPoint(new MyAuthenticationEntryPoint()) // 未登录处理器
                            .accessDeniedHandler(new MyAccessDeniedHandler()); // 权限不足处理器
                }
        );

        // 跨域访问：无参的 cors() 自 6.1 起已弃用，改用 Customizer 形式启用默认配置
        // 默认配置会交给 CorsFilter 使用容器中的 CorsConfigurationSource（或 MVC 的 CORS 配置）
        http.cors(Customizer.withDefaults());

        // 关闭csrf防护
        // 说明：项目前端为独立工程且当前按“JSON + Cookie 会话”调用，未实现读取 XSRF-TOKEN
        // 并回填 X-XSRF-TOKEN 的逻辑；若在此开启 CSRF，所有 POST/PUT/DELETE 都会 403。
        // 因此暂时保留 disable，改由 SameSite Cookie + 权限点校验降低风险，待前端适配后再开启。
        http.csrf(csrf -> csrf.disable());

        return http.build();
    }

    // 密码加密器
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
