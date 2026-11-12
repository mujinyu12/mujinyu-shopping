package com.mujinyu.shopping_common.result;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * <p>统一返回 {@link ResponseEntity}，让 HTTP 状态码与业务码保持一致：
 * 以前所有异常都返回 HTTP 200，网关、前端拦截器和监控都无法区分成功与失败。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final Environment environment;

    public GlobalExceptionHandler(Environment environment) {
        this.environment = environment;
    }

    // 处理业务异常
    @ExceptionHandler(BusException.class)
    public ResponseEntity<BaseResult<Void>> busExceptionHandler(BusException e) {
        HttpStatus status = toHttpStatus(e.getCode());
        return ResponseEntity.status(status)
                .body(new BaseResult<>(e.getCode(), e.getMessage(), null));
    }

    /**
     * 处理"接口不存在"
     * Spring Boot 3.2 起，请求匹配不到任何映射时抛的是 NoResourceFoundException，
     * 不单独处理会被下面的 Exception 处理器兜底成 500，排查时非常误导
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<BaseResult<Void>> noResourceFoundExceptionHandler(NoResourceFoundException e) {
        log.warn("接口不存在: {} {}", e.getHttpMethod(), e.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new BaseResult<>(CodeEnum.NOT_FOUND.getCode(),
                        CodeEnum.NOT_FOUND.getMessage() + ": " + e.getHttpMethod() + " /" + e.getResourcePath(), null));
    }

    /**
     * 处理"请求体缺失或格式错误"
     * DELETE/GET 这类没有请求体的请求，若参数上误加了 @RequestBody 就会抛此异常，
     * 不单独处理会被下面的 Exception 处理器兜底成 500，让人误以为是服务端故障
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<BaseResult<Void>> httpMessageNotReadableExceptionHandler(HttpMessageNotReadableException e) {
        log.warn("请求体缺失或格式错误: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new BaseResult<>(CodeEnum.PARAMETER_ERROR.getCode(),
                        CodeEnum.PARAMETER_ERROR.getMessage() + ": 请求体缺失或格式错误", null));
    }

    /**
     * 处理"缺少必填请求参数"
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<BaseResult<Void>> missingServletRequestParameterExceptionHandler(MissingServletRequestParameterException e) {
        log.warn("缺少请求参数: {}", e.getParameterName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new BaseResult<>(CodeEnum.PARAMETER_ERROR.getCode(),
                        CodeEnum.PARAMETER_ERROR.getMessage() + ": 缺少请求参数 " + e.getParameterName(), null));
    }

    /**
     * 处理"缺少必填请求头"
     * 比如 @RequestHeader 要求的头前端没带，不单独处理会被下面的 Exception 处理器兜底成 500
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<BaseResult<Void>> missingRequestHeaderExceptionHandler(MissingRequestHeaderException e) {
        log.warn("缺少请求头: {}", e.getHeaderName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new BaseResult<>(CodeEnum.PARAMETER_ERROR.getCode(),
                        CodeEnum.PARAMETER_ERROR.getMessage() + ": 缺少请求头 " + e.getHeaderName(), null));
    }

    /**
     * 处理"请求参数类型不匹配"
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<BaseResult<Void>> methodArgumentTypeMismatchExceptionHandler(MethodArgumentTypeMismatchException e) {
        log.warn("请求参数类型不匹配: {}={}", e.getName(), e.getValue());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new BaseResult<>(CodeEnum.PARAMETER_ERROR.getCode(),
                        CodeEnum.PARAMETER_ERROR.getMessage() + ": 参数 " + e.getName() + " 类型不匹配", null));
    }

    /**
     * 处理 @Valid / @Validated 入参校验失败（如注册接口的 DTO 校验）
     * 把各字段的提示拼成一句话返回，避免前端只看到 500
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResult<Void>> methodArgumentNotValidExceptionHandler(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        log.warn("参数校验失败: {}", detail);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new BaseResult<>(CodeEnum.PARAMETER_ERROR.getCode(),
                        CodeEnum.PARAMETER_ERROR.getMessage() + ": " + detail, null));
    }

    /**
     * 处理"上传文件超过限制"
     * Tomcat 在进入 Controller 之前就会因 multipart 大小超限抛此异常，
     * 不单独处理会变成 500，前端无法提示“文件过大”
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<BaseResult<Void>> maxUploadSizeExceededExceptionHandler(MaxUploadSizeExceededException e) {
        log.warn("上传文件超过大小限制: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new BaseResult<>(CodeEnum.PARAMETER_ERROR.getCode(),
                        "上传文件不能超过 5MB", null));
    }

    // 处理系统异常
    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResult<Void>> defaultExceptionHandler(Exception e) {
        log.error("系统异常", e);
        // 开发环境把真实原因带出去，方便定位；其它环境只返回统一提示，避免泄露内部信息
        String message = CodeEnum.SYSTEM_ERROR.getMessage();
        if (isDev()) {
            message = message + ": " + describe(e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new BaseResult<>(CodeEnum.SYSTEM_ERROR.getCode(), message, null));
    }

    /**
     * 业务码到 HTTP 状态码的映射。
     *
     * <p>只把“确定是客户端输入问题”的业务码映射成 4xx；
     * 文件上传/删除、邮件、支付、二维码这类失败可能来自下游存储或第三方，
     * 仍按 5xx 返回，避免监控把服务端故障统计成用户错误。</p>
     *
     * <p>402（账号或密码错误）保持 HTTP 200：登录页前端按“HTTP 200 + code”展示提示，
     * 改成 401/400 会走 axios 的 catch 分支导致提示丢失。</p>
     */
    private HttpStatus toHttpStatus(Integer code) {
        if (code == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return switch (code) {
            case 200 -> HttpStatus.OK;
            case 401 -> HttpStatus.UNAUTHORIZED;
            // 登录失败保持 200，见方法注释
            case 402 -> HttpStatus.OK;
            case 403, 620 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            // 601 参数异常、602/603 商品类型业务规则、605-611 注册登录校验、
            // 613 订单状态、616 回调验签、617 库存不足、618 订单超时
            case 601, 602, 603, 605, 606, 607, 608, 609, 610, 611, 613, 616, 617, 618 ->
                    HttpStatus.BAD_REQUEST;
            case 701 -> HttpStatus.TOO_MANY_REQUESTS;
            case 702 -> HttpStatus.SERVICE_UNAVAILABLE;
            // 604/612/614/615/619 等依赖下游资源，按 5xx 处理
            // 600-699 是业务校验类错误（含 621/622/623 退款相关），统一 400；
            // 其余 5xx 才是服务端错误
            default -> code >= 600 && code < 700 ? HttpStatus.BAD_REQUEST
                    : code >= 500 ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.OK;
        };
    }

    /**
     * 是否开发环境：必须显式激活 dev。
     * 不能再把“没有指定 profile”当成开发环境，否则生产环境漏配 profile 时会回传异常根因。
     */
    private boolean isDev() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> profile.equalsIgnoreCase("dev"));
    }

    /**
     * 描述异常根因：Dubbo 调用失败时外层是 RpcException，
     * 真正原因（比如 MyBatis 的 BindingException）在 cause 链的最里层
     */
    private String describe(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String detail = root.getClass().getSimpleName();
        if (root.getMessage() != null) {
            detail = detail + ": " + root.getMessage();
        }
        // Dubbo 会把远端的完整堆栈塞进异常 message 里，只保留开头，避免响应体过大
        return detail.length() > 300 ? detail.substring(0, 300) + "..." : detail;
    }
}