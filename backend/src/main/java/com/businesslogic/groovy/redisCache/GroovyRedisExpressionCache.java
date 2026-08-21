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
 * Groovy 表达式 Redis 缓存服务，按源报文编号（sourceNo）建立本地编译缓存。
 *
 * <p>Redis 存储结构：</p>
 * <pre>
 *   groovy-expr:source:global-version          String   全局整体更新版本号
 *   groovy-expr:source:versions                Hash     sourceNo -> sourceVersion
 *   groovy-expr:source:script:{sourceNo}       String   源报文对应的合并后整体脚本
 * </pre>
 *
 * <p>同步策略：启动时全量预热；运行期轮询全局版本号，对变化的源报文立即重新编译。
 * 查询阶段只读本地缓存，不触发 Redis 读取和 Groovy 编译。</p>
 */
@Component
public class GroovyRedisExpressionCache {

    private static final Logger logger = LoggerFactory.getLogger(GroovyRedisExpressionCache.class);

    /** 全量预热或轮询同步时，单批读取的脚本数量 */
    private static final int SCRIPT_BATCH_SIZE = 20;

    @Autowired
    private RedisUtils redisUtils;

    private final GroovyExpressionEngine engine;

    /**
     * 本地编译缓存：sourceNo -> 源报文编译条目。
     *
     * <p>全量预热和轮询同步时采用整体替换，读取操作无锁。</p>
     */
    private volatile Map<String, GroovySourceScriptEntry> localCache = Collections.emptyMap();

    /** 本地已观察到的全局更新版本号 */
    private volatile long localGlobalVersion = Long.MIN_VALUE;

    /** 是否已完成首次全量预热 */
    private volatile boolean ready = false;

    /** 是否正在执行全量预热 */
    private volatile boolean preheating = false;

    /** 重载锁，避免全量预热、轮询同步和手动重载并发执行 */
    private final ReentrantLock reloadLock = new ReentrantLock();

