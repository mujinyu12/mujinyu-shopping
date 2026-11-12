package com.mujinyu.shopping_manager_api.security;

import com.alibaba.fastjson2.JSON;
import com.mujinyu.shopping_common.result.BaseResult;
import com.mujinyu.shopping_common.result.CodeEnum;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

// 权限不足处理器
public class MyAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {
        BaseResult<Object> result = new BaseResult<>(CodeEnum.PERMISSION_DENIED.getCode(), CodeEnum.PERMISSION_DENIED.getMessage(), null);
        // 权限不足返回真实 403，避免无权限请求被误判为成功
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=utf-8");
        response.getWriter().write(JSON.toJSONString(result));
    }
}
