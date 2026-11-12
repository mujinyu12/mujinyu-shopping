package com.mujinyu.shopping_user_service.util;

import com.mujinyu.shopping_common.util.TokenUtils;
import lombok.SneakyThrows;
import org.jose4j.json.JsonUtil;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.util.Map;

/**
 * JWT 工具类：私钥签名放在本模块，验签统一使用 shopping_common 的 TokenUtils（只带公钥）。
 *
 * <p>私钥不再硬编码在源码里，按以下顺序读取（先命中先用）：</p>
 * <ol>
 *   <li>环境变量 / JVM 系统属性 {@code JWT_PRIVATE_JWK}：私钥 JWK 的 JSON 字符串；</li>
 *   <li>环境变量 / JVM 系统属性 {@code JWT_PRIVATE_JWK_FILE}：私钥 JWK 文件路径；</li>
 *   <li>默认文件 {@code ${user.home}/.shopping/jwt_private.jwk}（本地开发用，已被 .gitignore 排除）。</li>
 * </ol>
 * 三者都没有时启动即失败，避免"拿着空私钥还能启动、登录时才 500"。
 */
public class JwtUtils {

    /**
     * JWK 没带 kid 时的默认值，与历史令牌保持兼容
     */
    private static final String DEFAULT_KID = "keyId";

    /**
     * 私钥只解析一次，避免每次登录都做一次 JSON -> RSA 的转换
     */
    private static final RsaJsonWebKey PRIVATE_KEY = loadPrivateKey();

    private static final String KID = resolveKid(PRIVATE_KEY);

    private JwtUtils() {
    }

    /**
     * 加载RSA JSON Web Key私钥
     * 该方法会从多个可能的来源加载私钥，包括环境变量、系统属性或文件系统
     *
     * @return RsaJsonWebKey 包含私钥的JWK对象
     * @throws IllegalStateException 当无法加载或解析私钥时抛出
     */
    private static RsaJsonWebKey loadPrivateKey() {
        // 首先尝试从环境变量或系统属性中获取JWK JSON字符串
        String jwkJson = firstNonBlank(System.getenv("JWT_PRIVATE_JWK"), System.getProperty("JWT_PRIVATE_JWK"));
        if (jwkJson == null) {
            // 如果环境变量和系统属性都没有设置，则尝试从文件读取
            String path = firstNonBlank(System.getenv("JWT_PRIVATE_JWK_FILE"), System.getProperty("JWT_PRIVATE_JWK_FILE"));
            Path file = (path == null)
                    // 默认路径为用户主目录下的.shopping/jwt_private.jwk文件
                    ? Paths.get(System.getProperty("user.home"), ".shopping", "jwt_private.jwk")
                    : Paths.get(path);
            // 检查文件是否可读
            if (Files.isReadable(file)) {
                try {
                    // 读取文件内容并去除前后空白字符
                    jwkJson = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).trim();
                } catch (Exception e) {
                    throw new IllegalStateException("JWT 私钥文件读取失败：" + file, e);
                }
            }
        }
        // 检查是否成功获取到JWK JSON字符串
        if (jwkJson == null || jwkJson.isBlank()) {
            throw new IllegalStateException(
                    "缺少 JWT 私钥：请设置环境变量 JWT_PRIVATE_JWK（JWK JSON），"
                            + "或 JWT_PRIVATE_JWK_FILE（文件路径），"
                            + "或把私钥放到 " + Paths.get(System.getProperty("user.home"), ".shopping", "jwt_private.jwk"));
        }
        try {
            // 解析JWK JSON为RsaJsonWebKey对象
            RsaJsonWebKey jwk = new RsaJsonWebKey(JsonUtil.parseJson(jwkJson));
            // 验证私钥是否完整
            if (jwk.getPrivateKey() == null) {
                throw new IllegalStateException("JWT 私钥 JWK 不完整：缺少 d/p/q 等私有字段");
            }
            return jwk;
        } catch (IllegalStateException e) {
            // 直接抛出非法状态异常
            throw e;
        } catch (Exception e) {
            // 处理其他类型的异常，包装为非法状态异常
            throw new IllegalStateException("JWT 私钥 JWK 解析失败，请确认是合法的 RSA JWK JSON", e);
        }
    }

    private static String resolveKid(RsaJsonWebKey jwk) {
        String kid = jwk.getKeyId();
        return (kid == null || kid.isBlank()) ? DEFAULT_KID : kid;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    /**
     * 生成 token
     *
     * @param userId   用户id
     * @param username 用户名
     */
    @SneakyThrows
    public static String sign(Long userId, String username) {
        // 1、创建 jwtClaims：载荷部分
        JwtClaims claims = new JwtClaims();
        // 签发者
        claims.setIssuer("mujinyu");
        // 接收方
        claims.setAudience("audience");
        // 有效期：24 小时
        claims.setExpirationTimeMinutesInTheFuture(60 * 24);
        // 唯一标识
        claims.setGeneratedJwtId();
        // 签发时间
        claims.setIssuedAtToNow();
        // 生效时间：为了容忍服务器时钟偏差，提前 2 分钟生效
        claims.setNotBeforeMinutesInThePast(2);
        // 主题
        claims.setSubject("shopping");
        // 自定义声明
        claims.setClaim("userId", userId);
        claims.setClaim("username", username);

        // 2、签名
        JsonWebSignature jws = new JsonWebSignature();
        jws.setPayload(claims.toJson());
        // 3、jwt 使用私钥签署（私钥来自环境变量/文件，不落代码）
        PrivateKey privateKey = PRIVATE_KEY.getPrivateKey();
        jws.setKey(privateKey);
        // 4、设置 kid：JWK 里有就用 JWK 的，方便以后轮换密钥
        jws.setKeyIdHeaderValue(KID);
        // 5、设置签名算法
        jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
        // 6、生成 jwt
        return jws.getCompactSerialization();
    }

    /**
     * 解密 token，获取 token 中的信息；令牌非法/过期时抛 NOT_LOGIN（缺陷 44）
     */
    public static Map<String, Object> verify(String token) {
        // 验签逻辑与公钥都放公共模块，避免各服务重复维护一份
        return TokenUtils.verify(token);
    }
}