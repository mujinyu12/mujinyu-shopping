package com.mujinyu.shopping_common.util;


import java.security.SecureRandom;

public class RandomUtil {
    // 使用SecureRandom生成随机数，比Random更安全
    private static final SecureRandom RANDOM = new SecureRandom();

    public RandomUtil() {
    }

    /**
     * 生成指定位数的数字验证码
     *
     * @param digit 需要生成的验证码位数
     * @return 返回生成的数字验证码字符串
     */
    public static String buildCheckCode(int digit) {
        // 使用StringBuilder来构建验证码字符串
        StringBuilder sb = new StringBuilder(digit);
        // 循环digit次，每次生成一个0-9的随机数字并追加到StringBuilder中
        for (int i = 0; i < digit; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        // 将StringBuilder转换为字符串并返回
        return sb.toString();
    }
}
