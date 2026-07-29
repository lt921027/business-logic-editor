package com.businesslogic.groovy.redisCache;

/**
 * Groovy 表达式 Redis 缓存 Key 常量定义
 *
 * <p>使用独立前缀 groovy-expr: 与 Aviator 缓存隔离。
 *
 * <p>存储结构（3 个 Key）：
 * <pre>
 *   groovy-expr:txn:versions                       Hash    全局交易码版本索引
 *     field: {txnCode}        value: {txnVersion}
 *
 *   groovy-expr:txn:{txnCode}                      Hash    交易码骨架（仅特征版本号）
 *     field: {featureCode}    value: {featureVersion}
 *
 *   groovy-expr:txn:data:{txnCode}:{featureCode}   String  特征源码（Groovy 脚本字符串）
 * </pre>
 */
public final class GroovyExprRedisKeys {

    private GroovyExprRedisKeys() {}

    /** Key 前缀（与 Aviator 缓存隔离） */
    public static final String PREFIX = "groovy-expr:";

    /** 全局交易码版本索引 Hash Key */
    public static final String TXN_VERSIONS_KEY = PREFIX + "txn:versions";

    /** 交易码骨架 Hash Key 前缀 */
    public static final String TXN_SKELETON_PREFIX = PREFIX + "txn:";

    /** 特征源码 Key 前缀 */
    public static final String TXN_DATA_PREFIX = PREFIX + "txn:data:";

    /**
     * 构建交易码骨架 Hash Key
     */
    public static String txnSkeletonKey(String txnCode) {
        return TXN_SKELETON_PREFIX + txnCode;
    }

    /**
     * 构建特征源码 Key
     */
    public static String featureDataKey(String txnCode, String featureCode) {
        return TXN_DATA_PREFIX + txnCode + ":" + featureCode;
    }
}
