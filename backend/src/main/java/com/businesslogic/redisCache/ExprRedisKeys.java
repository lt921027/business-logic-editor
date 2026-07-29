package com.businesslogic.redisCache;

/**
 * Redis 表达式缓存 Key 常量定义（特征级别版本号方案）
 *
 * <p>存储结构（3 个 Key）：
 * <pre>
 *   expr:txn:versions                       Hash    全局交易码版本索引
 *     field: {txnCode}        value: {txnVersion}
 *
 *   expr:txn:{txnCode}                      Hash    交易码骨架（仅特征版本号）
 *     field: {featureCode}    value: {featureVersion}
 *
 *   expr:txn:data:{txnCode}:{featureCode}   String  特征字节（二进制，5-10KB）
 * </pre>
 */
public final class ExprRedisKeys {

    private ExprRedisKeys() {}

    /** Key 前缀 */
    public static final String PREFIX = "expr:";

    /** 全局交易码版本索引 Hash Key */
    public static final String TXN_VERSIONS_KEY = PREFIX + "txn:versions";

    /** 交易码骨架 Hash Key 前缀（field=featureCode, value=featureVersion） */
    public static final String TXN_SKELETON_PREFIX = PREFIX + "txn:";

    /** 特征字节 Key 前缀 */
    public static final String TXN_DATA_PREFIX = PREFIX + "txn:data:";

    /**
     * 构建交易码骨架 Hash Key
     * <p>Key: expr:txn:{txnCode}
     * <p>Field: {featureCode}  Value: {featureVersion}
     */
    public static String txnSkeletonKey(String txnCode) {
        return TXN_SKELETON_PREFIX + txnCode;
    }

    /**
     * 构建特征字节 Key
     * <p>Key: expr:txn:data:{txnCode}:{featureCode}
     */
    public static String featureDataKey(String txnCode, String featureCode) {
        return TXN_DATA_PREFIX + txnCode + ":" + featureCode;
    }
}
