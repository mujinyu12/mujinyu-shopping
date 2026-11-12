package com.mujinyu.shopping_manager_api.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// 统一异常处理器
@RestControllerAdvice
public class AccessDeniedExceptionHandler {
    // 处理权限不足异常时，捕获到异常再次抛出，交给AccessDeniedHandler处理
    @ExceptionHandler (AccessDeniedException.class)
    public void handleAccessDeniedException(AccessDeniedException e) {
        throw e;
    }
}
