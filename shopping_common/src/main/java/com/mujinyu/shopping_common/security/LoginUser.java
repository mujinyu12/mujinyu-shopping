package com.mujinyu.shopping_common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记控制器方法上需要登录用户 id 的参数
 * 由 LoginUserArgumentResolver 从请求头 Authorization 的令牌中解析后注入，
 * 业务接口不需要再要求前端单独传 userId 请求头。
 * <p>
 * 用法：public BaseResult<Xxx> foo(@LoginUser Long userId)
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LoginUser {
}
