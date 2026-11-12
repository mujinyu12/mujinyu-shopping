package com.mujinyu.shopping_common.util;

import org.apache.commons.codec.digest.DigestUtils;

/**
 * MD5 加盐工具：用户密码入库前必须加密，不能存明文
 */
public class Md5Util {

    /** 盐值：换掉这个值会让历史密码全部失效 */
    public final static String MD5_KEY = "MUJINYU";

    /**
     * 加密
     *
     * @param text 明文
     * @return 密文
     */
    public static String encode(String text) {
        return DigestUtils.md5Hex(text + MD5_KEY);
    }

    /**
     * 校验明文与密文是否匹配
     *
     * @param text   明文
     * @param cipher 密文
     * @return true/false
     */
    public static boolean verify(String text, String cipher) {
        String md5Text = encode(text);
        return md5Text.equalsIgnoreCase(cipher);
    }
}
