package com.mujinyu.shopping_common.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码工具：新密码一律BCrypt加密；旧密码兼容MD5
 */
public class PasswordUtil {

    // 强度：12，单词约 200 ~ 300ms，兼顾安全与登录体验
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(12);

    private PasswordUtil() {
    }

    public static String encode(String raw) {
        return ENCODER.encode(raw);
    }

    /**
     * 验证原始字符串与存储的字符串是否匹配
     * 根据存储字符串的格式选择不同的验证方式（BCrypt或MD5）
     *
     * @param raw    原始字符串，需要被验证的字符串
     * @param stored 存储的字符串，已加密的字符串
     * @return 如果匹配返回true，否则返回false；任一参数为null时返回false
     */
    public static boolean matches(String raw, String stored) {
        // 检查输入参数是否为null
        if (raw == null || stored == null) {
            return false;
        }
        // 判断存储的字符串是否为BCrypt格式
        if (isBCrypt(stored)) {
            // 使用BCrypt验证器进行验证
            return ENCODER.matches(raw, stored);
        } else {
            // 使用MD5验证器进行验证
            return Md5Util.verify(raw, stored);
        }
    }

    /**
     * 检查存储的密码是否需要升级
     *
     * @param stored 存储的密码字符串
     * @return 如果存储的密码不为null且不是BCrypt格式，则返回true表示需要升级；否则返回false
     */
    public static boolean needsUpgrade(String stored) {
        // 检查存储的密码是否不为null且不是BCrypt格式
        return stored != null && !isBCrypt(stored);
    }

    /**
     * 检查给定的存储值是否为BCrypt加密格式
     * BCrypt加密格式通常以$2a$、$2b$或$2y$开头
     *
     * @param stored 存储的加密字符串
     * @return 如果是BCrypt加密格式返回true，否则返回false
     */
    private static boolean isBCrypt(String stored) {
        return stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$");
    }
}
