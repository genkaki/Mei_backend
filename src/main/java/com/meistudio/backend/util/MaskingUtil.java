package com.meistudio.backend.util;

/**
 * 敏感数据脱敏工具类。
 *
 * 在日志记录、错误响应等场景中，自动将 API Key 等敏感信息进行掩码处理，
 * 防止因日志泄露导致的用户资产被盗刷。
 *
 * 示例：
 *   输入: "sk-28d25e803aa94d3891c2598db686fdd2"
 *   输出: "sk-28d2****fdd2"
 */
public class MaskingUtil {

    private MaskingUtil() {
        // 工具类禁止实例化
    }

    /**
     * 对 API Key 进行脱敏。
     * 保留前 6 位和后 4 位，中间用 **** 替代。
     *
     * @param apiKey 原始 API Key
     * @return 脱敏后的 API Key
     */
    public static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 10) {
            return "****";
        }
        return apiKey.substring(0, 6) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    /**
     * 对 JWT Token 进行脱敏。
     * 保留前 10 位，后面用 **** 替代。
     *
     * @param token 原始 Token
     * @return 脱敏后的 Token
     */
    public static String maskToken(String token) {
        if (token == null || token.length() <= 10) {
            return "****";
        }
        return token.substring(0, 10) + "****";
    }

    /**
     * 对任意字符串进行通用脱敏。
     * 保留前 prefix 位和后 suffix 位。
     *
     * @param text   原始文本
     * @param prefix 保留前缀长度
     * @param suffix 保留后缀长度
     * @return 脱敏后的文本
     */
    public static String mask(String text, int prefix, int suffix) {
        if (text == null || text.length() <= prefix + suffix) {
            return "****";
        }
        return text.substring(0, prefix) + "****" + text.substring(text.length() - suffix);
    }
}
