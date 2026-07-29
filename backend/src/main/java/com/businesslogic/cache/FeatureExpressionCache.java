package com.businesslogic.cache;

import com.googlecode.aviator.Expression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 功能表达式缓存服务（支持两阶段提交）
 * 
 * 核心职责 * - mainCache:    主缓存区，对外提供服务的表达式存储于 * - stagingCache: 待激活区，Prepare 阶段编译并暂存于此，Commit 阶段移入主缓 * 
 * 两阶段提交流程：
 * 阶段一 Prepare:
 *   1. 编译新表达式，生Aviator 预编译对 *   2. 存入 stagingCache（待激活区），不影响主缓存
 *   3. 返回 PREPARE_OK PREPARE_FAILED（编译失败）
 * 
 * 阶段Commit:
 *   1. stagingCache 取出预编译对 *   2. 使用 ConcurrentHashMap.put() 原子写入主缓存（无额外加锁）
 *   3. 清理 stagingCache
 * 
 * 异常回滚 Abort:
 *   1. 清理 stagingCache 中对key 的条 * 
 * 设计说明 * - ConcurrentHashMap.put() 本身是原子操作，无需额外加锁
 * - 读操作始终从 mainCache 获取，无阻塞
 * - stagingCache mainCache 隔离，Prepare 失败不影响线上服 * - 所Pod 同时 Commit 后同步切换表达式版本
 */
@Component
public class FeatureExpressionCache {

    private static final Logger logger = LoggerFactory.getLogger(FeatureExpressionCache.class);

    /** 主缓存区（对外服务）: 交易(功能CacheEntry) */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, CacheEntry>> cache = new ConcurrentHashMap<>();

    /** 待激活区（Prepare 阶段暂存 "syncId:txnCode:featureCode" StagingEntry */
    private final ConcurrentHashMap<String, StagingEntry> stagingCache = new ConcurrentHashMap<>();

    public static class CacheEntry {
        private final String transactionCode;
        private final String featureCode;
        private volatile Long version;
        private volatile String sourceExpression;
        private volatile Expression compiledExpression;

        CacheEntry(String transactionCode, String featureCode, Long version, String sourceExpression, Expression compiledExpression) {
            this.transactionCode = transactionCode;
            this.featureCode = featureCode;
            this.version = version;
            this.sourceExpression = sourceExpression;
            this.compiledExpression = compiledExpression;
        }

        public String getTransactionCode() {
            return transactionCode;
        }

        public String getFeatureCode() {
            return featureCode;
        }

        public Long getVersion() {
            return version;
        }

        public String getSourceExpression() {
            return sourceExpression;
        }

        public Expression getCompiledExpression() {
            return compiledExpression;
        }

        void update(Long newVersion, String newExpression, Expression newCompiled) {
            this.version = newVersion;
            this.sourceExpression = newExpression;
            this.compiledExpression = newCompiled;
        }
    }

    public CacheResult cacheExpression(String transactionCode, String featureCode,
                                        Long version, String expression) {
        if (version != null && version == -1L) {
            CacheEntry removed = removeEntry(transactionCode, featureCode);
            if (removed != null) {
                logger.info("版本号为-1，删除缓 txn={}, feature={}, 原版本v{}",
                        transactionCode, featureCode, removed.getVersion());
                return CacheResult.removed(removed.getVersion());
            }
            logger.info("版本号为-1，但缓存中不存在: txn={}, feature={}", transactionCode, featureCode);
            return CacheResult.removed(null);
        }
        com.googlecode.aviator.AviatorEvaluatorInstance evaluator =
                com.googlecode.aviator.AviatorEvaluator.getInstance();

        Expression compiled;
        try {
            compiled = evaluator.compile(expression);
        } catch (Exception e) {
            logger.error("编译表达式失 txn={}, feature={}, v={}, error={}",
                    transactionCode, featureCode, version, e.getMessage());
            return CacheResult.compileError("编译失败: " + e.getMessage());
        }

        ConcurrentHashMap<String, CacheEntry> featureMap = cache.computeIfAbsent(
                transactionCode, k -> new ConcurrentHashMap<>());

        CacheEntry existing = featureMap.get(featureCode);

        if (existing == null) {
            CacheEntry entry = new CacheEntry(transactionCode, featureCode, version, expression, compiled);
            featureMap.put(featureCode, entry);
            logger.info("首次缓存: txn={}, feature={}, v={}", transactionCode, featureCode, version);
            return CacheResult.inserted(version);
        }

        if (version != existing.getVersion()) {
            existing.update(version, expression, compiled);
            logger.info("更新缓存: txn={}, feature={}, v{}→v{}",
                    transactionCode, featureCode, existing.getVersion(), version);
            return CacheResult.updated(existing.getVersion(), version);
        }

        logger.debug("跳过更新: txn={}, feature={}, 当前v{} >= 传入v{}",
                transactionCode, featureCode, existing.getVersion(), version);
        return CacheResult.skipped(existing.getVersion());
    }

