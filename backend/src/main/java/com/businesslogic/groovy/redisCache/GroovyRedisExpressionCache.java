package com.businesslogic.groovy.redisCache;

import com.businesslogic.groovy.engine.CompiledGroovyScript;
import com.businesslogic.groovy.engine.GroovyExecutor;
import com.businesslogic.groovy.engine.GroovyExpressionEngine;
import com.businesslogic.util.RedisUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Groovy 表达式 Redis 缓存服务（特征级别版本号方案）
 *
 * <p>对应 Aviator 的 AviatorRedisExpressionCache，业务逻辑保持一致。
 *
 * <p>与 Aviator 版本的核心差异：
 * <ul>
 *   <li>Redis 存储源码字符串（String），而非序列化的 byte[]</li>
 *   <li>反序列化即重新编译源码为 CompiledGroovyScript</li>
 *   <li>使用独立 Key 前缀 groovy-expr: 与 Aviator 缓存隔离</li>
 *   <li>multiGet 使用 String 类型而非 byte[]</li>
 * </ul>
 *
 * <p>Redis 存储结构（3 个 Key）：
 * <pre>
 *   groovy-expr:txn:versions                       Hash    全局交易码版本索引
 *     field: {txnCode}        value: {txnVersion}
 *
 *   groovy-expr:txn:{txnCode}                      Hash    交易码骨架（仅特征版本号）
 *     field: {featureCode}    value: {featureVersion}
 *
 *   groovy-expr:txn:data:{txnCode}:{featureCode}   String  特征源码（Groovy 脚本字符串）
 * </pre>
 *
 * <p>同步机制（与 Aviator 保持一致）：
 * <ul>
 *   <li>启动时：全量加载所有交易码</li>
 *   <li>运行时：定时轮询交易码版本号，增量同步变化的交易码（仅重载变化的特征）</li>
 *   <li>查询时：按需检查单交易码版本号，不一致则精确同步</li>
 *   <li>并发控制：ReentrantLock.tryLock()，拿不到锁直接放弃，不排队</li>
 *   <li>数据完整性：重载过程中任何失败都会中止整个重载，保留旧缓存，下轮重试</li>
 * </ul>
 */
@Component
public class GroovyRedisExpressionCache {

    private static final Logger logger = LoggerFactory.getLogger(GroovyRedisExpressionCache.class);

    @Autowired
    private RedisUtils redisUtils;

    private final GroovyExpressionEngine engine;

    // ==================== 本地缓存 ====================

    /**
     * 交易码 → 上下文（含版本号 + 特征Map）。
     *
     * <p>为何用 volatile：整体替换式更新——每次重载构建新 Map 后整体赋值，
     * volatile 保证其他线程立即可见。读操作无锁，性能优于 ConcurrentHashMap 的 segment 锁。
     */
    private volatile Map<String, GroovyTxnExpressionContext> localCache = Collections.emptyMap();

    /**
     * 交易码 → 本地版本号（用于定时轮询比对）。
     *
     * <p>为何单独维护版本号 Map：避免每次轮询都遍历 localCache 取 version，
     * 扁平 Map 查询更高效。
     */
    private final ConcurrentHashMap<String, Long> localVersions = new ConcurrentHashMap<>();

    /** 缓存是否已初始化（首次全量加载完成后置 true） */
    private volatile boolean initialized = false;

    /**
     * 重载锁。
     *
     * <p>为何用 tryLock 而非 synchronized：定时轮询与查询触发的精确同步可能竞争同一把锁，
     * tryLock 拿不到立即放弃，避免查询线程被轮询阻塞，保证查询低延迟。
     */
    private final ReentrantLock reloadLock = new ReentrantLock();

