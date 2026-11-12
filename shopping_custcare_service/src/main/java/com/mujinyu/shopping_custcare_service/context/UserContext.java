package com.mujinyu.shopping_custcare_service.context;

/**
 * 当前登录用户上下文（线程内传递）。
 * 为什么需要它：Function Calling 的工具参数是模型自己生成的，如果把 userId 暴露成工具入参，
 * 模型抄错一个数字就会查到别人的订单（越权）。所以改成：
 * {@code AICustCareServiceImpl} 在入口把认证后的 userId 放进 ThreadLocal；
 * 工具 {@code OrderQueryService} 从 ThreadLocal 里取，模型根本没有机会指定用户。
 * 注意必须成对调用 {@link #clear()}，Dubbo/Web 线程是复用的，不清会把上一个用户带进下一个请求。
 */
public final class UserContext {

    private static final ThreadLocal<Long> CURRENT_USER_ID = new ThreadLocal<>(); // 当前登录用户 ID

    private UserContext() {
    }

    public static void setUserId(Long userId) {
        CURRENT_USER_ID.set(userId);
    }

    public static Long getUserId() {
        return CURRENT_USER_ID.get();
    }

    public static void clear() {
        CURRENT_USER_ID.remove();
    }
}