    public boolean needsUpdate(String transactionCode, String featureCode, Long incomingVersion) {
        ConcurrentHashMap<String, CacheEntry> featureMap = cache.get(transactionCode);
        if (featureMap == null) {
            return incomingVersion != null && incomingVersion != -1L;
        }

        CacheEntry entry = featureMap.get(featureCode);
        if (entry == null) {
            return incomingVersion != null && incomingVersion != -1L;
        }

        if (incomingVersion != null && incomingVersion == -1L) {
            return true;
        }

        return incomingVersion > entry.getVersion();
    }

    public CacheEntry getEntry(String transactionCode, String featureCode) {
        Map<String, CacheEntry> featureMap = cache.get(transactionCode);
        if (featureMap == null) {
            return null;
        }
        return featureMap.get(featureCode);
    }

    public Map<String, CacheEntry> getByTransactionCode(String transactionCode) {
        Map<String, CacheEntry> featureMap = cache.get(transactionCode);
        if (featureMap == null) {
            return Collections.emptyMap();
        }
        return new HashMap<>(featureMap);
    }

    public Map<String, Long> getVersionMap(String transactionCode) {
        Map<String, CacheEntry> featureMap = cache.get(transactionCode);
        if (featureMap == null) {
            return Collections.emptyMap();
        }
        Map<String, Long> result = new HashMap<>();
        for (Map.Entry<String, CacheEntry> e : featureMap.entrySet()) {
            result.put(e.getKey(), e.getValue().getVersion());
        }
        return result;
    }

    public ArrayList<CacheEntry> getAllEntries() {
        ArrayList<CacheEntry> result = new ArrayList<>();
        for (ConcurrentHashMap<String, CacheEntry> featureMap : cache.values()) {
            result.addAll(featureMap.values());
        }
        return result;
    }

    public CacheEntry removeEntry(String transactionCode, String featureCode) {
        Map<String, CacheEntry> featureMap = cache.get(transactionCode);
        if (featureMap == null) {
            return null;
        }
        CacheEntry removed = featureMap.remove(featureCode);
        if (removed != null) {
            logger.info("移除缓存: txn={}, feature={}", transactionCode, featureCode);
        }
        if (featureMap.isEmpty()) {
            cache.remove(transactionCode);
        }
        return removed;
    }

    public void removeByTransactionCode(String transactionCode) {
        Map<String, CacheEntry> removed = cache.remove(transactionCode);
        if (removed != null) {
            logger.info("移除交易码下所有缓 txn={}, count={}", transactionCode, removed.size());
        }
    }

    public void clearAll() {
        int total = cache.values().stream().mapToInt(Map::size).sum();
        cache.clear();
        logger.info("清除所有缓存: count={}", total);
    }

    public int totalEntryCount() {
        return cache.values().stream().mapToInt(Map::size).sum();
    }

    public static class CacheResult {
        public enum Type { INSERTED, UPDATED, SKIPPED, REMOVED, COMPILE_ERROR }

        private final Type type;
        private final Long oldVersion;
        private final Long newVersion;
        private final String errorMessage;

        private CacheResult(Type type, Long oldVersion, Long newVersion, String errorMessage) {
            this.type = type;
            this.oldVersion = oldVersion;
            this.newVersion = newVersion;
            this.errorMessage = errorMessage;
        }

        public static CacheResult inserted(Long version) {
            return new CacheResult(Type.INSERTED, null, version, null);
        }

        public static CacheResult updated(Long oldVersion, Long newVersion) {
            return new CacheResult(Type.UPDATED, oldVersion, newVersion, null);
        }

        public static CacheResult skipped(Long currentVersion) {
            return new CacheResult(Type.SKIPPED, currentVersion, null, null);
        }

        public static CacheResult removed(Long oldVersion) {
            return new CacheResult(Type.REMOVED, oldVersion, null, null);
        }

        public static CacheResult compileError(String errorMessage) {
            return new CacheResult(Type.COMPILE_ERROR, null, null, errorMessage);
        }

