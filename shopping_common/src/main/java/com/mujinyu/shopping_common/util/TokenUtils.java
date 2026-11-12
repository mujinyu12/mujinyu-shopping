package com.mujinyu.shopping_common.util;

import com.mujinyu.shopping_common.result.BusException;
import com.mujinyu.shopping_common.result.CodeEnum;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jose4j.json.JsonUtil;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;

import java.security.PublicKey;
import java.util.Collections;
import java.util.Map;

/**
 * 令牌校验工具类
 * 只持有公钥：令牌由 shopping_user_service 用私钥签发，其它微服务拿公钥验签即可，
 * 这样私钥不必扩散到各个业务模块。
 *
 * <p>公钥优先取环境变量 / JVM 系统属性 {@code JWT_PUBLIC_JWK}（轮换密钥时只改这一处），
 * 取不到时回落到下面内置的公钥——公钥本身不是秘密，放代码里不影响安全。</p>
 */
@Slf4j
public class TokenUtils {

    // 公钥（与 shopping_user_service 签发令牌所用的私钥配对）
    public final static String PUBLIC_JSON = "{\"kty\":\"RSA\",\"kid\":\"shopping-1793859297\",\"n\":\"zpHcq8iCfqfnSR1JjjAWL_wqT6KLneXIMmBBOlG3YA0WrqwDSSBBh9IZoZ1Vf9meBhyfsFv5Nz23Lau6gWaeGFoYo4a2J7ELV43kHjKkkLOZFBGco0HsGCA2WPC75Zg4lRNioQYBsE7i53zR3ep9-RoO1kZtTKWEhzlD-EyHM54f9uviaBruOs3dSy7HjU9i746W-tLbFmM7ef0syRjaBhk3i9tkooRBhT5zPCWmjlsc64SNixBZVlHQFy4QJziwwUuwIDWm9hKrI_LTrKaUDr99uNL6iAsFcheK7qaDStZAQubIccAfuGuNHUgodWqMPhKcoMQ2RDDAgXbXeXJ5PQ\",\"e\":\"AQAB\"}";

    /** 公钥只解析一次；轮换密钥时改 JWT_PUBLIC_JWK 环境变量即可，无需重新编译 */
    private static final PublicKey PUBLIC_KEY = loadPublicKey();

    private static PublicKey loadPublicKey() {
        String jwkJson = System.getenv("JWT_PUBLIC_JWK");
        if (jwkJson == null || jwkJson.isBlank()) {
            jwkJson = System.getProperty("JWT_PUBLIC_JWK");
        }
        if (jwkJson == null || jwkJson.isBlank()) {
            jwkJson = PUBLIC_JSON;
        }
        try {
            return new RsaJsonWebKey(JsonUtil.parseJson(jwkJson)).getPublicKey();
        } catch (Exception e) {
            throw new IllegalStateException("JWT 公钥 JWK 解析失败，请检查 JWT_PUBLIC_JWK", e);
        }
    }

    /**
     * 解密 token，获取 token 中的信息；令牌非法/过期时直接抛出NOT_LOGIN
     */
    public static Map<String, Object> verify(String token) {
        try {
            JwtClaims jwtClaims = buildConsumer().processToClaims(token);
            return jwtClaims.getClaimsMap();
        } catch (Exception e) {
            log.warn("令牌校验失败：{}", e.getMessage());
            throw new BusException(CodeEnum.NOT_LOGIN);
        }
    }

    /**
     * 可选场景使用：令牌缺失/非法时不抛异常，返回空 Map（如“登录可选”的接口）
     */
    public static Map<String, Object> tryVerify(String token) {
        try {
            return verify(token);
        } catch (BusException e) {
            return Collections.emptyMap();
        }
    }

    @SneakyThrows
    private static JwtConsumer buildConsumer() {
        // 使用启动时解析好的公钥（支持 JWT_PUBLIC_JWK 环境变量轮换），不再重复解析 PUBLIC_JSON
        return new JwtConsumerBuilder()
                .setRequireExpirationTime()
                .setAllowedClockSkewInSeconds(30)
                .setRequireSubject()
                .setExpectedIssuer("mujinyu")
                .setExpectedAudience("audience")
                .setVerificationKey(PUBLIC_KEY)
                .setJwsAlgorithmConstraints(new AlgorithmConstraints(
                        AlgorithmConstraints.ConstraintType.WHITELIST, AlgorithmIdentifiers.RSA_USING_SHA256))
                .build();
    }

    /**
     * 获取令牌中携带的用户 id；令牌非法/过期时抛 NOT_LOGIN，claims 里没有 userId 时返回 null
     */
    public static Long getUserId(String token) {
        Object userId = verify(token).get("userId");
        // claims 中的数字可能是 Integer/Long，统一按字符串转成 Long，避免类型转换异常
        return userId == null ? null : Long.valueOf(userId.toString());
    }
}