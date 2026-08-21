package com.businesslogic.groovy.redisCache;

/**
 * Groovy 表达式 Redis 缓存 Key 常量定义。
 *
 * <p>缓存维度从交易码改为源报文编号（sourceNo）。Redis 中不再保留 txn 维度 Key。</p>
 *
 * <pre>
 *   groovy-expr:source:global-version          String   全局整体更新版本号
 *   groovy-expr:source:versions                Hash     sourceNo -> sourceVersion
 *   groovy-expr:source:script:{sourceNo}       String   源报文对应的合并后整体脚本
 * </pre>
 */
public final class GroovyExprRedisKeys {

    private GroovyExprRedisKeys() {}

    /** Key 前缀 */
    public static final String PREFIX = "groovy-expr:source:";

    /** 全局整体更新版本号 Key */
    public static final String GLOBAL_VERSION_KEY = PREFIX + "global-version";

    /** 每个源报文当前版本列表 Hash Key */
    public static final String SOURCE_VERSIONS_KEY = PREFIX + "versions";

    /** 源报文脚本 Key 前缀 */
    public static final String SOURCE_SCRIPT_PREFIX = PREFIX + "script:";

    /**
     * 构建源报文脚本 Key。
     *
     * @param sourceNo 源报文编号
     * @return Redis Key
     */
    public static String sourceScriptKey(String sourceNo) {
        return SOURCE_SCRIPT_PREFIX + sourceNo;
    }
}