        public Type getType() { return type; }
        public Long getOldVersion() { return oldVersion; }
        public Long getNewVersion() { return newVersion; }
        public String getErrorMessage() { return errorMessage; }

        @Override
        public String toString() {
            switch (type) {
                case INSERTED: return "INSERTED(v" + newVersion + ")";
                case UPDATED: return "UPDATED(v" + oldVersion + "→v" + newVersion + ")";
                case SKIPPED: return "SKIPPED(当前v" + oldVersion + ")";
                case REMOVED: return "REMOVED(原v" + (oldVersion != null ? oldVersion : "?") + ")";
                case COMPILE_ERROR: return "COMPILE_ERROR(" + errorMessage + ")";
                default: return type.name();
            }
        }
    }

    /**
     * 待激活区条目（Prepare 阶段暂存     * 
     * 存储stagingCache 中，Commit 成功后才移入 mainCache     * 
     * 字段说明     * - syncId:          广播标识，用于关Prepare Commit
     * - transactionCode: 交易     * - featureCode:     功能     * - version:         新版本号
     * - expression:      表达式源     * - compiled:        预编译后Aviator 对象
     * - preparedAt:      Prepare 完成时间     */
    public static class StagingEntry {
        private final String syncId;
        private final String transactionCode;
        private final String featureCode;
        private final Long version;
        private final String expression;
        private final Expression compiled;
        private final long preparedAt;

        StagingEntry(String syncId, String transactionCode, String featureCode,
                     Long version, String expression, Expression compiled) {
            this.syncId = syncId;
            this.transactionCode = transactionCode;
            this.featureCode = featureCode;
            this.version = version;
            this.expression = expression;
            this.compiled = compiled;
            this.preparedAt = System.currentTimeMillis();
        }

        public String getSyncId() {
            return syncId;
        }

        public String getTransactionCode() {
            return transactionCode;
        }

        public String getFeatureCode() {
            return featureCode;
        }

        public Long getVersion() {
            return version;
        }

        public String getExpression() {
            return expression;
        }

        public Expression getCompiled() {
            return compiled;
        }

        public long getPreparedAt() {
            return preparedAt;
        }
    }

    /**
     * Prepare 阶段结果
     * 
     * - PREPARE_OK:     编译成功，已存入 stagingCache
     * - PREPARE_FAILED: 编译失败，stagingCache 中无数据
     * - ALREADY_EXISTS: 幂等处理，该 syncId 已在 stagingCache      */
    public enum PrepareResultType {
        PREPARE_OK,
        PREPARE_FAILED,
        ALREADY_EXISTS
    }

    /**
     * Commit 阶段结果
     * 
     * - COMMIT_OK:        成功stagingCache 移入 mainCache
     * - COMMIT_NOT_FOUND: stagingCache 中无对应syncId 的数据（可能已被清理     */
    public enum CommitResultType {
        COMMIT_OK,
        COMMIT_NOT_FOUND
    }

    // ======================== 两阶段提交方========================

    /**
     * 构建 stagingCache key
     * 
     * 格式: "syncId::txnCode::featureCode"
     * 用于Prepare Commit 阶段之间关联数据
     * 
     * @param syncId          广播标识
     * @param transactionCode 交易     * @param featureCode     功能     * @return stagingCache key
     */
    private String stagingKey(String syncId, String transactionCode, String featureCode) {
        return syncId + "::" + transactionCode + "::" + featureCode;
    }

