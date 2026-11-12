package com.mujinyu.shopping_common.result;

import com.alibaba.csp.sentinel.adapter.spring.webmvc_v6x.callback.BlockExceptionHandler;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Sentinel 限流处理器：把拦截结果写成统一的 BaseResult
 * 统一由 CommonAutoConfiguration 注册成 Bean
 */
@Slf4j
public class MyBlockExceptionHandler implements BlockExceptionHandler {

    private final ObjectMapper objectMapper;

    public MyBlockExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse,
                       String resourceName, BlockException e) throws Exception {
        log.warn("请求被 Sentinel 拦截: resource={}, 异常={}", resourceName, e.getClass().getSimpleName());
        BaseResult<Void> result;
        if (e instanceof FlowException) {
            result = new BaseResult<>(CodeEnum.FLOW_LIMIT.getCode(), CodeEnum.FLOW_LIMIT.getMessage(), null);
        } else {
            // 降级、热点参数、授权、系统保护等类型统一兜底；不写响应体会得到一个 200 的空包，前端无法判断
            result = new BaseResult<>(CodeEnum.BLOCK_ERROR.getCode(), CodeEnum.BLOCK_ERROR.getMessage(), null);
        }
        httpServletResponse.setContentType("application/json;charset=utf-8");
        httpServletResponse.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
