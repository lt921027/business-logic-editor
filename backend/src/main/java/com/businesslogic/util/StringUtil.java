package com.businesslogic.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 字符串工具类 - 用于 Aviator 表达式调
 * 提供常用的字符串操作方法
 */
@Component
public class StringUtil {

    private static final Logger logger = LoggerFactory.getLogger(StringUtil.class);

    /**
     * 判断字符串是否为
     * @param str 待检查的字符
     * @return true-为空或null，false-不为
     */
    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    /**
     * 判断字符串是否不为空
     * @param str 待检查的字符
     * @return true-不为空，false-为空或null
     */
    public static boolean isNotEmpty(String str) {
        return str != null && !str.isEmpty();
    }

    /**
     * 判断字符串是否为空白（包含空格、制表符等）
     * @param str 待检查的字符
     * @return true-为空白或null，false-不为空白
     */
    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * 判断字符串是否不为空
     * @param str 待检查的字符
     * @return true-不为空白，false-为空白或null
     */
    public static boolean isNotBlank(String str) {
        return str != null && !str.trim().isEmpty();
    }

    /**
     * 去除字符串两端空
     * @param str 原始字符
     * @return 去除空格后的字符串，如果为null则返回null
     */
    public static String trim(String str) {
        return str == null ? null : str.trim();
    }

    /**
     * 字符串转大写
     * @param str 原始字符
     * @return 大写字符串，如果为null则返回null
     */
    public static String toUpperCase(String str) {
        return str == null ? null : str.toUpperCase();
    }

    /**
     * 字符串转小写
     * @param str 原始字符
     * @return 小写字符串，如果为null则返回null
     */
    public static String toLowerCase(String str) {
        return str == null ? null : str.toLowerCase();
    }

    /**
     * 字符串替
     * @param str 原始字符
     * @param target 被替换的目标字符
     * @param replacement 替换后的字符
     * @return 替换后的字符
     */
    public static String replace(String str, String target, String replacement) {
        if (str == null || target == null) {
            return str;
        }
        return str.replace(target, replacement);
    }

    /**
     * 字符串是否包含指定子
     * @param str 原始字符
     * @param substring 要查找的子串
     * @return true-包含，false-不包
     */
    public static boolean contains(String str, String substring) {
        if (str == null || substring == null) {
            return false;
        }
        return str.contains(substring);
    }

    /**
     * 字符串是否以指定前缀开
     * @param str 原始字符
     * @param prefix 前缀字符
     * @return true-以指定前缀开头，false-不是
     */
    public static boolean startsWith(String str, String prefix) {
        if (str == null || prefix == null) {
            return false;
        }
        return str.startsWith(prefix);
    }

    /**
     * 字符串是否以指定后缀结尾
     * @param str 原始字符
     * @param suffix 后缀字符
     * @return true-以指定后缀结尾，false-不是
     */
    public static boolean endsWith(String str, String suffix) {
        if (str == null || suffix == null) {
            return false;
        }
        return str.endsWith(suffix);
    }

    /**
     * 获取字符串长
     * @param str 原始字符
     * @return 字符串长度，如果为null则返
     */
    public static int length(String str) {
        return str == null ? 0 : str.length();
    }

    /**
     * 截取子字符串
     * @param str 原始字符
     * @param beginIndex 起始索引（包含）
     * @return 截取后的字符
     */
    public static String substring(String str, int beginIndex) {
        if (str == null || beginIndex < 0 || beginIndex >= str.length()) {
            return str;
        }
        return str.substring(beginIndex);
    }

    /**
     * 截取子字符串（指定起始和结束索引
     * @param str 原始字符
     * @param beginIndex 起始索引（包含）
     * @param endIndex 结束索引（不包含
     * @return 截取后的字符
     */
    public static String substring(String str, int beginIndex, int endIndex) {
        if (str == null || beginIndex < 0 || endIndex > str.length() || beginIndex >= endIndex) {
            return str;
        }
        return str.substring(beginIndex, endIndex);
    }

    /**
     * 字符串分
     * @param str 原始字符
     * @param delimiter 分隔
     * @return 分割后的数组
     */
    public static String[] split(String str, String delimiter) {
        if (str == null || delimiter == null) {
            return new String[0];
        }
        return str.split(delimiter);
    }

    /**
     * 字符串连接（用指定分隔符连接数组元素
     * @param delimiter 分隔
     * @param elements 要连接的元素数组
     * @return 连接后的字符
     */
    public static String join(String delimiter, Object... elements) {
        if (elements == null || elements.length == 0) {
            return "";
        }
        if (delimiter == null) {
            delimiter = "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < elements.length; i++) {
            if (i > 0) {
                sb.append(delimiter);
            }
            sb.append(elements[i] == null ? "" : elements[i].toString());
        }
        return sb.toString();
    }

    /**
     * 字符串比较（忽略大小写）
     * @param str1 字符
     * @param str2 字符
     * @return true-相等，false-不相
     */
    public static boolean equalsIgnoreCase(String str1, String str2) {
        if (str1 == null && str2 == null) {
            return true;
        }
        if (str1 == null || str2 == null) {
            return false;
        }
        return str1.equalsIgnoreCase(str2);
    }

    /**
     * 字符串比较（区分大小写）
     * @param str1 字符
     * @param str2 字符
     * @return true-相等，false-不相
     */
    public static boolean equals(String str1, String str2) {
        if (str1 == null && str2 == null) {
            return true;
        }
        if (str1 == null || str2 == null) {
            return false;
        }
        return str1.equals(str2);
    }

    /**
     * 字符串拼
     * @param str1 字符
     * @param str2 字符
     * @return 拼接后的字符
     */
    public static String concat(String str1, String str2) {
        if (str1 == null) {
            str1 = "";
        }
        if (str2 == null) {
            str2 = "";
        }
        return str1 + str2;
    }

    /**
     * 重复字符
     * @param str 原始字符
     * @param count 重复次数
     * @return 重复后的字符
     */
    public static String repeat(String str, int count) {
        if (str == null || count <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(str.length() * count);
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }

    /**
     * 如果字符串为null或空，返回默认
     * @param str 原始字符
     * @param defaultValue 默认
     * @return 原始字符串或默认
     */
    public static String defaultIfEmpty(String str, String defaultValue) {
        return isEmpty(str) ? defaultValue : str;
    }

    /**
     * 如果字符串为null、空或空白，返回默认
     * @param str 原始字符
     * @param defaultValue 默认
     * @return 原始字符串或默认
     */
    public static String defaultIfBlank(String str, String defaultValue) {
        return isBlank(str) ? defaultValue : str;
    }
}
