package com.businesslogic.grpc;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.health.HealthServicesRequest;
import com.ecwid.consul.v1.health.model.HealthService;
import com.ecwid.consul.v1.QueryParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consul 服务实例监听器（基于 Watch 机制 * 
 * 核心职责 * - 通过 Consul Blocking Query 机制实时感知目标服务的实例变 * - 维护最新的服务实例列表，供 gRPC 广播使用
 * - 自动检测实例上下线，实时更新本地缓 * 
 * Consul Blocking Query 工作原理 * 1. 客户端发送请求时携带 lastIndex（上次响应的 Consul-Index * 2. Consul 服务端在实例状态未变化时阻塞请求（最wait 时间 * 3. 当实例状态变化或超时后，Consul 返回新数+ 新的 Consul-Index
 * 4. 这种方式比纯轮询更高效，减少无效请求
 * 
 * 设计说明 * - 使用 CopyOnWriteArrayList 保证线程安全（读多写少场景）
 * - 5 秒执行一Watch 检查（Consul 阻塞最5 秒）
 * - 仅在实例列表变化时更新本地缓存，避免无效刷新
 * 
 * 配置项（application.yml）：
 * - consul.host: Consul 地址（默localhost * - consul.port: Consul 端口（默8500 * - consul.service-name: 目标服务名（默认 target-service */
@Component
public class ConsulServiceWatcher {

    private static final Logger logger = LoggerFactory.getLogger(ConsulServiceWatcher.class);

    /** Consul 服务地址 */
    @Value("${consul.host:localhost}")
    private String consulHost;

    /** Consul 服务端口 */
    @Value("${consul.port:8500}")
    private int consulPort;

    /** 目标服务名称（需要监听的服务*/
    @Value("${consul.service-name:business-logic-editor}")
    private String serviceName;

    /** Blocking Query 阻塞等待时间（秒*/
    @Value("${consul.watch-timeout:10}")
    private int watchTimeout;

    /** 调度间隔（秒），建议略大watch-timeout */
    @Value("${consul.watch-interval:15}")
    private int watchInterval;

    /** Consul Java 客户*/
    private ConsulClient consulClient;

    /** 定时任务调度器，单线程执Watch 任务 */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /** 服务实例列表（线程安全，读多写少场景*/
    private final CopyOnWriteArrayList<ServiceInstance> instances = new CopyOnWriteArrayList<>();

    /** 上次 Consul 响应Index，用Blocking Query */
    private volatile long lastIndex;

    /**
     * 初始化方法（Spring 容器启动后自动调用）
     * 
     * 执行流程     * 1. 创建 ConsulClient 连接 Consul 服务
     * 2. 启动定时任务，每 5 秒执行一Watch 检     */
    @PostConstruct
    public void init() {
        consulClient = new ConsulClient(consulHost, consulPort);
        //scheduler.scheduleWithFixedDelay(this::watchServiceChanges, 0, watchInterval, TimeUnit.SECONDS);
        logger.info("Consul Service Watcher 初始 host={}, port={}, service={}, watchTimeout={}s, watchInterval={}s",
                consulHost, consulPort, serviceName, watchTimeout, watchInterval);
    }

