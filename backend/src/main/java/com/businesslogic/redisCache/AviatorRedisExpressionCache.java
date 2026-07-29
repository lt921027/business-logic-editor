package com.businesslogic.redisCache;

import com.businesslogic.util.RedisUtils;
import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Expression;
import com.googlecode.aviator.Options;
import com.googlecode.aviator.serialize.AviatorObjectInputStream;
import com.googlecode.aviator.serialize.AviatorObjectOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Aviator 表达式 Redis 缓存服务（特征级别版本号方案）
 *
 * <p>Redis 存储结构（3 个 Key）：
 * <pre>
 *   expr:txn:versions                       Hash    全局交易码版本索引
 *     field: {txnCode}        value: {txnVersion}
 *
 *   expr:txn:{txnCode}                      Hash    交易码骨架（仅特征版本号）
 *     field: {featureCode}    value: {featureVersion}
 *
 *   expr:txn:data:{txnCode}:{featureCode}   String  特征字节（原始 byte[]，5-10KB）
 * </pre>
 *
 * <p>同步机制：
 * <ul>
 *   <li>启动时：全量加载所有交易码</li>
 *   <li>运行时：定时轮询交易码版本号，增量同步变化的交易码（仅重载变化的特征）</li>
 *   <li>查询时：按需检查单交易码版本号，不一致则精确同步</li>
 * </ul>
 */
@Component
public class AviatorRedisExpressionCache {

    private static final Logger logger = LoggerFactory.getLogger(AviatorRedisExpressionCache.class);

    @Autowired
    private RedisUtils redisUtils;

    private final AviatorEvaluatorInstance evaluator;

    // ==================== 本地缓存 ====================

    /** 交易码 → 上下文（含版本号 + 特征Map），volatile 保证可见性 */
    private volatile Map<String, TxnExpressionContext> localCache = Collections.emptyMap();

    /** 交易码 → 本地版本号（用于定时轮询比对） */
    private final ConcurrentHashMap<String, Long> localVersions = new ConcurrentHashMap<>();

    /** 缓存是否已初始化 */
    private volatile boolean initialized = false;

    /** 重载锁（tryLock 不排队，拿不到直接放弃） */
    private final ReentrantLock reloadLock = new ReentrantLock();

