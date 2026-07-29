package com.businesslogic.groovy.cache;

import com.businesslogic.groovy.engine.CompiledGroovyScript;
import com.businesslogic.groovy.engine.GroovyExpressionEngine;
import com.businesslogic.groovy.engine.GroovyExecutor;
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
 * Groovy 功能表达式缓存服务（支持两阶段提交）
 *
 * <p>对应 Aviator 的 FeatureExpressionCache。
 * 业务逻辑与 Aviator 版本完全一致：
 * <ul>
 *   <li>mainCache: 主缓存区，对外提供服务</li>
 *   <li>stagingCache: 待激活区，Prepare 阶段编译并暂存</li>
 *   <li>Commit 阶段原子写入主缓存</li>
 *   <li>Abort 阶段清理待激活区</li>
 * </ul>
 *
 * <p>关联体系：
 * <ul>
 *   <li>编译依赖 {@link GroovyExpressionEngine}（通过 {@link #getEngine()} 获取单例）</li>
 *   <li>与 {@link GroovyExpressionCache} 互补：本缓存按交易码+特征码索引，支持版本号比对与两阶段提交</li>
 *   <li>CacheEntry 持有 {@link CompiledGroovyScript}，与 Aviator 版持有 Expression 对应</li>
 *   <li>version=-1 作为删除标记，与 Aviator 版语义一致</li>
 * </ul>
 *
 * <p>线程安全：主缓存与待激活区均使用 ConcurrentHashMap；CacheEntry 内部字段使用 volatile
 * 保证可见性；update 方法非原子但单条目并发更新风险低（业务侧通常串行更新同一特征）。
 */
@Component
public class GroovyFeatureExpressionCache {

    private static final Logger logger = LoggerFactory.getLogger(GroovyFeatureExpressionCache.class);

    /**
     * 主缓存区：交易码 → (特征码 → CacheEntry)。
     *
     * <p>为何用嵌套 ConcurrentHashMap：交易码是大粒度分组（如"贷款申请"），特征码是小粒度规则（如"金额校验"）。
     * 嵌套结构便于按交易码批量操作（如 {@link #removeByTransactionCode}）。
     */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, CacheEntry>> cache = new ConcurrentHashMap<>();

    /**
     * 待激活区：syncId::txnCode::featureCode → StagingEntry。
     *
     * <p>为何需要待激活区：分布式环境中多个节点可能同时收到缓存更新通知，
     * Prepare→Commit 两阶段保证所有节点要么全部提交、要么全部回滚，避免中间状态。
     */
    private final ConcurrentHashMap<String, StagingEntry> stagingCache = new ConcurrentHashMap<>();

    /**
     * 获取 Groovy 表达式引擎单例。
     *
     * <p>关联：委托 {@link GroovyExecutor#getEngine()}，所有编译操作都走同一引擎实例，
     * 复用 {@link GroovyExpressionEngine#compileCache} 编译缓存。
     */
    private GroovyExpressionEngine getEngine() {
        return GroovyExecutor.getEngine();
    }

    /**
     * 缓存条目：持有一条特征的源码、版本号与编译结果。
     *
     * <p>关联：被 {@link #cacheExpression} / {@link #commitExpression} 创建；
     * 被 {@link #getEntry} / {@link #getByTransactionCode} 查询；
     * 被 {@link CacheEntry#update} 原地更新（volatile 保证可见性）。
     */
    public static class CacheEntry {
        private final String transactionCode;
        private final String featureCode;
        private volatile Long version;
        private volatile String sourceExpression;
        private volatile CompiledGroovyScript compiledExpression;

        CacheEntry(String transactionCode, String featureCode, Long version,
                   String sourceExpression, CompiledGroovyScript compiledExpression) {
            this.transactionCode = transactionCode;
            this.featureCode = featureCode;
            this.version = version;
            this.sourceExpression = sourceExpression;
            this.compiledExpression = compiledExpression;
        }

        public String getTransactionCode() { return transactionCode; }
        public String getFeatureCode() { return featureCode; }
        public Long getVersion() { return version; }
        public String getSourceExpression() { return sourceExpression; }
        public CompiledGroovyScript getCompiledExpression() { return compiledExpression; }

        void update(Long newVersion, String newExpression, CompiledGroovyScript newCompiled) {
            this.version = newVersion;
            this.sourceExpression = newExpression;
            this.compiledExpression = newCompiled;
        }
    }

    /**
     * 缓存表达式（直接写入主缓存，非两阶段提交路径）。
     *
     * <p>处理逻辑：
     * <ol>
     *   <li>version == -1：删除该特征，返回 REMOVED</li>
     *   <li>编译失败：返回 COMPILE_ERROR</li>
     *   <li>本地无此特征：插入新条目，返回 INSERTED</li>
     *   <li>版本号不同：更新条目，返回 UPDATED</li>
     *   <li>版本号相同：跳过，返回 SKIPPED</li>
     * </ol>
     *
     * <p>为何 version == -1 表示删除：业务约定 -1 是哨兵值，与 Aviator 版语义一致，
     * 避免额外引入 isDeleted 字段。
     *
     * <p>关联：被外部定时同步任务调用（非两阶段提交的快速路径）；
     * 与 {@link #prepareExpression}+{@link #commitExpression} 是互斥的两种写入方式。
     */
    public CacheResult cacheExpression(String transactionCode, String featureCode,
                                        Long version, String expression) {
        if (version != null && version == -1L) {
            CacheEntry removed = removeEntry(transactionCode, featureCode);
            if (removed != null) {
                logger.info("版本号为-1，删除缓存: txn={}, feature={}, 原版本v{}",
                        transactionCode, featureCode, removed.getVersion());
                return CacheResult.removed(removed.getVersion());
            }
            logger.info("版本号为-1，但缓存中不存在: txn={}, feature={}", transactionCode, featureCode);
            return CacheResult.removed(null);
        }

        CompiledGroovyScript compiled;
        try {
            compiled = getEngine().compile(expression);
        } catch (Exception e) {
            logger.error("编译表达式失败: txn={}, feature={}, v={}, error={}",
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

    /**
     * 判断指定特征是否需要更新。
     *
     * <p>判断规则：
     * <ul>
     *   <li>本地无此特征且 incomingVersion 非 -1：需要更新</li>
     *   <li>incomingVersion == -1：需要更新（删除）</li>
     *   <li>incomingVersion > 本地版本：需要更新</li>
     *   <li>其他：不需要</li>
     * </ul>
     *
     * <p>关联：被外部同步任务在拉取前调用，避免不必要的 MGET。
     */
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
            logger.info("移除交易码下所有缓存: txn={}, count={}", transactionCode, removed.size());
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

    // ==================== 两阶段提交 ====================

    public static class StagingEntry {
        private final String syncId;
        private final String transactionCode;
        private final String featureCode;
        private final Long version;
        private final String expression;
        private final CompiledGroovyScript compiled;
        private final long preparedAt;

        StagingEntry(String syncId, String transactionCode, String featureCode,
                     Long version, String expression, CompiledGroovyScript compiled) {
            this.syncId = syncId;
            this.transactionCode = transactionCode;
            this.featureCode = featureCode;
            this.version = version;
            this.expression = expression;
            this.compiled = compiled;
            this.preparedAt = System.currentTimeMillis();
        }

        public String getSyncId() { return syncId; }
        public String getTransactionCode() { return transactionCode; }
        public String getFeatureCode() { return featureCode; }
        public Long getVersion() { return version; }
        public String getExpression() { return expression; }
        public CompiledGroovyScript getCompiled() { return compiled; }
        public long getPreparedAt() { return preparedAt; }
    }

    public enum PrepareResultType { PREPARE_OK, PREPARE_FAILED, ALREADY_EXISTS }
    public enum CommitResultType { COMMIT_OK, COMMIT_NOT_FOUND }

    private String stagingKey(String syncId, String transactionCode, String featureCode) {
        return syncId + "::" + transactionCode + "::" + featureCode;
    }

    /**
     * 阶段一：Prepare（编译表达式并存入待激活区）。
     *
     * <p>为何需要 Prepare 阶段：在分布式缓存同步中，多个节点可能同时收到同一条特征的更新通知。
     * Prepare 阶段先编译验证 + 暂存，确保所有节点都能成功编译后才统一 Commit，
     * 避免部分节点提交成功、部分失败导致缓存不一致。
     *
     * <p>幂等性：同一 syncId+txnCode+featureCode 重复 Prepare 返回 ALREADY_EXISTS，不覆盖。
     *
     * <p>关联：与 {@link #commitExpression} / {@link #abortExpression} 组成两阶段提交三件套。
     *
     * @param syncId 同步批次 ID（用于区分不同批次的 Prepare）
     */
    public PrepareResult prepareExpression(String syncId, String transactionCode, String featureCode,
                                            Long version, String expression) {
        String key = stagingKey(syncId, transactionCode, featureCode);

        if (stagingCache.containsKey(key)) {
            logger.info("Prepare 幂等: syncId={}, {}#{} 已在待激活区", syncId, transactionCode, featureCode);
            return new PrepareResult(PrepareResultType.ALREADY_EXISTS, null);
        }

        if (version != null && version == -1L) {
            StagingEntry entry = new StagingEntry(syncId, transactionCode, featureCode, version, "", null);
            stagingCache.put(key, entry);
            logger.info("Prepare 删除: syncId={}, {}#{}", syncId, transactionCode, featureCode);
            return new PrepareResult(PrepareResultType.PREPARE_OK, null);
        }

        CompiledGroovyScript compiled;
        try {
            compiled = getEngine().compile(expression);
        } catch (Exception e) {
            logger.error("Prepare 编译失败: syncId={}, {}#{} v{}, error={}",
                    syncId, transactionCode, featureCode, version, e.getMessage());
            return new PrepareResult(PrepareResultType.PREPARE_FAILED, "编译失败: " + e.getMessage());
        }

        StagingEntry entry = new StagingEntry(syncId, transactionCode, featureCode, version, expression, compiled);
        stagingCache.put(key, entry);
        logger.info("Prepare 成功: syncId={}, {}#{} v{}", syncId, transactionCode, featureCode, version);
        return new PrepareResult(PrepareResultType.PREPARE_OK, null);
    }

    /**
     * 阶段二：Commit（原子替换主缓存）。
     *
     * <p>从待激活区取出 StagingEntry，写入主缓存。version==-1 时执行删除。
     *
     * <p>关联：必须在 {@link #prepareExpression} 成功后调用；
     * 待激活区无对应数据时返回 COMMIT_NOT_FOUND（可能因超时被 {@link #cleanExpiredStaging} 清理）。
     */
    public CommitResult commitExpression(String syncId, String transactionCode, String featureCode) {
        String key = stagingKey(syncId, transactionCode, featureCode);
        StagingEntry staged = stagingCache.remove(key);

        if (staged == null) {
            logger.info("Commit 未找到待激活数据: syncId={}, {}#{}", syncId, transactionCode, featureCode);
            return new CommitResult(CommitResultType.COMMIT_NOT_FOUND, "待激活区无此数据");
        }

        if (staged.getVersion() != null && staged.getVersion() == -1L) {
            removeEntry(transactionCode, featureCode);
            logger.info("Commit 删除: syncId={}, {}#{}", syncId, transactionCode, featureCode);
            return new CommitResult(CommitResultType.COMMIT_OK, "删除成功");
        }

        ConcurrentHashMap<String, CacheEntry> featureMap = cache.computeIfAbsent(
                transactionCode, k -> new ConcurrentHashMap<>());

        CacheEntry existing = featureMap.get(featureCode);

        if (existing == null) {
            CacheEntry newEntry = new CacheEntry(
                    transactionCode, featureCode,
                    staged.getVersion(), staged.getExpression(), staged.getCompiled());
            featureMap.put(featureCode, newEntry);
            logger.info("Commit 首次缓存: syncId={}, {}#{} v{}",
                    syncId, transactionCode, featureCode, staged.getVersion());
        } else {
            existing.update(staged.getVersion(), staged.getExpression(), staged.getCompiled());
            logger.info("Commit 更新缓存: syncId={}, {}#{} v{}→v{}",
                    syncId, transactionCode, featureCode, existing.getVersion(), staged.getVersion());
        }

        return new CommitResult(CommitResultType.COMMIT_OK, "提交成功");
    }

    /**
     * 异常回滚：Abort。
     *
     * <p>从待激活区移除指定条目，不影响主缓存。
     *
     * <p>关联：在 {@link #prepareExpression} 成功但后续业务异常时调用，
     * 防止待激活区残留脏数据。也可由 {@link #cleanExpiredStaging} 自动清理超时条目。
     */
    public boolean abortExpression(String syncId, String transactionCode, String featureCode) {
        String key = stagingKey(syncId, transactionCode, featureCode);
        StagingEntry removed = stagingCache.remove(key);

        if (removed != null) {
            logger.info("Abort 清理待激活区: syncId={}, {}#{}", syncId, transactionCode, featureCode);
            return true;
        }

        logger.info("Abort 待激活区无数据: syncId={}, {}#{}", syncId, transactionCode, featureCode);
        return false;
    }

    public int stagingEntryCount() {
        return stagingCache.size();
    }

    /**
     * 清理过期待激活条目。
     *
     * <p>为何需要：Prepare 后若调用方异常退出未发 Commit/Abort，待激活区会残留条目。
     * 此方法按 preparedAt 时间戳清理超时条目，防止内存泄漏。
     *
     * <p>关联：被外部定时任务调用；与 {@link #abortExpression} 互为补充——
     * abort 是主动回滚，cleanExpiredStaging 是被动兜底。
     *
     * @param expireAfterSeconds 超时阈值（秒）
     * @return 清理的条目数
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
            logger.info("清理过期待激活数据: count={}, expireAfter={}s", expiredKeys.size(), expireAfterSeconds);
        }

        return expiredKeys.size();
    }

    // ==================== 结果类 ====================

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

        public static CacheResult inserted(Long version) { return new CacheResult(Type.INSERTED, null, version, null); }
        public static CacheResult updated(Long oldVersion, Long newVersion) { return new CacheResult(Type.UPDATED, oldVersion, newVersion, null); }
        public static CacheResult skipped(Long currentVersion) { return new CacheResult(Type.SKIPPED, currentVersion, null, null); }
        public static CacheResult removed(Long oldVersion) { return new CacheResult(Type.REMOVED, oldVersion, null, null); }
        public static CacheResult compileError(String errorMessage) { return new CacheResult(Type.COMPILE_ERROR, null, null, errorMessage); }

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