    /**
     * 异步重载线程池（单线程，保证串行执行）。
     *
     * <p>为何用守护线程：避免阻止 JVM 退出；为何单线程：重载任务串行化简化并发控制，
     * 避免多次全量重载互相干扰。
     */
    private final ExecutorService asyncReloadExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "groovy-expr-cache-async-reload");
        t.setDaemon(true);
        return t;
    });

    /**
     * 构造方法：获取引擎单例。
     *
     * <p>关联：通过 {@link GroovyExecutor#getEngine()} 获取全局引擎，
     * 与其他 Groovy 组件共享编译缓存。
     */
    public GroovyRedisExpressionCache() {
        this.engine = GroovyExecutor.getEngine();
    }

    /**
     * PostConstruct 钩子：仅打印日志，真正的初始化在 ApplicationReadyEvent 中执行。
     *
     * <p>为何不在 @PostConstruct 中全量加载：此时 Redis 连接可能尚未就绪，
     * ApplicationReadyEvent 保证所有 Bean 初始化完成后再加载。
     */
    @PostConstruct
    public void init() {
        logger.info("[GroovyExprCache] 初始化完成，Groovy 引擎就绪，编译缓存大小={}", engine.getCompileCacheSize());
    }

    // ==================== 启动全量加载 ====================

    /**
     * 启动后执行首次全量加载。
     *
     * <p>关联：委托 {@link #fullReload}；加载完成后置 {@link #initialized} = true，
     * 让定时轮询 {@link #scheduledPoll} 开始工作。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            fullReload();
            initialized = true;
            logger.info("[GroovyExprCache] 首次全量加载完成，交易码数={}, 特征数={}",
                    localCache.size(), totalFeatureCount());
        } catch (Exception e) {
            logger.error("[GroovyExprCache] 首次全量加载失败", e);
        }
    }

    // ==================== 定时轮询 ====================

    /**
     * 定时轮询：每 10 秒检查交易码版本号，增量同步变化的交易码。
     *
     * <p>为何用 fixedDelay 而非 fixedRate：fixedDelay 在上次执行结束后再计时，
     * 避免重载耗时较长时多次任务叠加执行。
     *
     * <p>为何 initialDelay=15s：给首次全量加载留出缓冲时间，避免与 onApplicationReady 重叠。
     *
     * <p>关联：委托 {@link #pollAndReload}；仅在 {@link #initialized}=true 后生效。
     */
    @Scheduled(fixedDelayString = "${groovy.expr.cache.poll-interval-ms:10000}",
               initialDelayString = "${groovy.expr.cache.poll-initial-delay-ms:15000}")
    public void scheduledPoll() {
        if (!initialized) {
            return;
        }
        try {
            pollAndReload();
        } catch (Exception e) {
            logger.error("[GroovyExprCache] 定时轮询异常", e);
        }
    }

    /**
     * 轮询核心逻辑：HGETALL 全局版本索引 → 与本地比对 → 增量同步变化的交易码。
     *
     * <p>为何先无锁比对再加锁执行：HGETALL 与本地版本比对是只读操作，无需加锁；
     * 找到变化后再 tryLock 执行同步，减少锁持有时间。
     *
     * <p>为何加锁后双重检查：无锁比对到加锁之间，其他线程可能已完成同步，
     * 双重检查避免重复同步。
     *
     * <p>关联：被 {@link #scheduledPoll} 调用；委托 {@link #syncTxnFeatures} 同步单个交易码，
     * 委托 {@link #removeFromLocalCache} 清理已删除的交易码。
     */
    void pollAndReload() {
        // 1. HGETALL 拿所有交易码版本号
        Map<Object, Object> redisVersions = redisUtils.hGetAll(GroovyExprRedisKeys.TXN_VERSIONS_KEY);
        if (redisVersions == null || redisVersions.isEmpty()) {
            return;
        }

        // 2. 比对找出变化的交易码
        List<String> changedTxnCodes = new ArrayList<>();
        for (Map.Entry<Object, Object> entry : redisVersions.entrySet()) {
            String txnCode = entry.getKey().toString();
            long redisVersion = Long.parseLong(entry.getValue().toString().trim());
            Long localVersion = localVersions.get(txnCode);
            if (localVersion == null || localVersion != redisVersion) {
                changedTxnCodes.add(txnCode);
            }
        }

        // 3. 检查本地有但 Redis 已删除的交易码
        List<String> removedTxnCodes = new ArrayList<>();
        for (String localTxn : localVersions.keySet()) {
            if (!redisVersions.containsKey(localTxn)) {
                removedTxnCodes.add(localTxn);
            }
        }

        if (changedTxnCodes.isEmpty() && removedTxnCodes.isEmpty()) {
            return;
        }

        // 4. tryLock 防止并发重载
        if (!reloadLock.tryLock()) {
            logger.debug("[GroovyExprCache] 其他线程正在重载，放弃本轮轮询");
            return;
        }
        try {
            // 5. 加锁后双重检查
            for (Iterator<String> it = changedTxnCodes.iterator(); it.hasNext(); ) {
                String txnCode = it.next();
                long redisVersion = Long.parseLong(redisVersions.get(txnCode).toString().trim());
                Long localVer = localVersions.get(txnCode);
                if (localVer != null && localVer == redisVersion) {
                    it.remove();
                }
            }
            if (changedTxnCodes.isEmpty() && removedTxnCodes.isEmpty()) {
                return;
            }

            logger.info("[GroovyExprCache] 检测到 {} 个交易码变化，{} 个交易码删除，开始增量同步...",
                    changedTxnCodes.size(), removedTxnCodes.size());

            // 6. 同步变化的交易码（任一失败不影响其他交易码）
            for (String txnCode : changedTxnCodes) {
                try {
                    syncTxnFeatures(txnCode);
                } catch (Exception e) {
                    logger.error("[GroovyExprCache] 同步交易码 {} 异常", txnCode, e);
                }
            }

            // 7. 清理已删除的交易码
            for (String txnCode : removedTxnCodes) {
                removeFromLocalCache(txnCode);
            }
        } finally {
            reloadLock.unlock();
        }
    }

    // ==================== 核心加载逻辑 ====================

    /**
     * 全量重载入口（对外暴露，带防重复触发）。
     *
     * <p>关联：被 {@link #onApplicationReady} / {@link #triggerFullReload} 调用；
     * 委托 {@link #fullReloadInternal} 执行实际加载。
     */
    public void fullReload() {
        if (!reloadLock.tryLock()) {
            logger.debug("[GroovyExprCache] 其他线程正在重载，放弃竞争");
            return;
        }
        try {
            fullReloadInternal();
        } catch (Exception e) {
            logger.error("[GroovyExprCache] 全量重载异常", e);
        } finally {
            reloadLock.unlock();
        }
    }

    /**
     * 异步全量重载：提交到线程池执行，调用方立即返回
     */
    public void fullReloadAsync() {
        asyncReloadExecutor.submit(() -> {
            try {
                fullReload();
            } catch (Exception e) {
                logger.error("[GroovyExprCache] 异步全量重载异常", e);
            }
        });
        logger.info("[GroovyExprCache] 已提交异步全量重载任务");
    }

    /**
     * 全量重载：从 Redis 拉取所有数据到本地缓存（内部实现）
     *
     * <p>数据完整性策略：构建新的 newCache，所有交易码加载成功后整体原子替换；
     * 任何交易码加载失败仅跳过该交易码，不影响其他交易码；不影响旧缓存可用性。
     */
    private void fullReloadInternal() throws Exception {
        long startTime = System.currentTimeMillis();

        // 1. HGETALL 全局版本索引
        Map<Object, Object> redisVersions = redisUtils.hGetAll(GroovyExprRedisKeys.TXN_VERSIONS_KEY);
        if (redisVersions == null || redisVersions.isEmpty()) {
            logger.info("[GroovyExprCache] Redis 中无交易码数据，本地缓存清空");
            this.localCache = Collections.emptyMap();
            localVersions.clear();
            return;
        }

        // 2. 遍历每个交易码，逐个加载
        Map<String, GroovyTxnExpressionContext> newCache = new ConcurrentHashMap<>();
        Map<String, Long> newVersions = new ConcurrentHashMap<>();

        for (Map.Entry<Object, Object> entry : redisVersions.entrySet()) {
            String txnCode = entry.getKey().toString();
            long txnVersion = Long.parseLong(entry.getValue().toString().trim());

            GroovyTxnExpressionContext ctx = loadTxnFromRedis(txnCode, txnVersion);
            if (ctx != null) {
                newCache.put(txnCode, ctx);
                newVersions.put(txnCode, txnVersion);
            }
        }

        // 3. 原子替换
        this.localCache = newCache;
        this.localVersions.clear();
        this.localVersions.putAll(newVersions);

        long costMs = System.currentTimeMillis() - startTime;
        logger.info("[GroovyExprCache] 全量重载完成: 交易码数={}, 特征数={}, 耗时={}ms",
                newCache.size(), totalFeatureCount(), costMs);
    }

    // ==================== 查询接口 ====================

    /**
     * 根据交易码获取该交易码下的所有特征及编译后的表达式
     *
     * <p>优先走本地缓存，查询前检查单交易码版本号是否最新。
     *
     * @param txnCode 交易码
     * @return 交易码上下文；交易码不存在返回 null
     */
    public GroovyTxnExpressionContext getByTxnCode(String txnCode) {
        // 1. 检查单交易码版本号
        ensureFreshCache(txnCode);

        // 2. 本地缓存查找
        GroovyTxnExpressionContext cached = localCache.get(txnCode);
        if (cached != null) {
            return cached;
        }

        // 3. 本地未命中，Redis 实时加载
        logger.debug("[GroovyExprCache] 本地缓存未命中交易码 {}，从 Redis 获取", txnCode);
        return loadFromRedis(txnCode);
    }

    /**
     * 确保指定交易码的本地缓存是最新版本
     *
     * <p>策略：
     * <ul>
     *   <li>读 Redis 中该交易码的版本号</li>
     *   <li>与本地版本比对，不一致则触发单交易码精确同步</li>
     * </ul>
     */
    private void ensureFreshCache(String txnCode) {
        if (reloadLock.isLocked()) {
            return;
        }

        String redisVersionStr = redisUtils.hGet(GroovyExprRedisKeys.TXN_VERSIONS_KEY, txnCode);
        if (redisVersionStr == null || redisVersionStr.isEmpty()) {
            return;
        }

        long redisVersion = Long.parseLong(redisVersionStr.trim());
        Long localVer = localVersions.get(txnCode);
        if (localVer != null && localVer == redisVersion) {
            return;
        }

        // 版本不一致，触发单交易码同步
        if (reloadLock.tryLock()) {
            try {
                // 双重检查
                localVer = localVersions.get(txnCode);
                if (localVer == null || localVer != redisVersion) {
                    logger.info("[GroovyExprCache] 交易码 {} 版本变化: {} -> {}, 触发精确同步",
                            txnCode, localVer, redisVersion);
                    syncTxnFeatures(txnCode);
                }
            } catch (Exception e) {
                logger.error("[GroovyExprCache] 交易码 {} 精确同步异常", txnCode, e);
            } finally {
                reloadLock.unlock();
            }
        }
    }

    /**
     * 单交易码精确同步（核心热路径）。
     *
     * <p>只更新变化的特征，不动其他特征：
     * <ol>
     *   <li>HGETALL 交易码骨架（特征版本号 Map）</li>
     *   <li>与本地比对找出变化的特征</li>
     *   <li>MGET 只拉变化的特征源码</li>
     *   <li>重新编译 + 更新本地</li>
     * </ol>
     *
     * <p>为何用 MGET 而非逐条 GET：减少 Redis 往返次数，N 个特征只需 1 次 MGET。
     *
     * <p>为何用 String multiGet 而非 byte[] multiGet：Groovy 源码以 String 存储在 Redis，
     * {@code RedisUtils.multiGetBytes} 仅支持 byte[]，此处直接用 RedisTemplate.opsForValue().multiGet。
     *
     * <p>关联：被 {@link #pollAndReload} / {@link #ensureFreshCache} 调用；
     * 委托 {@link #compileFromSource} 编译源码；委托 {@link GroovyExprRedisKeys#featureDataKey} 构建 Key。
     */
    void syncTxnFeatures(String txnCode) throws Exception {
        long startTime = System.currentTimeMillis();

        // 1. 取交易码版本号
        String txnVersionStr = redisUtils.hGet(GroovyExprRedisKeys.TXN_VERSIONS_KEY, txnCode);
        if (txnVersionStr == null || txnVersionStr.isEmpty()) {
            // 交易码已被删除
            removeFromLocalCache(txnCode);
            return;
        }
        long txnVersion = Long.parseLong(txnVersionStr.trim());

        // 2. HGETALL 交易码骨架（特征版本号）
        Map<Object, Object> redisFeatureVersions = redisUtils.hGetAll(GroovyExprRedisKeys.txnSkeletonKey(txnCode));
        if (redisFeatureVersions == null || redisFeatureVersions.isEmpty()) {
            // 交易码下无特征，清理
            removeFromLocalCache(txnCode);
            return;
        }

        // 3. 取本地缓存
        GroovyTxnExpressionContext localCtx = localCache.get(txnCode);
        Map<String, GroovyTxnExpressionContext.FeatureVersionedExpression> localFeatures =
                (localCtx != null) ? localCtx.getFeatures() : Collections.<String, GroovyTxnExpressionContext.FeatureVersionedExpression>emptyMap();

        // 4. 比对找出变化的特征
        List<String> changedFeatures = new ArrayList<>();
        Set<String> redisFeatureCodes = new HashSet<>();
        for (Map.Entry<Object, Object> entry : redisFeatureVersions.entrySet()) {
            String featCode = entry.getKey().toString();
            long featVersion = Long.parseLong(entry.getValue().toString().trim());
            redisFeatureCodes.add(featCode);

            GroovyTxnExpressionContext.FeatureVersionedExpression localFeat = localFeatures.get(featCode);
            if (localFeat == null || localFeat.getVersion() != featVersion) {
                changedFeatures.add(featCode);
            }
        }

        // 5. 找出本地有但 Redis 已删除的特征
        List<String> removedFeatures = new ArrayList<>();
        for (String localFeatCode : localFeatures.keySet()) {
            if (!redisFeatureCodes.contains(localFeatCode)) {
                removedFeatures.add(localFeatCode);
            }
        }

        // 6. 按需拉变化的特征源码
        Map<String, GroovyTxnExpressionContext.FeatureVersionedExpression> newFeatureMap =
                new ConcurrentHashMap<>(localFeatures);

        if (!changedFeatures.isEmpty()) {
            List<String> dataKeys = changedFeatures.stream()
                    .map(fc -> GroovyExprRedisKeys.featureDataKey(txnCode, fc))
                    .collect(Collectors.toList());
            // 使用 String 类型的 multiGet（Groovy 源码以字符串存储）
            List<String> sourceList = redisUtils.getRedisTemplate().opsForValue().multiGet(dataKeys);

            for (int i = 0; i < changedFeatures.size(); i++) {
                String featCode = changedFeatures.get(i);
                String source = (sourceList != null) ? sourceList.get(i) : null;
                if (source == null || source.isEmpty()) {
                    logger.warn("[GroovyExprCache] 交易码 {} 特征 {} 的源码为空，跳过", txnCode, featCode);
                    continue;
                }
                try {
                    CompiledGroovyScript exp = compileFromSource(source);
                    long featVersion = Long.parseLong(
                            redisFeatureVersions.get(featCode).toString().trim());
                    newFeatureMap.put(featCode,
                            new GroovyTxnExpressionContext.FeatureVersionedExpression(featCode, featVersion, exp));
                } catch (Exception e) {
                    logger.warn("[GroovyExprCache] 编译交易码 {} 特征 {} 失败: {}",
                            txnCode, featCode, e.getMessage());
                }
            }
        }

        // 7. 清理已删除的特征
        for (String featCode : removedFeatures) {
            newFeatureMap.remove(featCode);
        }

        // 8. 构建新的上下文，原子替换
        GroovyTxnExpressionContext newCtx = new GroovyTxnExpressionContext(txnCode, txnVersion, newFeatureMap);

        // 复制当前 Map → put → 整体替换（不变性模式）
        Map<String, GroovyTxnExpressionContext> newCache = new ConcurrentHashMap<>(localCache);
        if (newFeatureMap.isEmpty()) {
            newCache.remove(txnCode);
            localVersions.remove(txnCode);
        } else {
            newCache.put(txnCode, newCtx);
            localVersions.put(txnCode, txnVersion);
        }
        this.localCache = newCache;

        long costMs = System.currentTimeMillis() - startTime;
        logger.info("[GroovyExprCache] 交易码 {} 精确同步完成: 变化特征={}, 删除特征={}, 总特征={}, 耗时={}ms",
                txnCode, changedFeatures.size(), removedFeatures.size(), newFeatureMap.size(), costMs);
    }

    /**
     * Redis 实时加载某交易码的数据（本地未命中时的降级路径）
     */
    private GroovyTxnExpressionContext loadFromRedis(String txnCode) {
        String txnVersionStr = redisUtils.hGet(GroovyExprRedisKeys.TXN_VERSIONS_KEY, txnCode);
        if (txnVersionStr == null || txnVersionStr.isEmpty()) {
            return null;
        }
        long txnVersion = Long.parseLong(txnVersionStr.trim());

        return loadTxnFromRedis(txnCode, txnVersion);
    }

    /**
     * 从 Redis 完整加载一个交易码的数据（用于全量加载和降级查询）
     */
    private GroovyTxnExpressionContext loadTxnFromRedis(String txnCode, long txnVersion) {
        // 1. HGETALL 交易码骨架
        Map<Object, Object> redisFeatureVersions = redisUtils.hGetAll(GroovyExprRedisKeys.txnSkeletonKey(txnCode));
        if (redisFeatureVersions == null || redisFeatureVersions.isEmpty()) {
            return null;
        }

        // 2. MGET 批量拉所有特征源码
        List<String> featureCodes = new ArrayList<>(redisFeatureVersions.size());
        for (Object key : redisFeatureVersions.keySet()) {
            featureCodes.add(key.toString());
        }

        List<String> dataKeys = featureCodes.stream()
                .map(fc -> GroovyExprRedisKeys.featureDataKey(txnCode, fc))
                .collect(Collectors.toList());
        // 使用 String 类型的 multiGet
        List<String> sourceList = redisUtils.getRedisTemplate().opsForValue().multiGet(dataKeys);

        // 3. 编译 + 组装
        Map<String, GroovyTxnExpressionContext.FeatureVersionedExpression> featureMap = new ConcurrentHashMap<>();
        for (int i = 0; i < featureCodes.size(); i++) {
            String featCode = featureCodes.get(i);
            String source = (sourceList != null) ? sourceList.get(i) : null;
            if (source == null || source.isEmpty()) {
                logger.warn("[GroovyExprCache] 交易码 {} 特征 {} 的源码为空，跳过", txnCode, featCode);
                continue;
            }
            try {
                CompiledGroovyScript exp = compileFromSource(source);
                long featVersion = Long.parseLong(
                        redisFeatureVersions.get(featCode).toString().trim());
                featureMap.put(featCode,
                        new GroovyTxnExpressionContext.FeatureVersionedExpression(featCode, featVersion, exp));
            } catch (Exception e) {
                logger.warn("[GroovyExprCache] 编译交易码 {} 特征 {} 失败: {}",
                        txnCode, featCode, e.getMessage());
            }
        }

        if (featureMap.isEmpty()) {
            return null;
        }

        return new GroovyTxnExpressionContext(txnCode, txnVersion, featureMap);
    }

    /**
     * 从本地缓存移除交易码
     */
    private void removeFromLocalCache(String txnCode) {
        Map<String, GroovyTxnExpressionContext> newCache = new ConcurrentHashMap<>(localCache);
        newCache.remove(txnCode);
        this.localCache = newCache;
        localVersions.remove(txnCode);
        logger.info("[GroovyExprCache] 交易码 {} 已从本地缓存移除", txnCode);
    }

    /**
     * 获取所有交易码
     */
    public Set<String> getAllTxnCodes() {
        return Collections.unmodifiableSet(localCache.keySet());
    }

    /**
     * 获取缓存状态信息
     */
    public CacheStatus getStatus() {
        return new CacheStatus(localCache.size(), totalFeatureCount(), initialized);
    }

    // ==================== 事务操作 ====================

    /**
     * Redis 事务中执行多个操作
     */
    private void executeInTransaction(List<Consumer<RedisConnection>> operations, String operationName) {
        try {
            redisUtils.executeInTransaction(operations);
            logger.info("[GroovyExprCache] {} 操作成功", operationName);
        } catch (Exception e) {
            logger.error("[GroovyExprCache] {} 操作失败", operationName, e);
            throw e;
        }
    }

    // ==================== 写入操作 ====================

    /**
     * 新增交易码下的特征表达式
     *
     * <p>Redis 事务：
     * <pre>
     *   SET     groovy-expr:txn:data:{txnCode}:{featureCode}  source
     *   HSET    groovy-expr:txn:{txnCode}                    {featureCode}  1
     *   HINCRBY groovy-expr:txn:versions                     {txnCode}      1
     * </pre>
     *
     * @param txnCode     交易码
     * @param featureCode 特征编码
     * @param expression  Groovy 表达式源码
     */
    public void putFeature(String txnCode, String featureCode, String expression) throws Exception {
        // 编译验证（同时缓存编译结果）
        String source = compileAndStore(expression);

        String dataKey = GroovyExprRedisKeys.featureDataKey(txnCode, featureCode);
        String skeletonKey = GroovyExprRedisKeys.txnSkeletonKey(txnCode);

        byte[] dataKeyBytes = dataKey.getBytes(StandardCharsets.UTF_8);
        byte[] sourceBytes = source.getBytes(StandardCharsets.UTF_8);
        byte[] skeletonKeyBytes = skeletonKey.getBytes(StandardCharsets.UTF_8);
        byte[] featureCodeBytes = featureCode.getBytes(StandardCharsets.UTF_8);
        byte[] txnCodeBytes = txnCode.getBytes(StandardCharsets.UTF_8);
        byte[] versionsKeyBytes = GroovyExprRedisKeys.TXN_VERSIONS_KEY.getBytes(StandardCharsets.UTF_8);

        List<Consumer<RedisConnection>> operations = Arrays.asList(
                // SET 特征源码
                connection -> connection.set(dataKeyBytes, sourceBytes),
                // HSET 交易码骨架：featureCode → 1（初始版本号）
                connection -> connection.hSet(skeletonKeyBytes, featureCodeBytes, "1".getBytes(StandardCharsets.UTF_8)),
                // HINCRBY 全局版本索引：txnCode 版本号 +1
                connection -> connection.hIncrBy(versionsKeyBytes, txnCodeBytes, 1)
        );

        executeInTransaction(operations,
                String.format("putFeature(txn=%s, feat=%s)", txnCode, featureCode));
    }

    /**
     * 更新某特征的表达式内容
     *
     * <p>Redis 事务：
     * <pre>
     *   SET     groovy-expr:txn:data:{txnCode}:{featureCode}  source
     *   HINCRBY groovy-expr:txn:{txnCode}                    {featureCode}  1
     *   HINCRBY groovy-expr:txn:versions                     {txnCode}      1
     * </pre>
     *
     * @param txnCode     交易码
     * @param featureCode 特征编码
     * @param expression  Groovy 表达式源码
     */
    public void updateFeature(String txnCode, String featureCode, String expression) throws Exception {
        String source = compileAndStore(expression);

        String dataKey = GroovyExprRedisKeys.featureDataKey(txnCode, featureCode);
        String skeletonKey = GroovyExprRedisKeys.txnSkeletonKey(txnCode);

        byte[] dataKeyBytes = dataKey.getBytes(StandardCharsets.UTF_8);
        byte[] sourceBytes = source.getBytes(StandardCharsets.UTF_8);
        byte[] skeletonKeyBytes = skeletonKey.getBytes(StandardCharsets.UTF_8);
        byte[] featureCodeBytes = featureCode.getBytes(StandardCharsets.UTF_8);
        byte[] txnCodeBytes = txnCode.getBytes(StandardCharsets.UTF_8);
        byte[] versionsKeyBytes = GroovyExprRedisKeys.TXN_VERSIONS_KEY.getBytes(StandardCharsets.UTF_8);

        List<Consumer<RedisConnection>> operations = Arrays.asList(
                // SET 特征源码
                connection -> connection.set(dataKeyBytes, sourceBytes),
                // HINCRBY 交易码骨架：featureCode 版本号 +1
                connection -> connection.hIncrBy(skeletonKeyBytes, featureCodeBytes, 1),
                // HINCRBY 全局版本索引：txnCode 版本号 +1
                connection -> connection.hIncrBy(versionsKeyBytes, txnCodeBytes, 1)
        );

        executeInTransaction(operations,
                String.format("updateFeature(txn=%s, feat=%s)", txnCode, featureCode));
    }

    /**
     * 从交易码下移除特征（同时删除特征源码）
     *
     * <p>Redis 事务：
     * <pre>
     *   DEL     groovy-expr:txn:data:{txnCode}:{featureCode}
     *   HDEL    groovy-expr:txn:{txnCode}                    {featureCode}
     *   HINCRBY groovy-expr:txn:versions                     {txnCode}      1
     * </pre>
     *
     * <p>事务后判断：如果交易码下无特征了，清理交易码 Hash 和版本索引
     *
     * @param txnCode     交易码
     * @param featureCode 特征编码
     */
    public void removeFeature(String txnCode, String featureCode) {
        String dataKey = GroovyExprRedisKeys.featureDataKey(txnCode, featureCode);
        String skeletonKey = GroovyExprRedisKeys.txnSkeletonKey(txnCode);

        byte[] dataKeyBytes = dataKey.getBytes(StandardCharsets.UTF_8);
        byte[] skeletonKeyBytes = skeletonKey.getBytes(StandardCharsets.UTF_8);
        byte[] featureCodeBytes = featureCode.getBytes(StandardCharsets.UTF_8);
        byte[] txnCodeBytes = txnCode.getBytes(StandardCharsets.UTF_8);
        byte[] versionsKeyBytes = GroovyExprRedisKeys.TXN_VERSIONS_KEY.getBytes(StandardCharsets.UTF_8);

        List<Consumer<RedisConnection>> operations = Arrays.asList(
                // DEL 特征源码
                connection -> connection.del(dataKeyBytes),
                // HDEL 交易码骨架
                connection -> connection.hDel(skeletonKeyBytes, featureCodeBytes),
                // HINCRBY 全局版本索引
                connection -> connection.hIncrBy(versionsKeyBytes, txnCodeBytes, 1)
        );

        executeInTransaction(operations,
                String.format("removeFeature(txn=%s, feat=%s)", txnCode, featureCode));

        // 事务后判断：交易码下是否还有特征
        Long remaining = redisUtils.hLen(skeletonKey);
        if (remaining != null && remaining == 0) {
            // 清理空交易码
            redisUtils.del(skeletonKey);
            redisUtils.hDel(GroovyExprRedisKeys.TXN_VERSIONS_KEY, txnCode);
            logger.info("[GroovyExprCache] 交易码 {} 下无特征，已清理空交易码", txnCode);
        }
    }

    /**
     * 删除整个交易码（含所有特征源码）
     *
     * @param txnCode 交易码
     */
    public void removeTxn(String txnCode) {
        String skeletonKey = GroovyExprRedisKeys.txnSkeletonKey(txnCode);

        // 1. 拿到所有特征编码
        Set<Object> featureCodes = redisUtils.hKeys(skeletonKey);

        // 2. 组装事务操作
        List<Consumer<RedisConnection>> operations = new ArrayList<>();
        byte[] skeletonKeyBytes = skeletonKey.getBytes(StandardCharsets.UTF_8);
        byte[] txnCodeBytes = txnCode.getBytes(StandardCharsets.UTF_8);
        byte[] versionsKeyBytes = GroovyExprRedisKeys.TXN_VERSIONS_KEY.getBytes(StandardCharsets.UTF_8);

        // DEL 交易码骨架
        operations.add(connection -> connection.del(skeletonKeyBytes));
        // HDEL 全局版本索引
        operations.add(connection -> connection.hDel(versionsKeyBytes, txnCodeBytes));
        // DEL 所有特征源码
        if (featureCodes != null) {
            for (Object featCode : featureCodes) {
                String dataKey = GroovyExprRedisKeys.featureDataKey(txnCode, featCode.toString());
                byte[] dataKeyBytes = dataKey.getBytes(StandardCharsets.UTF_8);
                operations.add(connection -> connection.del(dataKeyBytes));
            }
        }

        executeInTransaction(operations, String.format("removeTxn(txn=%s)", txnCode));
    }

    /**
     * 手动触发一次全量重载（供外部调用）
     */
    public void triggerFullReload() {
        fullReload();
    }

    // ==================== 编译工具方法 ====================

    /**
     * 编译表达式并返回源码字符串（用于存储到 Redis）。
     *
     * <p>对应 Aviator 版本的 compileAndSerialize，但 Groovy 无法序列化 Class，
     * 因此仅做编译验证，存储时直接保存源码字符串。
     *
     * <p>为何编译后再存源码：编译验证可在写入前发现语法错误，避免 Redis 中残留不可用的脏数据；
     * 编译结果会自动缓存到 {@link GroovyExpressionEngine#compileCache}，后续读取时可直接复用。
     *
     * <p>关联：被 {@link #putFeature} / {@link #updateFeature} 调用；
     * 与 {@link #compileFromSource} 互为逆操作——前者编译+存源码，后者读源码+编译。
     */
    String compileAndStore(String expression) throws Exception {
        try {
            // 编译验证（语法错误会抛出 GroovyCompileException）
            engine.compile(expression);
            return expression;
        } catch (Exception e) {
            logger.error("[GroovyExprCache] 编译表达式失败: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 从源码重新编译为 CompiledGroovyScript。
     *
     * <p>对应 Aviator 版本的 deserialize，从 Redis 读取源码后重新编译。
     * 编译结果会自动缓存到 {@link GroovyExpressionEngine#compileCache}，相同源码不会重复编译。
     *
     * <p>关联：被 {@link #syncTxnFeatures} / {@link #loadTxnFromRedis} 调用；
     * 与 {@link #compileAndStore} 互为逆操作。
     */
    CompiledGroovyScript compileFromSource(String source) {
        return engine.compile(source);
    }

    // ==================== 内部数据结构 ====================

    /** 统计本地缓存中所有交易码下的特征总数 */
    private int totalFeatureCount() {
        return localCache.values().stream().mapToInt(ctx -> ctx.getFeatures().size()).sum();
    }

    /**
     * 缓存状态信息
     */
    public static class CacheStatus {
        private int txnCount;
        private int featureCount;
        private boolean initialized;

        public CacheStatus() {
        }

        public CacheStatus(int txnCount, int featureCount, boolean initialized) {
            this.txnCount = txnCount;
            this.featureCount = featureCount;
            this.initialized = initialized;
        }

        public int getTxnCount() {
            return txnCount;
        }

        public void setTxnCount(int txnCount) {
            this.txnCount = txnCount;
        }

        public int getFeatureCount() {
            return featureCount;
        }

        public void setFeatureCount(int featureCount) {
            this.featureCount = featureCount;
        }

        public boolean isInitialized() {
            return initialized;
        }

        public void setInitialized(boolean initialized) {
            this.initialized = initialized;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CacheStatus that = (CacheStatus) o;
            return txnCount == that.txnCount
                    && featureCount == that.featureCount
                    && initialized == that.initialized;
        }

        @Override
        public int hashCode() {
            int result = Integer.hashCode(txnCount);
            result = 31 * result + Integer.hashCode(featureCount);
            result = 31 * result + Boolean.hashCode(initialized);
            return result;
        }

        @Override
        public String toString() {
            return "CacheStatus{"
                    + "txnCount=" + txnCount
                    + ", featureCount=" + featureCount
                    + ", initialized=" + initialized
                    + '}';
        }
    }
}