    /**
     * 阶段一：Prepare（编译表达式并存入待激活区     * 
     * 执行流程     * 1. 构建 stagingKey
     * 2. 检查幂等性（同一 syncId Prepare 过则直接返回     * 3. 调用 AviatorEvaluator 编译表达     * 4. 编译成功 创建 StagingEntry 存入 stagingCache 返回 PREPARE_OK
     * 5. 编译失败 返回 PREPARE_FAILED + 错误信息
     * 
     * 设计说明     * - 此阶段不修改 mainCache，不影响线上服务
     * - 编译失败的表达式不会进入 stagingCache
     * - 幂等处理避免重复编译
     * 
     * @param syncId          广播标识
     * @param transactionCode 交易     * @param featureCode     功能     * @param version         新版本号1L 表示删除     * @param expression      表达式源     * @return PrepareResult 对象
     */
    public PrepareResult prepareExpression(String syncId, String transactionCode, String featureCode,
                                            Long version, String expression) {
        String key = stagingKey(syncId, transactionCode, featureCode);

        // 幂等处理：同一 syncId Prepare         
        if (stagingCache.containsKey(key)) {
            logger.info("Prepare 幂等: syncId={}, {}#{} 已在待激活区", syncId, transactionCode, featureCode);
            return new PrepareResult(PrepareResultType.ALREADY_EXISTS, null);
        }

        // 删除操作无需编译，直接存stagingCache
        if (version != null && version == -1L) {
            StagingEntry entry = new StagingEntry(syncId, transactionCode, featureCode, version, "", null);
            stagingCache.put(key, entry);
            logger.info("Prepare 删除: syncId={}, {}#{}", syncId, transactionCode, featureCode);
            return new PrepareResult(PrepareResultType.PREPARE_OK, null);
        }

        // 编译表达        
        com.googlecode.aviator.AviatorEvaluatorInstance evaluator =
                com.googlecode.aviator.AviatorEvaluator.getInstance();

        Expression compiled;
        try {
            compiled = evaluator.compile(expression);
        } catch (Exception e) {
            logger.error("Prepare 编译失败: syncId={}, {}#{} v{}, error={}",
                    syncId, transactionCode, featureCode, version, e.getMessage());
            return new PrepareResult(PrepareResultType.PREPARE_FAILED,
                    "编译失败: " + e.getMessage());
        }

        // 存入待激活区
        StagingEntry entry = new StagingEntry(syncId, transactionCode, featureCode, version, expression, compiled);
        stagingCache.put(key, entry);
        logger.info("Prepare 成功: syncId={}, {}#{} v{}", syncId, transactionCode, featureCode, version);
        return new PrepareResult(PrepareResultType.PREPARE_OK, null);
    }

    /**
     * 阶段二：Commit（原子替换主缓存     * 
     * 执行流程     * 1. 构建 stagingKey，从 stagingCache 中查StagingEntry
     * 2. 若找不到 返回 COMMIT_NOT_FOUND（可能已Abort 清理     * 3. 若找到：
     *    a. 删除操作（version == -1L）→ mainCache 移除对应条目
     *    b. 更新操作 使用 ConcurrentHashMap.put() 原子写入 mainCache
     * 4. 清理 stagingCache 中对应条     * 5. 返回 COMMIT_OK
     * 
     * 设计说明     * - ConcurrentHashMap.put() 是原子操作，无需额外加锁
     * - put() 仅锁定对segment，不影响其他 key 的读     * - 读操作始终从 mainCache 获取，commit 期间不阻塞读
     * - 写完后立即清stagingCache，释放内     * 
     * @param syncId          广播标识（与 Prepare 时一致）
     * @param transactionCode 交易     * @param featureCode     功能     * @return CommitResult 对象
     */
    public CommitResult commitExpression(String syncId, String transactionCode, String featureCode) {
        String key = stagingKey(syncId, transactionCode, featureCode);
        StagingEntry staged = stagingCache.remove(key);

        // stagingCache 中无对应数据
        if (staged == null) {
            logger.info("Commit 未找到待激活数 syncId={}, {}#{}", syncId, transactionCode, featureCode);
            return new CommitResult(CommitResultType.COMMIT_NOT_FOUND, "待激活区无此数据");
        }

        // 删除操作：从主缓存中移除
        if (staged.getVersion() != null && staged.getVersion() == -1L) {
            removeEntry(transactionCode, featureCode);
            logger.info("Commit 删除: syncId={}, {}#{}", syncId, transactionCode, featureCode);
            return new CommitResult(CommitResultType.COMMIT_OK, "删除成功");
        }

        //更新操作：原子写入主缓存（ConcurrentHashMap.put()
        ConcurrentHashMap<String, CacheEntry> featureMap = cache.computeIfAbsent(
                transactionCode, k -> new ConcurrentHashMap<>());

        CacheEntry existing = featureMap.get(featureCode);

        if (existing == null) {
            // 首次缓存
            CacheEntry newEntry = new CacheEntry(
                    transactionCode, featureCode,
                    staged.getVersion(), staged.getExpression(), staged.getCompiled());
            featureMap.put(featureCode, newEntry);
            logger.info("Commit 首次缓存: syncId={}, {}#{} v{}",
                    syncId, transactionCode, featureCode, staged.getVersion());
        } else {
            //更新已有缓存（volatile 字段原子更新
            existing.update(staged.getVersion(), staged.getExpression(), staged.getCompiled());
            logger.info("Commit 更新缓存: syncId={}, {}#{} v{}→v{}",
                    syncId, transactionCode, featureCode, existing.getVersion(), staged.getVersion());
        }

        return new CommitResult(CommitResultType.COMMIT_OK, "提交成功");
    }

