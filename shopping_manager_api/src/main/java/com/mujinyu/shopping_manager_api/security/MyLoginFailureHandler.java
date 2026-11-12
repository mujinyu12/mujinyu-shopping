package com.mujinyu.shopping_manager_api.security;

import com.alibaba.fastjson2.JSON;
import com.mujinyu.shopping_common.result.BaseResult;
import com.mujinyu.shopping_common.result.CodeEnum;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import java.io.IOException;

// 登录失败处理器
public class MyLoginFailureHandler implements AuthenticationFailureHandler {
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        BaseResult<Object> result = new BaseResult<>(CodeEnum.ACCOUNT_OR_PASSWORD_ERROR.getCode(), CodeEnum.ACCOUNT_OR_PASSWORD_ERROR.getMessage(), null);
        response.setContentType("application/json;charset=utf-8");
        response.getWriter().write(JSON.toJSONString(result));

    }
}