    /** 异步预热/重载线程池 */
    private final ExecutorService asyncReloadExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "groovy-source-cache-async-reload");
        t.setDaemon(true);
        return t;
    });

    public GroovyRedisExpressionCache() {
        this.engine = GroovyExecutor.getEngine();
    }

    @PostConstruct
    public void init() {
        logger.info("[GroovySourceCache] 初始化完成，Groovy 引擎就绪");
    }

    /**
     * 应用启动后异步全量预热，避免阻塞 Spring 启动流程。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        fullReloadAsync();
    }

    /**
     * 定时轮询：检查全局版本号，增量预热变化的源报文脚本。
     */
    @Scheduled(fixedDelayString = "${groovy.expr.cache.poll-interval-ms:10000}",
               initialDelayString = "${groovy.expr.cache.poll-initial-delay-ms:15000}")
    public void scheduledPoll() {
        if (!ready) {
            return;
        }
        try {
            pollAndReload();
        } catch (Exception e) {
            logger.error("[GroovySourceCache] 定时轮询异常", e);
        }
    }

    /**
     * 查询源报文对应的编译脚本。
     *
     * @param sourceNo 源报文编号
     * @return 本地编译条目；未就绪或不存在返回 null
     */
    public GroovySourceScriptEntry getBySourceNo(String sourceNo) {
        if (!ready) {
            return null;
        }
        return localCache.get(sourceNo);
    }

    /**
     * 获取当前本地缓存中的所有源报文编号。
     */
    public Set<String> getAllSourceNos() {
        return Collections.unmodifiableSet(localCache.keySet());
    }

    /**
     * 手动触发一次全量预热。
     */
    public void triggerFullReload() {
        fullReload();
    }

    /**
     * 异步触发一次全量预热。
     */
    public void fullReloadAsync() {
        asyncReloadExecutor.submit(() -> {
            try {
                fullReload();
            } catch (Exception e) {
                logger.error("[GroovySourceCache] 异步全量预热异常", e);
            }
        });
    }

    /**
     * 全量预热入口。
     */
    public void fullReload() {
        if (!reloadLock.tryLock()) {
            logger.debug("[GroovySourceCache] 已有预热/重载任务在执行，跳过本次全量预热");
            return;
        }
        try {
            fullReloadInternal();
        } catch (Exception e) {
            logger.error("[GroovySourceCache] 全量预热异常", e);
        } finally {
            reloadLock.unlock();
        }
    }

    /**
     * 全量预热实现：读取全局版本号、源报文版本列表和所有脚本，逐条编译后整体替换本地缓存。
     */
    private void fullReloadInternal() {
        long startTime = System.currentTimeMillis();
        preheating = true;
        try {
            String globalVersionStr = redisUtils.get(GroovyExprRedisKeys.GLOBAL_VERSION_KEY);
            if (globalVersionStr == null || globalVersionStr.isEmpty()) {
                this.localCache = Collections.emptyMap();
                this.localGlobalVersion = Long.MIN_VALUE;
                this.ready = true;
                logger.info("[GroovySourceCache] Redis 中无源报文数据，本地缓存已清空");
                return;
            }

            long globalVersion = Long.parseLong(globalVersionStr.trim());
            Map<Object, Object> redisVersions = redisUtils.hGetAll(GroovyExprRedisKeys.SOURCE_VERSIONS_KEY);

            Map<String, GroovySourceScriptEntry> newCache = new ConcurrentHashMap<>();
            if (redisVersions != null && !redisVersions.isEmpty()) {
                List<String> sourceNos = new ArrayList<>(redisVersions.size());
                for (Object key : redisVersions.keySet()) {
                    sourceNos.add(key.toString());
                }

                List<String> scripts = multiGetSourceScripts(sourceNos);
                for (int i = 0; i < sourceNos.size(); i++) {
                    String sourceNo = sourceNos.get(i);
                    String source = scripts.get(i);
                    if (source == null || source.isEmpty()) {
                        logger.warn("[GroovySourceCache] 源报文 {} 的脚本为空，首次预热跳过", sourceNo);
                        continue;
                    }

                    try {
                        long version = Long.parseLong(redisVersions.get(sourceNo).toString().trim());
                        CompiledGroovyScript compiled = engine.compile(source);
                        newCache.put(sourceNo, new GroovySourceScriptEntry(sourceNo, version, compiled));
                    } catch (Exception e) {
                        logger.warn("[GroovySourceCache] 源报文 {} 编译失败，首次预热跳过: {}",
                                sourceNo, e.getMessage());
                    }
                }
            }

            this.localCache = newCache;
            this.localGlobalVersion = globalVersion;
            this.ready = true;

            long costMs = System.currentTimeMillis() - startTime;
            logger.info("[GroovySourceCache] 全量预热完成: sourceCount={}, globalVersion={}, 耗时={}ms",
                    newCache.size(), globalVersion, costMs);
        } finally {
            preheating = false;
        }
    }

    /**
     * 轮询同步实现。
     */
    void pollAndReload() {
        String globalVersionStr = redisUtils.get(GroovyExprRedisKeys.GLOBAL_VERSION_KEY);
        if (globalVersionStr == null || globalVersionStr.isEmpty()) {
            return;
        }
        long newGlobalVersion = Long.parseLong(globalVersionStr.trim());
        if (newGlobalVersion == localGlobalVersion) {
            return;
        }

        if (!reloadLock.tryLock()) {
            logger.debug("[GroovySourceCache] 其他线程正在重载，跳过本轮轮询");
            return;
        }

        try {
            globalVersionStr = redisUtils.get(GroovyExprRedisKeys.GLOBAL_VERSION_KEY);
            if (globalVersionStr == null || globalVersionStr.isEmpty()) {
                return;
            }
            newGlobalVersion = Long.parseLong(globalVersionStr.trim());
            if (newGlobalVersion == localGlobalVersion) {
                return;
            }

            Map<Object, Object> redisVersions = redisUtils.hGetAll(GroovyExprRedisKeys.SOURCE_VERSIONS_KEY);
            Map<String, GroovySourceScriptEntry> current = localCache;

            List<String> changedSourceNos = new ArrayList<>();
            Set<String> redisSourceNos = new HashSet<>();

            if (redisVersions != null) {
                for (Map.Entry<Object, Object> entry : redisVersions.entrySet()) {
                    String sourceNo = entry.getKey().toString();
                    long redisVersion = Long.parseLong(entry.getValue().toString().trim());
                    redisSourceNos.add(sourceNo);

                    GroovySourceScriptEntry localEntry = current.get(sourceNo);
                    if (localEntry == null || localEntry.getVersion() != redisVersion) {
                        changedSourceNos.add(sourceNo);
                    }
                }
            }

            List<String> removedSourceNos = new ArrayList<>();
            for (String sourceNo : current.keySet()) {
                if (!redisSourceNos.contains(sourceNo)) {
                    removedSourceNos.add(sourceNo);
                }
            }

            if (changedSourceNos.isEmpty() && removedSourceNos.isEmpty()) {
                this.localGlobalVersion = newGlobalVersion;
                return;
            }

            Map<String, GroovySourceScriptEntry> newCache = new ConcurrentHashMap<>(current);
            if (!changedSourceNos.isEmpty()) {
                List<String> scripts = multiGetSourceScripts(changedSourceNos);
                for (int i = 0; i < changedSourceNos.size(); i++) {
                    String sourceNo = changedSourceNos.get(i);
                    String source = scripts.get(i);
                    if (source == null || source.isEmpty()) {
                        logger.warn("[GroovySourceCache] 源报文 {} 的脚本为空，保持旧缓存不变", sourceNo);
                        continue;
                    }

                    try {
                        long version = Long.parseLong(redisVersions.get(sourceNo).toString().trim());
                        CompiledGroovyScript compiled = engine.compile(source);
                        newCache.put(sourceNo, new GroovySourceScriptEntry(sourceNo, version, compiled));
                        logger.info("[GroovySourceCache] 源报文 {} 预热更新成功, version={}", sourceNo, version);
                    } catch (Exception e) {
                        logger.warn("[GroovySourceCache] 源报文 {} 编译失败，保持旧缓存不变: {}",
                                sourceNo, e.getMessage());
                    }
                }
            }

            for (String sourceNo : removedSourceNos) {
                newCache.remove(sourceNo);
                logger.info("[GroovySourceCache] 源报文 {} 已从本地缓存移除", sourceNo);
            }

            this.localCache = newCache;
            this.localGlobalVersion = newGlobalVersion;
        } finally {
            reloadLock.unlock();
        }
    }

    /**
     * 发布或更新源报文脚本。
     *
     * @param sourceNo 源报文编号
     * @param script   合并后的整体 Groovy 脚本
     */
    public void publishSourceScript(String sourceNo, String script) throws Exception {
        CompiledGroovyScript compiled = compileForCache(script);

        byte[] scriptKeyBytes = GroovyExprRedisKeys.sourceScriptKey(sourceNo).getBytes(StandardCharsets.UTF_8);
        byte[] sourceBytes = script.getBytes(StandardCharsets.UTF_8);
        byte[] versionsKeyBytes = GroovyExprRedisKeys.SOURCE_VERSIONS_KEY.getBytes(StandardCharsets.UTF_8);
        byte[] sourceNoBytes = sourceNo.getBytes(StandardCharsets.UTF_8);
        byte[] globalVersionKeyBytes = GroovyExprRedisKeys.GLOBAL_VERSION_KEY.getBytes(StandardCharsets.UTF_8);

        List<Consumer<RedisConnection>> operations = Arrays.asList(
                connection -> connection.set(scriptKeyBytes, sourceBytes),
                connection -> connection.hIncrBy(versionsKeyBytes, sourceNoBytes, 1),
                connection -> connection.incr(globalVersionKeyBytes)
        );

        executeInTransaction(operations, String.format("publishSourceScript(sourceNo=%s)", sourceNo));
        refreshLocalEntryAfterPublish(sourceNo, compiled);
    }

    /**
     * 更新源报文脚本。
     */
    public void updateSourceScript(String sourceNo, String script) throws Exception {
        publishSourceScript(sourceNo, script);
    }

    /**
     * 删除源报文脚本。
     */
    public void removeSourceScript(String sourceNo) {
        byte[] scriptKeyBytes = GroovyExprRedisKeys.sourceScriptKey(sourceNo).getBytes(StandardCharsets.UTF_8);
        byte[] versionsKeyBytes = GroovyExprRedisKeys.SOURCE_VERSIONS_KEY.getBytes(StandardCharsets.UTF_8);
        byte[] sourceNoBytes = sourceNo.getBytes(StandardCharsets.UTF_8);
        byte[] globalVersionKeyBytes = GroovyExprRedisKeys.GLOBAL_VERSION_KEY.getBytes(StandardCharsets.UTF_8);

        List<Consumer<RedisConnection>> operations = Arrays.asList(
                connection -> connection.del(scriptKeyBytes),
                connection -> connection.hDel(versionsKeyBytes, sourceNoBytes),
                connection -> connection.incr(globalVersionKeyBytes)
        );

        executeInTransaction(operations, String.format("removeSourceScript(sourceNo=%s)", sourceNo));
        removeLocalEntry(sourceNo);
    }

    /**
     * 编译校验后返回原始脚本，用于 Redis 存储。
     */
    private CompiledGroovyScript compileForCache(String expression) throws Exception {
        try {
            return engine.compile(expression);
        } catch (Exception e) {
            logger.error("[GroovySourceCache] 脚本编译失败: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 批量读取源报文脚本，保持入参与返回值顺序一致。
     */
    private void refreshLocalEntryAfterPublish(String sourceNo, CompiledGroovyScript compiled) {
        reloadLock.lock();
        try {
            String versionStr = redisUtils.hGet(GroovyExprRedisKeys.SOURCE_VERSIONS_KEY, sourceNo);
            if (versionStr == null || versionStr.isEmpty()) {
                logger.warn("[GroovySourceCache] 鏃犳硶鑾峰彇婧愭姤鏂?{} 鐨勭増鏈彿锛屾湰鍦扮紦瀛樻湭鏇存柊", sourceNo);
                return;
            }

            long version = Long.parseLong(versionStr.trim());
            Map<String, GroovySourceScriptEntry> newCache = new ConcurrentHashMap<>(localCache);
            newCache.put(sourceNo, new GroovySourceScriptEntry(sourceNo, version, compiled));
            this.localCache = newCache;
            refreshLocalGlobalVersion();
        } catch (Exception e) {
            logger.error("[GroovySourceCache] 鏇存柊鏈湴缂栬瘧缂撳瓨澶辫触, sourceNo={}", sourceNo, e);
        } finally {
            reloadLock.unlock();
        }
    }

    private void removeLocalEntry(String sourceNo) {
        reloadLock.lock();
        try {
            Map<String, GroovySourceScriptEntry> newCache = new ConcurrentHashMap<>(localCache);
            newCache.remove(sourceNo);
            this.localCache = newCache;
            refreshLocalGlobalVersion();
        } finally {
            reloadLock.unlock();
        }
    }

    private void refreshLocalGlobalVersion() {
        String globalVersionStr = redisUtils.get(GroovyExprRedisKeys.GLOBAL_VERSION_KEY);
        if (globalVersionStr == null || globalVersionStr.isEmpty()) {
            return;
        }
        try {
            this.localGlobalVersion = Long.parseLong(globalVersionStr.trim());
        } catch (Exception e) {
            logger.warn("[GroovySourceCache] 鏃犳硶瑙ｆ瀽鍏ㄥ眬鐗堟湰鍙? value={}", globalVersionStr, e);
        }
    }

    private List<String> multiGetSourceScripts(List<String> sourceNos) {
        List<String> result = new ArrayList<>(sourceNos.size());
        for (int start = 0; start < sourceNos.size(); start += SCRIPT_BATCH_SIZE) {
            int end = Math.min(start + SCRIPT_BATCH_SIZE, sourceNos.size());
            List<String> batch = sourceNos.subList(start, end);
            List<String> keys = batch.stream()
                    .map(GroovyExprRedisKeys::sourceScriptKey)
                    .collect(Collectors.toList());

            List<String> values = redisUtils.getRedisTemplate().opsForValue().multiGet(keys);
            for (int i = 0; i < batch.size(); i++) {
                result.add(values != null ? values.get(i) : null);
            }
        }
        return result;
    }

    /**
     * 在 Redis 事务中执行多个操作。
     */
    private void executeInTransaction(List<Consumer<RedisConnection>> operations, String operationName) {
        try {
            redisUtils.executeInTransaction(operations);
            logger.info("[GroovySourceCache] {} 操作成功", operationName);
        } catch (Exception e) {
            logger.error("[GroovySourceCache] {} 操作失败", operationName, e);
            throw e;
        }
    }

    /**
     * 获取缓存状态。
     */
    public CacheStatus getStatus() {
        return new CacheStatus(localCache.size(), ready, preheating, localGlobalVersion);
    }

    /**
     * 缓存状态信息。
     */
    public static class CacheStatus {
        private int sourceCount;
        private boolean ready;
        private boolean preheating;
        private long localGlobalVersion;

        public CacheStatus() {
        }

        public CacheStatus(int sourceCount, boolean ready, boolean preheating, long localGlobalVersion) {
            this.sourceCount = sourceCount;
            this.ready = ready;
            this.preheating = preheating;
            this.localGlobalVersion = localGlobalVersion;
        }

        public int getSourceCount() {
            return sourceCount;
        }

        public void setSourceCount(int sourceCount) {
            this.sourceCount = sourceCount;
        }

        public boolean isReady() {
            return ready;
        }

        public void setReady(boolean ready) {
            this.ready = ready;
        }

        public boolean isPreheating() {
            return preheating;
        }

        public void setPreheating(boolean preheating) {
            this.preheating = preheating;
        }

        public long getLocalGlobalVersion() {
            return localGlobalVersion;
        }

        public void setLocalGlobalVersion(long localGlobalVersion) {
            this.localGlobalVersion = localGlobalVersion;
        }

        @Override
        public String toString() {
            return "CacheStatus{"
                    + "sourceCount=" + sourceCount
                    + ", ready=" + ready
                    + ", preheating=" + preheating
                    + ", localGlobalVersion=" + localGlobalVersion
                    + '}';
        }
    }
}
