package com.businesslogic.grpc;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.QueryParams;
import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.health.HealthServicesRequest;
import com.ecwid.consul.v1.health.model.HealthService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consul 集群服务实例监听 *
 * 核心职责 * 1. 同时监听 N Consul 集群，每个集群独Blocking Query
 * 2. 聚合所有集群的健康实例，提getAllHealthyInstances() 统一视图
 * 3. 检测实例变化（ADDED / REMOVED / UPDATED），派发事件给监听器
 * 4. 故障隔离：单集群异常不影响其他集 *
 * ConsulServiceWatcher 关系 * - 这是独立的新组件，与原有 ConsulServiceWatcher 并行运行
 * - 不修改、不依赖 ConsulServiceWatcher
 * - 原有单集群逻辑保持不变，新场景直接 @Autowired 本类即可
 *
 * 核心方法 * - getAllHealthyInstances()          获取所有集群的健康实例
 * - getHealthyInstances(clusterName)  获取指定集群的健康实 * - getClusterStatus()                获取所有集群的运行状 * - addListener(listener)             订阅实例变更事件
 */
@Component
public class MultiClusterConsulServiceWatcher {

    private static final Logger logger = LoggerFactory.getLogger(MultiClusterConsulServiceWatcher.class);

    private final MultiClusterConsulProperties properties;
    private final List<ClusterWatcherTask> watcherTasks = new CopyOnWriteArrayList<>();
    private final ExecutorService listenerExecutor;

    /**
     * 全局实例表：globalId ClusteredServiceInstance
     * 来自不同集群的相instanceId 不会冲突（globalId 包含 clusterName     */
    private final ConcurrentMap<String, ClusteredServiceInstance> globalInstances = new ConcurrentHashMap<>();

    /**
     * 集群状态表：clusterName ClusterState
     */
    private final ConcurrentMap<String, ClusterState> clusterStates = new ConcurrentHashMap<>();

    /**
     * 变更监听器列     */
    private final List<InstanceChangeListener> listeners = new CopyOnWriteArrayList<>();