    /**
     * 异常回滚：Abort（清理待激活区     * 
     * 执行流程     * 1. 构建 stagingKey，从 stagingCache 中移除对应条     * 2. 若存清理成功，返回清理数     * 3. 若不存在 可能已被 Cleanup 或之前的 Abort 清理
     * 
     * 触发场景     * - Prepare 阶段任一 Pod 编译失败
     * - Prepare 阶段 Pod 下线
     * - Prepare 阶段网络超时
     * 
     * 设计说明     * - 仅清stagingCache，不影响 mainCache
     * - 幂等操作：重Abort 不会出错
     * - 确保 stagingCache 不会残留无效数据
     * 
     * @param syncId          广播标识
     * @param transactionCode 交易     * @param featureCode     功能     * @return 是否清理成功
     */
    public boolean abortExpression(String syncId, String transactionCode, String featureCode) {
        String key = stagingKey(syncId, transactionCode, featureCode);
        StagingEntry removed = stagingCache.remove(key);

        if (removed != null) {
            logger.info("Abort 清理待激活区: syncId={}, {}#{}", syncId, transactionCode, featureCode);
            return true;
        }

        logger.info("Abort 待激活区无数据（可能已清理）: syncId={}, {}#{}", syncId, transactionCode, featureCode);
        return false;
    }

    /**
     * 获取待激活区条目数量（用于监控）
     * 
     * @return stagingCache 中的条目     */
    public int stagingEntryCount() {
        return stagingCache.size();
    }

    /**
     * 清理过期的待激活区条目（超过指定时间未 Commit 的数据）
     * 
     * 执行流程     * 1. 遍历 stagingCache
     * 2. 移除 preparedAt 早于过期时间的条     * 3. 记录清理日志
     * 
     * 触发场景     * - 定时任务清理泄露staging 数据
     * - Prepare 成功后发起方崩溃，导致数据永久残留在 stagingCache
     * 
     * @param expireAfterSeconds 过期时间（秒），超过此时间未 Commit 的条目将被清     * @return 清理的条目数
     */
    public int cleanExpiredStaging(long expireAfterSeconds) {
        long now = System.currentTimeMillis();
        long expireBefore = now - expireAfterSeconds * 1000;
        List<String> expiredKeys = new ArrayList<>();

        for (Map.Entry<String, StagingEntry> entry : stagingCache.entrySet()) {
            if (entry.getValue().getPreparedAt() < expireBefore) {
                expiredKeys.add(entry.getKey());
            }
        }

        for (String key : expiredKeys) {
            stagingCache.remove(key);
        }

        if (!expiredKeys.isEmpty()) {
            logger.info("清理过期待激活数 count={}, expireAfter={}s", expiredKeys.size(), expireAfterSeconds);
        }

        return expiredKeys.size();
    }

    // ======================== 两阶段提交结果类 ========================

    /**
     * Prepare 阶段结果
     */
    public static class PrepareResult {
        private final PrepareResultType type;
        private final String errorMessage;

        PrepareResult(PrepareResultType type, String errorMessage) {
            this.type = type;
            this.errorMessage = errorMessage;
        }

        public PrepareResultType getType() { return type; }
        public String getErrorMessage() { return errorMessage; }
        public boolean isOk() { return type == PrepareResultType.PREPARE_OK || type == PrepareResultType.ALREADY_EXISTS; }
        public boolean isFailed() { return type == PrepareResultType.PREPARE_FAILED; }

        @Override
        public String toString() {
            switch (type) {
                case PREPARE_OK: return "PREPARE_OK";
                case PREPARE_FAILED: return "PREPARE_FAILED(" + errorMessage + ")";
                case ALREADY_EXISTS: return "ALREADY_EXISTS";
                default: return type.name();
            }
        }
    }

    /**
     * Commit 阶段结果
     */
    public static class CommitResult {
        private final CommitResultType type;
        private final String message;

        CommitResult(CommitResultType type, String message) {
            this.type = type;
            this.message = message;
        }

        public CommitResultType getType() { return type; }
        public String getMessage() { return message; }
        public boolean isOk() { return type == CommitResultType.COMMIT_OK; }

        @Override
        public String toString() {
            return type.name() + "(" + message + ")";
        }
    }
}