    /**
     * 销毁方法（Spring 容器关闭前自动调用）
     * 
     * 优雅关闭定时任务调度器，避免资源泄漏
     */
    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
    }

    /**
     * Watch 服务实例变化（核心方法）
     * 
     * 执行流程     * 1. 构建 HealthServicesRequest，携lastIndex 实现 Blocking Query
     * 2. 调用 Consul API 获取健康服务列表（仅返回 passing 状态的实例     * 3. 更新 lastIndex 用于下次请求
     * 4. Consul 返回HealthService 转换ServiceInstance
     * 5. 对比新旧实例列表，仅在变化时更新本地缓存
     * 
     * Consul Blocking Query 机制     * - QueryParams(5, lastIndex): wait 最5 秒，lastIndex 用于增量更新
     * - 当实例无变化时，Consul 会阻塞请求直到超时或有变     * - 当实例有变化时，Consul 立即返回新数+ 新的 Consul-Index
     */
    private void watchServiceChanges() {
        try {
            HealthServicesRequest request = HealthServicesRequest.newBuilder()
                    .setQueryParams(new QueryParams(watchTimeout, lastIndex))
                    .setPassing(true)
                    .build();

            com.ecwid.consul.v1.Response<List<HealthService>> response =
                    consulClient.getHealthServices(serviceName, request);

            Long newIndex = response.getConsulIndex();
            if (newIndex == null) {
                return;
            }

            if (newIndex != lastIndex) {
                List<HealthService> services = response.getValue();
                List<ServiceInstance> newInstances = services.stream()
                        .map(this::toServiceInstance)
                        .collect(Collectors.toList());

                lastIndex = newIndex;
                instances.clear();
                instances.addAll(newInstances);
                logger.info("Consul 实例变更: service={}, index={}, 实例{}", serviceName, newIndex, newInstances.size());
            }else {
                logger.info("Consul 实例未变 service={}, index={}", serviceName, newIndex);
            }

        } catch (Exception e) {
            logger.error("Consul Watch 异常: service={}", serviceName, e);
        }
    }

    /**
     * Consul HealthService 转换为内ServiceInstance
     * 
     * @param hs Consul 返回的健康服务信     * @return 内部服务实例对象
     */
    private ServiceInstance toServiceInstance(HealthService hs) {
        ServiceInstance si = new ServiceInstance();
        si.setServiceName(hs.getService().getService());
        si.setInstanceId(hs.getService().getId());
        String address = hs.getService().getAddress();
        si.setHost(address == null || address.isEmpty() ? "127.0.0.1" : address);
        si.setPort(hs.getService().getPort());
        si.setHealthy(true);
        List<String> tags = hs.getService().getTags();
        si.setTags(tags != null ? new ArrayList<>(tags) : Collections.emptyList());
        return si;
    }

    /**
     * 获取当前服务实例列表（返回副本，避免外部修改     * 
     * @return 服务实例列表副本
     */
    public List<ServiceInstance> getInstances() {
        return new ArrayList<>(instances);
    }

    /**
     * 获取当前服务实例数量
     * 
     * @return 实例数量
     */
    public int getInstanceCount() {
        return instances.size();
    }

    /**
     * 服务实例数据模型
     * 
     * 字段说明     * - serviceName:  服务名称（如 "target-service"     * - instanceId:   实例唯一标识（Consul 注册 ID     * - host:         实例主机地址
     * - port:         实例 gRPC 端口
     * - healthy:      健康状态（true 表示 passing     * - tags:         实例标签列表
     */
    public static class ServiceInstance {
        private String serviceName;
        private String instanceId;
        private String host;
        private int port;
        private boolean healthy;
        private List<String> tags;

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public String getInstanceId() {
            return instanceId;
        }

        public void setInstanceId(String instanceId) {
            this.instanceId = instanceId;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public boolean isHealthy() {
            return healthy;
        }

        public void setHealthy(boolean healthy) {
            this.healthy = healthy;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ServiceInstance that = (ServiceInstance) o;
            return port == that.port &&
                    healthy == that.healthy &&
                    Objects.equals(serviceName, that.serviceName) &&
                    Objects.equals(instanceId, that.instanceId) &&
                    Objects.equals(host, that.host) &&
                    Objects.equals(tags, that.tags);
        }

        @Override
        public int hashCode() {
            return Objects.hash(serviceName, instanceId, host, port, healthy, tags);
        }

        @Override
        public String toString() {
            return "ServiceInstance{" +
                    "serviceName='" + serviceName + '\'' +
                    ", instanceId='" + instanceId + '\'' +
                    ", host='" + host + '\'' +
                    ", port=" + port +
                    ", healthy=" + healthy +
                    ", tags=" + tags +
                    '}';
        }
    }
}