    /** 异步重载线程池（单线程，保证串行执行） */
    private final ExecutorService asyncReloadExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "expr-cache-async-reload");
        t.setDaemon(true);
        return t;
    });

    public AviatorRedisExpressionCache() {
        this.evaluator = AviatorEvaluator.getInstance();
    }

    @PostConstruct
    public void init() {
        evaluator.setOption(Options.SERIALIZABLE, true);
        logger.info("[ExprCache] 初始化完成，Aviator version={}, SERIALIZABLE={}",
                AviatorEvaluator.VERSION, true);
    }

    // ==================== 启动全量加载 ====================

    /**
     * 启动后执行首次全量加载
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            fullReload();
            initialized = true;
            logger.info("[ExprCache] 首次全量加载完成，交易码数={}, 特征数={}",
                    localCache.size(), totalFeatureCount());
        } catch (Exception e) {
            logger.error("[ExprCache] 首次全量加载失败", e);
        }
    }

    // ==================== 定时轮询 ====================

    /**
     * 定时轮询：每 10 秒检查交易码版本号，增量同步变化的交易码
     */
    @Scheduled(fixedDelayString = "${expr.cache.poll-interval-ms:10000}",
               initialDelayString = "${expr.cache.poll-initial-delay-ms:15000}")
    public void scheduledPoll() {
        if (!initialized) {
            return;
        }
        try {
            pollAndReload();
        } catch (Exception e) {
            logger.error("[ExprCache] 定时轮询异常", e);
        }
    }

    /**
     * 轮询：HGETALL 全局版本索引 → 与本地比对 → 增量同步变化的交易码
     */
    void pollAndReload() {
        // 1. HGETALL 拿所有交易码版本号
        Map<Object, Object> redisVersions = redisUtils.hGetAll(ExprRedisKeys.TXN_VERSIONS_KEY);
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
            logger.debug("[ExprCache] 其他线程正在重载，放弃本轮轮询");
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

            logger.info("[ExprCache] 检测到 {} 个交易码变化，{} 个交易码删除，开始增量同步...",
                    changedTxnCodes.size(), removedTxnCodes.size());

            // 6. 同步变化的交易码
            for (String txnCode : changedTxnCodes) {
                try {
                    syncTxnFeatures(txnCode);
                } catch (Exception e) {
                    logger.error("[ExprCache] 同步交易码 {} 异常", txnCode, e);
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
     * 全量重载入口（对外暴露，带防重复触发）
     */
    public void fullReload() {
        if (!reloadLock.tryLock()) {
            logger.debug("[ExprCache] 其他线程正在重载，放弃竞争");
            return;
        }
        try {
            fullReloadInternal();
        } catch (Exception e) {
            logger.error("[ExprCache] 全量重载异常", e);
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
                logger.error("[ExprCache] 异步全量重载异常", e);
            }
        });
        logger.info("[ExprCache] 已提交异步全量重载任务");
    }

    /**
     * 全量重载：从 Redis 拉取所有数据到本地缓存（内部实现）
     */
    private void fullReloadInternal() throws Exception {
        long startTime = System.currentTimeMillis();

        // 1. HGETALL 全局版本索引
        Map<Object, Object> redisVersions = redisUtils.hGetAll(ExprRedisKeys.TXN_VERSIONS_KEY);
        if (redisVersions == null || redisVersions.isEmpty()) {
            logger.info("[ExprCache] Redis 中无交易码数据，本地缓存清空");
            this.localCache = Collections.emptyMap();
            localVersions.clear();
            return;
        }

        // 2. 遍历每个交易码，逐个加载
        Map<String, TxnExpressionContext> newCache = new ConcurrentHashMap<>();
        Map<String, Long> newVersions = new ConcurrentHashMap<>();

        for (Map.Entry<Object, Object> entry : redisVersions.entrySet()) {
            String txnCode = entry.getKey().toString();
            long txnVersion = Long.parseLong(entry.getValue().toString().trim());

            TxnExpressionContext ctx = loadTxnFromRedis(txnCode, txnVersion);
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
        logger.info("[ExprCache] 全量重载完成: 交易码数={}, 特征数={}, 耗时={}ms",
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
    public TxnExpressionContext getByTxnCode(String txnCode) {
        // 1. 检查单交易码版本号
        ensureFreshCache(txnCode);

        // 2. 本地缓存查找
        TxnExpressionContext cached = localCache.get(txnCode);
        if (cached != null) {
            return cached;
        }

        // 3. 本地未命中，Redis 实时加载
        logger.debug("[ExprCache] 本地缓存未命中交易码 {}，从 Redis 获取", txnCode);
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

        String redisVersionStr = redisUtils.hGet(ExprRedisKeys.TXN_VERSIONS_KEY, txnCode);
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
                    logger.info("[ExprCache] 交易码 {} 版本变化: {} -> {}, 触发精确同步",
                            txnCode, localVer, redisVersion);
                    syncTxnFeatures(txnCode);
                }
            } catch (Exception e) {
                logger.error("[ExprCache] 交易码 {} 精确同步异常", txnCode, e);
            } finally {
                reloadLock.unlock();
            }
        }
    }

    /**
     * 单交易码精确同步（核心热路径）
     *
     * <p>只更新变化的特征，不动其他特征：
     * <ol>
     *   <li>HGETALL 交易码骨架（特征版本号 Map）</li>
     *   <li>与本地比对找出变化的特征</li>
     *   <li>MGET 只拉变化的特征字节</li>
     *   <li>反序列化 + 更新本地</li>
     * </ol>
     */
    void syncTxnFeatures(String txnCode) throws Exception {
        long startTime = System.currentTimeMillis();

        // 1. 取交易码版本号
        String txnVersionStr = redisUtils.hGet(ExprRedisKeys.TXN_VERSIONS_KEY, txnCode);
        if (txnVersionStr == null || txnVersionStr.isEmpty()) {
            // 交易码已被删除
            removeFromLocalCache(txnCode);
            return;
        }
        long txnVersion = Long.parseLong(txnVersionStr.trim());

        // 2. HGETALL 交易码骨架（特征版本号）
        Map<Object, Object> redisFeatureVersions = redisUtils.hGetAll(ExprRedisKeys.txnSkeletonKey(txnCode));
        if (redisFeatureVersions == null || redisFeatureVersions.isEmpty()) {
            // 交易码下无特征，清理
            removeFromLocalCache(txnCode);
            return;
        }

        // 3. 取本地缓存
        TxnExpressionContext localCtx = localCache.get(txnCode);
        Map<String, TxnExpressionContext.FeatureVersionedExpression> localFeatures =
                (localCtx != null) ? localCtx.getFeatures() : Collections.emptyMap();

        // 4. 比对找出变化的特征
        List<String> changedFeatures = new ArrayList<>();
        Set<String> redisFeatureCodes = new HashSet<>();
        for (Map.Entry<Object, Object> entry : redisFeatureVersions.entrySet()) {
            String featCode = entry.getKey().toString();
            long featVersion = Long.parseLong(entry.getValue().toString().trim());
            redisFeatureCodes.add(featCode);

            TxnExpressionContext.FeatureVersionedExpression localFeat = localFeatures.get(featCode);
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

        // 6. 按需拉变化的特征字节
        Map<String, TxnExpressionContext.FeatureVersionedExpression> newFeatureMap =
                new ConcurrentHashMap<>(localFeatures);

        if (!changedFeatures.isEmpty()) {
            List<String> dataKeys = changedFeatures.stream()
                    .map(fc -> ExprRedisKeys.featureDataKey(txnCode, fc))
                    .collect(Collectors.toList());
            List<byte[]> bytesList = redisUtils.multiGetBytes(dataKeys);

            for (int i = 0; i < changedFeatures.size(); i++) {
                String featCode = changedFeatures.get(i);
                byte[] bytes = (bytesList != null) ? bytesList.get(i) : null;
                if (bytes == null || bytes.length == 0) {
                    logger.warn("[ExprCache] 交易码 {} 特征 {} 的字节为空，跳过", txnCode, featCode);
                    continue;
                }
                try {
                    Expression exp = deserialize(bytes);
                    long featVersion = Long.parseLong(
                            redisFeatureVersions.get(featCode).toString().trim());
                    newFeatureMap.put(featCode,
                            new TxnExpressionContext.FeatureVersionedExpression(featCode, featVersion, exp));
                } catch (Exception e) {
                    logger.warn("[ExprCache] 反序列化交易码 {} 特征 {} 失败: {}",
                            txnCode, featCode, e.getMessage());
                }
            }
        }

        // 7. 清理已删除的特征
        for (String featCode : removedFeatures) {
            newFeatureMap.remove(featCode);
        }

        // 8. 构建新的上下文，原子替换
        TxnExpressionContext newCtx = new TxnExpressionContext(txnCode, txnVersion, newFeatureMap);

        // 复制当前 Map → put → 整体替换（不变性模式）
        Map<String, TxnExpressionContext> newCache = new ConcurrentHashMap<>(localCache);
        if (newFeatureMap.isEmpty()) {
            newCache.remove(txnCode);
            localVersions.remove(txnCode);
        } else {
            newCache.put(txnCode, newCtx);
            localVersions.put(txnCode, txnVersion);
        }
        this.localCache = newCache;

        long costMs = System.currentTimeMillis() - startTime;
        logger.info("[ExprCache] 交易码 {} 精确同步完成: 变化特征={}, 删除特征={}, 总特征={}, 耗时={}ms",
                txnCode, changedFeatures.size(), removedFeatures.size(), newFeatureMap.size(), costMs);
    }

    /**
     * Redis 实时加载某交易码的数据（本地未命中时的降级路径）
     */
    private TxnExpressionContext loadFromRedis(String txnCode) {
        String txnVersionStr = redisUtils.hGet(ExprRedisKeys.TXN_VERSIONS_KEY, txnCode);
        if (txnVersionStr == null || txnVersionStr.isEmpty()) {
            return null;
        }
        long txnVersion = Long.parseLong(txnVersionStr.trim());

        return loadTxnFromRedis(txnCode, txnVersion);
    }

    /**
     * 从 Redis 完整加载一个交易码的数据（用于全量加载和降级查询）
     */
    private TxnExpressionContext loadTxnFromRedis(String txnCode, long txnVersion) {
        // 1. HGETALL 交易码骨架
        Map<Object, Object> redisFeatureVersions = redisUtils.hGetAll(ExprRedisKeys.txnSkeletonKey(txnCode));
        if (redisFeatureVersions == null || redisFeatureVersions.isEmpty()) {
            return null;
        }

        // 2. MGET 批量拉所有特征字节
        List<String> featureCodes = new ArrayList<>(redisFeatureVersions.size());
        for (Object key : redisFeatureVersions.keySet()) {
            featureCodes.add(key.toString());
        }

        List<String> dataKeys = featureCodes.stream()
                .map(fc -> ExprRedisKeys.featureDataKey(txnCode, fc))
                .collect(Collectors.toList());
        List<byte[]> bytesList = redisUtils.multiGetBytes(dataKeys);

        // 3. 反序列化 + 组装
        Map<String, TxnExpressionContext.FeatureVersionedExpression> featureMap = new ConcurrentHashMap<>();
        for (int i = 0; i < featureCodes.size(); i++) {
            String featCode = featureCodes.get(i);
            byte[] bytes = (bytesList != null) ? bytesList.get(i) : null;
            if (bytes == null || bytes.length == 0) {
                logger.warn("[ExprCache] 交易码 {} 特征 {} 的字节为空，跳过", txnCode, featCode);
                continue;
            }
            try {
                Expression exp = deserialize(bytes);
                long featVersion = Long.parseLong(
                        redisFeatureVersions.get(featCode).toString().trim());
                featureMap.put(featCode,
                        new TxnExpressionContext.FeatureVersionedExpression(featCode, featVersion, exp));
            } catch (Exception e) {
                logger.warn("[ExprCache] 反序列化交易码 {} 特征 {} 失败: {}",
                        txnCode, featCode, e.getMessage());
            }
        }

        if (featureMap.isEmpty()) {
            return null;
        }

        return new TxnExpressionContext(txnCode, txnVersion, featureMap);
    }

    /**
     * 从本地缓存移除交易码
     */
    private void removeFromLocalCache(String txnCode) {
        Map<String, TxnExpressionContext> newCache = new ConcurrentHashMap<>(localCache);
        newCache.remove(txnCode);
        this.localCache = newCache;
        localVersions.remove(txnCode);
        logger.info("[ExprCache] 交易码 {} 已从本地缓存移除", txnCode);
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
            logger.info("[ExprCache] {} 操作成功", operationName);
        } catch (Exception e) {
            logger.error("[ExprCache] {} 操作失败", operationName, e);
            throw e;
        }
    }

    // ==================== 写入操作 ====================

    /**
     * 新增交易码下的特征表达式
     *
     * <p>Redis 事务：
     * <pre>
     *   SET   expr:txn:data:{txnCode}:{featureCode}  bytes
     *   HSET  expr:txn:{txnCode}  {featureCode}  1
     *   HINCRBY expr:txn:versions  {txnCode}  1
     * </pre>
     *
     * @param txnCode     交易码
     * @param featureCode 特征编码
     * @param expression  Aviator 表达式源码
     */
    public void putFeature(String txnCode, String featureCode, String expression) throws Exception {
        byte[] bytes = compileAndSerialize(expression);

        String dataKey = ExprRedisKeys.featureDataKey(txnCode, featureCode);
        String skeletonKey = ExprRedisKeys.txnSkeletonKey(txnCode);

        List<Consumer<RedisConnection>> operations = Arrays.asList(
                // SET 特征字节
                connection -> connection.set(dataKey.getBytes(), bytes),
                // HSET 交易码骨架：featureCode → 1（初始版本号）
                connection -> connection.hSet(skeletonKey.getBytes(), featureCode.getBytes(), "1".getBytes()),
                // HINCRBY 全局版本索引：txnCode 版本号 +1
                connection -> connection.hIncrBy(ExprRedisKeys.TXN_VERSIONS_KEY.getBytes(), txnCode.getBytes(), 1)
        );

        executeInTransaction(operations,
                String.format("putFeature(txn=%s, feat=%s)", txnCode, featureCode));
    }

    /**
     * 更新某特征的表达式内容
     *
     * <p>Redis 事务：
     * <pre>
     *   SET     expr:txn:data:{txnCode}:{featureCode}  bytes
     *   HINCRBY expr:txn:{txnCode}  {featureCode}  1
     *   HINCRBY expr:txn:versions   {txnCode}       1
     * </pre>
     *
     * @param txnCode     交易码
     * @param featureCode 特征编码
     * @param expression  Aviator 表达式源码
     */
    public void updateFeature(String txnCode, String featureCode, String expression) throws Exception {
        byte[] bytes = compileAndSerialize(expression);

        String dataKey = ExprRedisKeys.featureDataKey(txnCode, featureCode);
        String skeletonKey = ExprRedisKeys.txnSkeletonKey(txnCode);

        List<Consumer<RedisConnection>> operations = Arrays.asList(
                // SET 特征字节
                connection -> connection.set(dataKey.getBytes(), bytes),
                // HINCRBY 交易码骨架：featureCode 版本号 +1
                connection -> connection.hIncrBy(skeletonKey.getBytes(), featureCode.getBytes(), 1),
                // HINCRBY 全局版本索引：txnCode 版本号 +1
                connection -> connection.hIncrBy(ExprRedisKeys.TXN_VERSIONS_KEY.getBytes(), txnCode.getBytes(), 1)
        );

        executeInTransaction(operations,
                String.format("updateFeature(txn=%s, feat=%s)", txnCode, featureCode));
    }

    /**
     * 从交易码下移除特征（同时删除特征字节）
     *
     * <p>Redis 事务：
     * <pre>
     *   DEL     expr:txn:data:{txnCode}:{featureCode}
     *   HDEL    expr:txn:{txnCode}  {featureCode}
     *   HINCRBY expr:txn:versions   {txnCode}  1
     * </pre>
     *
     * <p>事务后判断：如果交易码下无特征了，清理交易码 Hash 和版本索引
     *
     * @param txnCode     交易码
     * @param featureCode 特征编码
     */
    public void removeFeature(String txnCode, String featureCode) {
        String dataKey = ExprRedisKeys.featureDataKey(txnCode, featureCode);
        String skeletonKey = ExprRedisKeys.txnSkeletonKey(txnCode);

        List<Consumer<RedisConnection>> operations = Arrays.asList(
                // DEL 特征字节
                connection -> connection.del(dataKey.getBytes()),
                // HDEL 交易码骨架
                connection -> connection.hDel(skeletonKey.getBytes(), featureCode.getBytes()),
                // HINCRBY 全局版本索引
                connection -> connection.hIncrBy(ExprRedisKeys.TXN_VERSIONS_KEY.getBytes(), txnCode.getBytes(), 1)
        );

        executeInTransaction(operations,
                String.format("removeFeature(txn=%s, feat=%s)", txnCode, featureCode));

        // 事务后判断：交易码下是否还有特征
        Long remaining = redisUtils.hLen(skeletonKey);
        if (remaining != null && remaining == 0) {
            // 清理空交易码
            redisUtils.del(skeletonKey);
            redisUtils.hDel(ExprRedisKeys.TXN_VERSIONS_KEY, txnCode);
            logger.info("[ExprCache] 交易码 {} 下无特征，已清理空交易码", txnCode);
        }
    }

    /**
     * 删除整个交易码（含所有特征字节）
     *
     * @param txnCode 交易码
     */
    public void removeTxn(String txnCode) {
        String skeletonKey = ExprRedisKeys.txnSkeletonKey(txnCode);

        // 1. 拿到所有特征编码
        Set<Object> featureCodes = redisUtils.hKeys(skeletonKey);

        // 2. 组装事务操作
        List<Consumer<RedisConnection>> operations = new ArrayList<>();
        // DEL 交易码骨架
        operations.add(connection -> connection.del(skeletonKey.getBytes()));
        // HDEL 全局版本索引
        operations.add(connection -> connection.hDel(
                ExprRedisKeys.TXN_VERSIONS_KEY.getBytes(), txnCode.getBytes()));
        // DEL 所有特征字节
        if (featureCodes != null) {
            for (Object featCode : featureCodes) {
                String dataKey = ExprRedisKeys.featureDataKey(txnCode, featCode.toString());
                operations.add(connection -> connection.del(dataKey.getBytes()));
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

    // ==================== 序列化工具方法 ====================

    /**
     * 编译表达式并序列化为字节数组
     */
    byte[] compileAndSerialize(String expression) throws Exception {
        Expression compiled = evaluator.compile(expression);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (AviatorObjectOutputStream oos = new AviatorObjectOutputStream(baos)) {
            oos.writeObject(compiled);
        }
        return baos.toByteArray();
    }

    /**
     * 反序列化字节数组为 Expression
     */
    Expression deserialize(byte[] bytes) throws Exception {
        try (AviatorObjectInputStream ois = new AviatorObjectInputStream(
                new ByteArrayInputStream(bytes), evaluator)) {
            return (Expression) ois.readObject();
        }
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