    @Autowired
    public MultiClusterConsulServiceWatcher(MultiClusterConsulProperties properties) {
        this.properties = properties;
        this.listenerExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "consul-multi-listener");
            t.setDaemon(true);
            return t;
        });
    }

    @PostConstruct
    public void init() {
        List<MultiClusterConsulProperties.ClusterConfig> clusterConfigs = resolveClusters();
        logger.info("Consul 集群监听器初始化: {} 个集 service={}",
                clusterConfigs.size(), properties.getServiceName());
        for (MultiClusterConsulProperties.ClusterConfig config : clusterConfigs) {
            startWatcher(config);
        }
    }

    /**
     * 解析集群配置：clusters 为空时降级为单集群（consul.host/port     */
    private List<MultiClusterConsulProperties.ClusterConfig> resolveClusters() {
        List<MultiClusterConsulProperties.ClusterConfig> configured = properties.getClusters();
        if (configured == null || configured.isEmpty()) {
            MultiClusterConsulProperties.ClusterConfig fallback = new MultiClusterConsulProperties.ClusterConfig();
            fallback.setName("default");
            fallback.setHost(properties.getHost());
            fallback.setPort(properties.getPort());
            fallback.setDatacenter("default");
            fallback.setEnabled(true);
            logger.info("未配置多集群，降级为单集群模 {}:{}", fallback.getHost(), fallback.getPort());
            return Collections.singletonList(fallback);
        }
        return configured.stream()
                .filter(MultiClusterConsulProperties.ClusterConfig::isEnabled)
                .collect(Collectors.toList());
    }

    private void startWatcher(MultiClusterConsulProperties.ClusterConfig config) {
        ClusterWatcherTask task = new ClusterWatcherTask(config);
        watcherTasks.add(task);
        task.start();
    }

    @PreDestroy
    public void shutdown() {
        logger.info("Consul 集群监听器关闭");
        for (ClusterWatcherTask task : watcherTasks) {
            task.stop();
        }
        listenerExecutor.shutdownNow();
    }

    // ======================== 公开 API ========================

    /**
     * 获取多集群中所有健康的 Pod 实例
     *
     * 这是核心方法：返回聚合后的全量视     * - 已通过 passing=true 过滤（只有健康实例）
     * - 已跨集群聚合（一Pod 在两集群都注册会返回两次，因globalId 不同     * - 返回不可变副本，外部修改不影响内部状     *
     * @return 所有集群的健康实例列表
     */
    public List<ClusteredServiceInstance> getAllHealthyInstances() {
        return Collections.unmodifiableList(new ArrayList<>(globalInstances.values()));
    }

    /**
     * 获取指定集群的健康实     *
     * @param clusterName 集群名（cluster-a     * @return 该集群下的健康实例列表（空列表表示集群未启用/无实不可达）
     */
    public List<ClusteredServiceInstance> getHealthyInstances(String clusterName) {
        if (clusterName == null) return Collections.emptyList();
        return globalInstances.values().stream()
                .filter(i -> clusterName.equals(i.getClusterName()))
                .collect(Collectors.toList());
    }

    /**
     * 获取各集群的运行状态（健康度、最后响应时间、当index     */
    public Map<String, ClusterState> getClusterStatus() {
        return Collections.unmodifiableMap(new TreeMap<>(clusterStates));
    }

    /**
     * 注册实例变更监听器（异步派发，监听器慢不影响主循环）
     */
    public void addListener(InstanceChangeListener listener) {
        if (listener != null) {
            listeners.add(listener);
            //主动派发一次当前快照，便于监听器建立初始视
            for (ClusteredServiceInstance instance : globalInstances.values()) {
                listenerExecutor.execute(() -> listener.onChange(InstanceChangeEvent.added(
                        instance.getClusterName(), instance, 0L)));
            }
        }
    }

    /**
     * 移除监听     */
    public void removeListener(InstanceChangeListener listener) {
        listeners.remove(listener);
    }

    /**
     * 全局健康实例数量
     */
    public int getTotalInstanceCount() {
        return globalInstances.size();
    }

    // ======================== 集群回调（由 ClusterWatcherTask 调用========================

    /**
     * ClusterWatcherTask 上报新数据时被调     * 负责：Diff、更新全局表、派发事     */
    private void onClusterInstancesUpdated(String clusterName,
                                            long consulIndex,
                                            List<ClusteredServiceInstance> newInstances) {
        // 构造本次新数据globalId 集合
        Set<String> newGlobalIds = newInstances.stream()
                .map(ClusteredServiceInstance::globalId)
                .collect(Collectors.toSet());

        // 找出本集群在全局表中的旧实例
        Map<String, ClusteredServiceInstance> oldOfCluster = globalInstances.values().stream()
                .filter(i -> clusterName.equals(i.getClusterName()))
                .collect(Collectors.toMap(ClusteredServiceInstance::globalId, i -> i));

        //1. 检REMOVED（在 old 不在 new
        for (Map.Entry<String, ClusteredServiceInstance> entry : oldOfCluster.entrySet()) {
            if (!newGlobalIds.contains(entry.getKey())) {
                globalInstances.remove(entry.getKey());
                fireEvent(InstanceChangeEvent.removed(clusterName, entry.getValue(), consulIndex));
            }
        }

        // 2. 检ADDED / UPDATED
        for (ClusteredServiceInstance fresh : newInstances) {
            ClusteredServiceInstance old = oldOfCluster.get(fresh.globalId());
            if (old == null) {
                globalInstances.put(fresh.globalId(), fresh);
                fireEvent(InstanceChangeEvent.added(clusterName, fresh, consulIndex));
            } else {
                //健康状态变化：拆成 REMOVED + ADDED（语义更清晰
                if (old.isHealthy() != fresh.isHealthy()) {
                    globalInstances.put(fresh.globalId(), fresh);
                    fireEvent(InstanceChangeEvent.removed(clusterName, old, consulIndex));
                    fireEvent(InstanceChangeEvent.added(clusterName, fresh, consulIndex));
                } else if (isInstanceChanged(old, fresh)) {
                    //其它字段变化（host/port/tags
                    globalInstances.put(fresh.globalId(), fresh);
                    fireEvent(InstanceChangeEvent.updated(clusterName, fresh, consulIndex));
                }
                // 完全没变：不更新不发事件（避免无意义刷新
            }
        }

        //3. 更新集群状
        ClusterState state = clusterStates.computeIfAbsent(clusterName, k -> new ClusterState());
        state.setLastIndex(consulIndex);
        state.setLastUpdateTime(System.currentTimeMillis());
        state.setReachable(true);
        state.setInstanceCount(newInstances.size());
    }

    /**
     * 集群断开时调用：踢出该集群的所有实例，标记 FAILED
     */
    private void onClusterDisconnected(String clusterName, Throwable cause) {
        List<ClusteredServiceInstance> toRemove = globalInstances.values().stream()
                .filter(i -> clusterName.equals(i.getClusterName()))
                .collect(Collectors.toList());
        for (ClusteredServiceInstance instance : toRemove) {
            globalInstances.remove(instance.globalId());
            fireEvent(InstanceChangeEvent.removed(clusterName, instance, 0L));
        }
        ClusterState state = clusterStates.computeIfAbsent(clusterName, k -> new ClusterState());
        state.setReachable(false);
        state.setLastError(cause == null ? "unknown" : cause.getClass().getSimpleName() + ": " + cause.getMessage());
        logger.warn("集群 {} 已断开，移除该集群 {} 个实例", clusterName, toRemove.size());
    }

    /**
     * 集群从断开恢复时调     */
    private void onClusterReconnected(String clusterName) {
        ClusterState state = clusterStates.computeIfAbsent(clusterName, k -> new ClusterState());
        state.setReachable(true);
        state.setLastError(null);
        logger.info("集群 {} 已恢复", clusterName);
    }

    private boolean isInstanceChanged(ClusteredServiceInstance a, ClusteredServiceInstance b) {
        if (a.getPort() != b.getPort()) return true;
        if (!Objects.equals(a.getHost(), b.getHost())) return true;
        if (!Objects.equals(a.getTags(), b.getTags())) return true;
        return false;
    }

    private void fireEvent(InstanceChangeEvent event) {
        if (listeners.isEmpty()) return;
        for (InstanceChangeListener listener : listeners) {
            listenerExecutor.execute(() -> {
                try {
                    listener.onChange(event);
                } catch (Exception e) {
                    logger.error("实例变更监听器异 cluster={}, type={}",
                            event.getClusterName(), event.getType(), e);
                }
            });
        }
    }

    // ======================== 内部类：单集群监听任========================

    /**
     * 单集Blocking Query 任务
     *
     * 每个集群一个独立线程：
     * - 独立ConsulClient
     * - 独立lastIndex
     * - 独立处理异常（不影响其他集群     * - 退避重+ 重置 index 防止丢事     */
    private class ClusterWatcherTask {

        private final MultiClusterConsulProperties.ClusterConfig config;
        private final Thread worker;
        private volatile boolean running = true;
        private volatile long lastIndex = 0;
        private ConsulClient consulClient;
        private final AtomicLong reconnectDelayMs = new AtomicLong(1000);
        private boolean wasReachable = true;

        ClusterWatcherTask(MultiClusterConsulProperties.ClusterConfig config) {
            this.config = config;
            this.worker = new Thread(this::runWatchLoop,
                    "consul-multi-watcher-" + config.getName());
            this.worker.setDaemon(true);
        }

        void start() {
            logger.info("启动集群监听: name={}, {}:{}, dc={}",
                    config.getName(), config.getHost(), config.getPort(), config.getDatacenter());
            worker.start();
        }

        void stop() {
            running = false;
            worker.interrupt();
        }

        private void runWatchLoop() {
            consulClient = new ConsulClient(config.getHost(), config.getPort());
            ClusterState clusterState = clusterStates.computeIfAbsent(config.getName(), k -> new ClusterState());
            clusterState.setHost(String.format("%s:%d", config.getHost(), config.getPort()));
            clusterState.setDatacenter(config.getDatacenter());

            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    HealthServicesRequest request = HealthServicesRequest.newBuilder()
                            .setQueryParams(new QueryParams(properties.getWatchTimeout(), lastIndex))
                            .setPassing(true)
                            .build();

                    Response<List<HealthService>> response = consulClient.getHealthServices(
                            properties.getServiceName(), request);

                    Long newIndex = response.getConsulIndex();
                    if (newIndex == null) {
                        continue;
                    }

                    // 标记重连成功
                    if (!wasReachable) {
                        wasReachable = true;
                        reconnectDelayMs.set(1000);
                        onClusterReconnected(config.getName());
                    }

                    lastIndex = newIndex;
                    List<ClusteredServiceInstance> newInstances = response.getValue().stream()
                            .map(this::toClusteredInstance)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());

                    onClusterInstancesUpdated(config.getName(), newIndex, newInstances);

                } catch (Exception e) {
                    logger.error("Consul 监听异常: cluster={}, 将退避重试", config.getName(), e);

                    if (wasReachable) {
                        wasReachable = false;
                        onClusterDisconnected(config.getName(), e);
                    }

                    //重连前重index，避免漏掉中间事
                    lastIndex = 0;

                    try {
                        Thread.sleep(reconnectDelayMs.get());
                        reconnectDelayMs.updateAndGet(v -> Math.min(v * 2, 30_000));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }

        private ClusteredServiceInstance toClusteredInstance(HealthService hs) {
            try {
                ClusteredServiceInstance si = new ClusteredServiceInstance();
                si.setClusterName(config.getName());
                si.setDatacenter(config.getDatacenter());
                si.setServiceName(hs.getService().getService());
                si.setInstanceId(hs.getService().getId());
                String address = hs.getService().getAddress();
                si.setHost(address == null || address.isEmpty() ? "127.0.0.1" : address);
                si.setPort(hs.getService().getPort());
                si.setHealthy(true);
                List<String> tags = hs.getService().getTags();
                si.setTags(tags != null ? new ArrayList<>(tags) : Collections.emptyList());
                return si;
            } catch (Exception e) {
                logger.warn("转换 HealthService 失败: cluster={}, error={}", config.getName(), e.getMessage());
                return null;
            }
        }
    }

    // ======================== 公开类型定义 ========================

    /**
     * 实例变更事件
     */
    public static class InstanceChangeEvent {
        public enum Type { ADDED, REMOVED, UPDATED }

        private String clusterName;
        private Type type;
        private ClusteredServiceInstance instance;
        private long consulIndex;
        private long timestamp = System.currentTimeMillis();

        public String getClusterName() {
            return clusterName;
        }

        public void setClusterName(String clusterName) {
            this.clusterName = clusterName;
        }

        public Type getType() {
            return type;
        }

        public void setType(Type type) {
            this.type = type;
        }

        public ClusteredServiceInstance getInstance() {
            return instance;
        }

        public void setInstance(ClusteredServiceInstance instance) {
            this.instance = instance;
        }

        public long getConsulIndex() {
            return consulIndex;
        }

        public void setConsulIndex(long consulIndex) {
            this.consulIndex = consulIndex;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            InstanceChangeEvent that = (InstanceChangeEvent) o;
            return consulIndex == that.consulIndex &&
                    timestamp == that.timestamp &&
                    Objects.equals(clusterName, that.clusterName) &&
                    type == that.type &&
                    Objects.equals(instance, that.instance);
        }

        @Override
        public int hashCode() {
            return Objects.hash(clusterName, type, instance, consulIndex, timestamp);
        }

        @Override
        public String toString() {
            return "InstanceChangeEvent{" +
                    "clusterName='" + clusterName + '\'' +
                    ", type=" + type +
                    ", instance=" + instance +
                    ", consulIndex=" + consulIndex +
                    ", timestamp=" + timestamp +
                    '}';
        }

        public static InstanceChangeEvent added(String cluster, ClusteredServiceInstance inst, long idx) {
            InstanceChangeEvent e = new InstanceChangeEvent();
            e.clusterName = cluster; e.type = Type.ADDED; e.instance = inst; e.consulIndex = idx;
            return e;
        }
        public static InstanceChangeEvent removed(String cluster, ClusteredServiceInstance inst, long idx) {
            InstanceChangeEvent e = new InstanceChangeEvent();
            e.clusterName = cluster; e.type = Type.REMOVED; e.instance = inst; e.consulIndex = idx;
            return e;
        }
        public static InstanceChangeEvent updated(String cluster, ClusteredServiceInstance inst, long idx) {
            InstanceChangeEvent e = new InstanceChangeEvent();
            e.clusterName = cluster; e.type = Type.UPDATED; e.instance = inst; e.consulIndex = idx;
            return e;
        }
    }

    /**
     * 实例变更监听     */
    @FunctionalInterface
    public interface InstanceChangeListener {
        void onChange(InstanceChangeEvent event);
    }

    /**
     * 集群运行状     */
    public static class ClusterState {
        private String host;
        private String datacenter;
        private boolean reachable;
        private long lastIndex;
        private long lastUpdateTime;
        private int instanceCount;
        private String lastError;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public String getDatacenter() {
            return datacenter;
        }

        public void setDatacenter(String datacenter) {
            this.datacenter = datacenter;
        }

        public boolean isReachable() {
            return reachable;
        }

        public void setReachable(boolean reachable) {
            this.reachable = reachable;
        }

        public long getLastIndex() {
            return lastIndex;
        }

        public void setLastIndex(long lastIndex) {
            this.lastIndex = lastIndex;
        }

        public long getLastUpdateTime() {
            return lastUpdateTime;
        }

        public void setLastUpdateTime(long lastUpdateTime) {
            this.lastUpdateTime = lastUpdateTime;
        }

        public int getInstanceCount() {
            return instanceCount;
        }

        public void setInstanceCount(int instanceCount) {
            this.instanceCount = instanceCount;
        }

        public String getLastError() {
            return lastError;
        }

        public void setLastError(String lastError) {
            this.lastError = lastError;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ClusterState that = (ClusterState) o;
            return reachable == that.reachable &&
                    lastIndex == that.lastIndex &&
                    lastUpdateTime == that.lastUpdateTime &&
                    instanceCount == that.instanceCount &&
                    Objects.equals(host, that.host) &&
                    Objects.equals(datacenter, that.datacenter) &&
                    Objects.equals(lastError, that.lastError);
        }

        @Override
        public int hashCode() {
            return Objects.hash(host, datacenter, reachable, lastIndex, lastUpdateTime, instanceCount, lastError);
        }

        @Override
        public String toString() {
            return "ClusterState{" +
                    "host='" + host + '\'' +
                    ", datacenter='" + datacenter + '\'' +
                    ", reachable=" + reachable +
                    ", lastIndex=" + lastIndex +
                    ", lastUpdateTime=" + lastUpdateTime +
                    ", instanceCount=" + instanceCount +
                    ", lastError='" + lastError + '\'' +
                    '}';
        }
    }
}
