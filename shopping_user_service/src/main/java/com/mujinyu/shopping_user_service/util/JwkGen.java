package com.mujinyu.shopping_user_service.util;

import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jwk.RsaJsonWebKey;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 生成 JWT 签名用的 RSA 密钥对（本地运维小工具，不参与业务逻辑）。
 *
 * <p>用法：在 shopping_user_service 目录下执行</p>
 * <pre>
 * mvn -o -q exec:java -Dexec.mainClass=com.mujinyu.shopping_user_service.util.JwkGen
 * </pre>
 * <p>私钥会写到 ${user.home}/.shopping/jwt_private.jwk（JwtUtils 默认从这个位置读），
 * 公钥打印到控制台，替换 shopping_common 里 TokenUtils.PUBLIC_JSON 即可。</p>
 */
public class JwkGen {

    /**
     * 主方法，用于生成RSA密钥对并保存为JSON Web Key (JWK)格式
     *
     * @param args 命令行参数（未使用）
     * @throws Exception 可能抛出的异常
     */
    public static void main(String[] args) throws Exception {
        // 生成一个2048位的RSA JWK密钥对
        RsaJsonWebKey jwk = RsaJwkGenerator.generateJwk(2048);
        // 设置密钥ID，使用当前时间戳作为唯一标识
        jwk.setKeyId("shopping-" + System.currentTimeMillis() / 1000);

        // 将JWK转换为JSON字符串，包含私钥信息
        String privateJson = jwk.toJson(JsonWebKey.OutputControlLevel.INCLUDE_PRIVATE);
        // 将JWK转换为JSON字符串，仅包含公钥信息
        String publicJson = jwk.toJson(JsonWebKey.OutputControlLevel.PUBLIC_ONLY);

        // 获取用户主目录并创建.shopping文件夹
        Path dir = Paths.get(System.getProperty("user.home"), ".shopping");
        Files.createDirectories(dir);
        // 定义私钥文件路径
        Path privateFile = dir.resolve("jwt_private.jwk");
        // 将私钥JSON写入文件
        Files.write(privateFile, privateJson.getBytes(StandardCharsets.UTF_8));

        // 输出私钥文件路径和公钥JSON内容
        System.out.println("PRIVATE_FILE=" + privateFile);
        System.out.println("PUBLIC_JWK=" + publicJson);
    }
}