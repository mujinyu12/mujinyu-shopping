package com.mujinyu.shopping_manager_api.security;

import com.alibaba.fastjson2.JSON;
import com.mujinyu.shopping_common.result.BaseResult;
import com.mujinyu.shopping_common.result.CodeEnum;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

// 未登录处理器
public class MyAuthenticationEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        BaseResult<Object> result = new BaseResult<>(CodeEnum.NOT_LOGIN.getCode(), CodeEnum.NOT_LOGIN.getMessage(), null);
        // HTTP 状态码与业务码保持一致，前端和监控才能正确识别“未登录”
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=utf-8");
        response.getWriter().write(JSON.toJSONString(result));
    }
}
