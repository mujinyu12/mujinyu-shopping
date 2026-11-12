package com.mujinyu.shopping_manager_api.security;

import com.alibaba.fastjson2.JSON;
import com.mujinyu.shopping_common.result.BaseResult;
import com.mujinyu.shopping_common.result.CodeEnum;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;

// 登录成功处理器
public class MyLoginSuccessHandler implements AuthenticationSuccessHandler {
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        BaseResult<Object> result = new BaseResult<>(CodeEnum.SUCCESS.getCode(), "登录成功", null);
        response.setContentType("application/json;charset=utf-8");
        response.getWriter().write(JSON.toJSONString(result));
    }
}
