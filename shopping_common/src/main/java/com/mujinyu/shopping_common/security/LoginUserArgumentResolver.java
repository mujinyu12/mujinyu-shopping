package com.mujinyu.shopping_common.security;

import com.mujinyu.shopping_common.result.BusException;
import com.mujinyu.shopping_common.result.CodeEnum;
import com.mujinyu.shopping_common.util.TokenUtils;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * @LoginUser 参数解析器
 * 从请求头 Authorization: Bearer xxx 中取出令牌，用公钥验签后把令牌里的 userId 注入到 Long 参数上；
 * 令牌缺失或非法时抛业务异常（401 用户未登录），由全局异常处理器统一返回。
 */
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    // 前端携带令牌的请求头
    private static final String AUTHORIZATION = "Authorization";
    // 令牌前缀
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        // 只处理标注了 @LoginUser 且类型为 Long 的参数
        return parameter.hasParameterAnnotation(LoginUser.class)
                && Long.class.equals(parameter.getParameterType());
    }

    @Override
    public Long resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String authorization = webRequest.getHeader(AUTHORIZATION);
        // 部分客户端会把 Bearer 写成小写，这里忽略大小写比较
        if (authorization == null
                || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            throw new BusException(CodeEnum.NOT_LOGIN);
        }
        Long userId = TokenUtils.getUserId(authorization.substring(BEARER_PREFIX.length()).trim());
        if (userId == null) {
            throw new BusException(CodeEnum.NOT_LOGIN);
        }
        return userId;
    }
}
