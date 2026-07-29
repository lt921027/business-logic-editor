package com.businesslogic.openfeign.service;

import com.businesslogic.grpc.ConsulServiceWatcher;
import com.businesslogic.openfeign.client.CacheSyncFeignClient;
import com.businesslogic.openfeign.config.FeignConfig;
import com.businesslogic.openfeign.dto.*;
import feign.Client;
import feign.Request;
import feign.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.FeignClientBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * OpenFeign 广播协调服务（替GrpcBroadcastService *
 * 核心职责 * - Consul 拉取所Pod 实例
 * - 并发调用所Pod 的缓存同步接 * - 汇总结果，分类成功/失败/超时
 *
 * gRPC 实现的关键差异：
 * 1. 通过 FeignClientBuilder 为每Pod 动态构Feign 客户端（指定 host:port * 2. 客户端内置超时与重试（FeignConfig 统一配置 * 3. 整体超时控制CompletableFuture.get(30s) 兜底
 *
 * 性能优化 * - OkHttp 连接池：复用 20 个长连接，避免每次广播都建连
 * - 业务级重试：仅对 Pod 下线（Fallback 触发）做重试，不重试业务错误
 * - 并发调用：使CachedThreadPool，所Pod 同步触发
 *
 * 异常处理矩阵（与 gRPC GrpcBroadcastService 完全对齐）：
 * - HTTP 200 + status=PREPARE_OK:         成功
 * - HTTP 200 + status=PREPARE_FAILED:     业务失败（编译错误）
 * - HTTP 200 + status=POD_OFFLINE:        视为成功（Fallback 触发 * - HTTP 422:                              业务错误（参数校验失败）
 * - HTTP 5xx / FeignException:             视为 Pod 下线
 * - TimeoutException:                      超时，加入重试列 */
@Service
public class FeignBroadcastService {

    private static final Logger logger = LoggerFactory.getLogger(FeignBroadcastService.class);

    private final ConsulServiceWatcher serviceWatcher;
    private final ApplicationContext applicationContext;
    private final Client feignClient;  // 共享OkHttp Feign Client
    private final ExecutorService executorService;

    /** Feign 客户端缓 "host:port" CacheSyncFeignClient */
    private final ConcurrentHashMap<String, CacheSyncFeignClient> clientCache = new ConcurrentHashMap<>();

    @Value("${feign.broadcast.connect-timeout-ms:1000}")
    private int connectTimeoutMs;

    @Value("${feign.broadcast.read-timeout-ms:3000}")
    private int readTimeoutMs;

    @Value("${feign.broadcast.retry-read-timeout-ms:10000}")
    private int retryReadTimeoutMs;

    @Value("${feign.broadcast.total-timeout-seconds:30}")
    private int totalTimeoutSeconds;

    public FeignBroadcastService(ConsulServiceWatcher serviceWatcher,
                                  ApplicationContext applicationContext,
                                  Client feignClient) {
        this.serviceWatcher = serviceWatcher;
        this.applicationContext = applicationContext;
        this.feignClient = feignClient;
        this.executorService = Executors.newCachedThreadPool();
    }

    // ======================== 单阶段广播（向后兼容========================

    /**
     * 单阶段广播同步（兼容旧接口语义）
     */
    public BroadcastResult broadcastSync(String transactionCode, String featureCode,
                                         String expression, Long version) {
        String syncId = UUID.randomUUID().toString();
        SyncRequest request = SyncRequest.builder()
                .syncId(syncId)
                .transactionCode(transactionCode)
                .featureCode(featureCode)
                .version(version)
                .expression(expression)
                .timestamp(System.currentTimeMillis())
                .build();
        return doBroadcast(syncId, request, this::callSync, false);
    }

    // ======================== 两阶段提========================

    /**
     * 2PC Prepare 阶段广播
     */
    public BroadcastResult broadcastPrepare(String transactionCode, String featureCode,
                                            String expression, Long version) {
        String syncId = UUID.randomUUID().toString();
        PrepareRequest request = PrepareRequest.builder()
                .syncId(syncId)
                .transactionCode(transactionCode)
                .featureCode(featureCode)
                .version(version)
                .expression(expression)
                .timestamp(System.currentTimeMillis())
                .build();
        return doBroadcast(syncId, request, this::callPrepare, false);
    }

    /**
     * 2PC Commit 阶段广播
     */
    public BroadcastResult broadcastCommit(String syncId, String transactionCode, String featureCode) {
        CommitRequest request = CommitRequest.builder()
                .syncId(syncId)
                .transactionCode(transactionCode)
                .featureCode(featureCode)
                .timestamp(System.currentTimeMillis())
                .build();
        return doBroadcast(syncId, request, this::callCommit, false);
    }

    /**
     * 2PC Abort 阶段广播
     */
    public BroadcastResult broadcastAbort(String syncId, String transactionCode,
                                          String featureCode, String reason) {
        AbortRequest request = AbortRequest.builder()
                .syncId(syncId)
                .transactionCode(transactionCode)
                .featureCode(featureCode)
                .reason(reason)
                .timestamp(System.currentTimeMillis())
                .build();
        return doBroadcast(syncId, request, this::callAbort, false);
    }

    // ======================== 通用广播框架 ========================

    /**
     * 通用广播方法（消Prepare/Commit/Abort/Sync 的重复代码）
     *
     * @param syncId    同步 ID
     * @param request   请求对象
     * @param caller    调用具体方法（如 callPrepare）的 Function
     * @param <T>       请求类型
     * @return 广播结果
     */
    private <T> BroadcastResult doBroadcast(String syncId, T request,
                                           BiCall<CacheSyncFeignClient, T, ?> caller,
                                           boolean enableRetry) {
        List<ConsulServiceWatcher.ServiceInstance> targets = serviceWatcher.getInstances();

        if (targets.isEmpty()) {
            logger.info("无目标实 syncId={}", syncId);
            return BroadcastResult.noInstances(syncId);
        }

        logger.info("开Feign 广播: syncId={}, 目标实例{}", syncId, targets.size());

        // 并发调用
        List<CompletableFuture<SyncTaskResult>> futures = targets.stream()
                .map(instance -> CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                CacheSyncFeignClient client = getOrBuildClient(instance);
                                Object resp = caller.apply(client, request);
                                return buildSuccessTaskResult(instance, resp);
                            } catch (Exception e) {
                                return buildFailedTaskResult(instance, e);
                            }
                        },
                        executorService))
                .collect(Collectors.toList());

        // 整体超时控制
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));

        try {
            allFutures.get(totalTimeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            logger.warn("广播超时: syncId={}, totalTimeout={}s", syncId, totalTimeoutSeconds);
            // 注意：此future.cancel(true) 仅取CompletableFuture 包装            // 不会中断底层 HTTP 调用，但 OkHttp 连接会被超时机制回收
            futures.forEach(f -> f.cancel(true));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("广播被中 syncId={}", syncId);
        } catch (Exception e) {
            logger.error("广播异常: syncId={}", syncId, e);
        }

        return collectResults(syncId, futures, targets);
    }

    /**
     * 获取或构建指Pod Feign 客户     *
     * 关键点：Feign 默认通过服务名走 LoadBalancer，无法指定固host:port
     * 解决：用 FeignClientBuilder 配合 url 参数，绕过服务发     */
    private CacheSyncFeignClient getOrBuildClient(ConsulServiceWatcher.ServiceInstance instance) {
        String key = instance.getHost() + ":" + instance.getPort();
        return clientCache.computeIfAbsent(key, k -> {
            // 动态构Feign 客户端，URL 直连目标 Pod
            String url = "http://" + instance.getHost() + ":" + instance.getPort();
            return new FeignClientBuilder(applicationContext)
                    .forType(CacheSyncFeignClient.class, "feign-broadcast-" + key)
                    .url(url)
                    .build();
        });
    }

    // ======================== 各阶Feign 调用 ========================

    private PrepareResponse callPrepare(CacheSyncFeignClient client, PrepareRequest req) {
        return client.prepare(req);
    }

    private CommitResponse callCommit(CacheSyncFeignClient client, CommitRequest req) {
        return client.commit(req);
    }

    private AbortResponse callAbort(CacheSyncFeignClient client, AbortRequest req) {
        return client.abort(req);
    }

    private SyncResponse callSync(CacheSyncFeignClient client, SyncRequest req) {
        return client.sync(req);
    }

    // ======================== 结果构========================

    private SyncTaskResult buildSuccessTaskResult(ConsulServiceWatcher.ServiceInstance instance, Object resp) {
        SyncTaskResult result = new SyncTaskResult();
        result.setInstanceId(instance.getInstanceId());
        result.setHost(instance.getHost());
        result.setPort(instance.getPort());

        long start = System.currentTimeMillis();

        // 提取响应信息
        if (resp instanceof PrepareResponse) {
            PrepareResponse r = (PrepareResponse) resp;
            result.setStatus(r.getStatus());
            result.setMessage(r.getMessage());
            result.setPodId(r.getPodId());
            //Pod 下线视为成功（与 gRPC 对齐
            boolean isOffline = "POD_OFFLINE".equals(r.getStatus());
            result.setSuccess(isOffline || "PREPARE_OK".equals(r.getStatus()) || "ALREADY_EXISTS".equals(r.getStatus()));
            result.setCostMs(r.getCostMs());
        } else if (resp instanceof CommitResponse) {
            CommitResponse r = (CommitResponse) resp;
            result.setStatus(r.getStatus());
            result.setMessage(r.getMessage());
            result.setPodId(r.getPodId());
            boolean isOffline = "POD_OFFLINE".equals(r.getStatus());
            result.setSuccess(isOffline || "COMMIT_OK".equals(r.getStatus()));
            result.setCostMs(r.getCostMs());
        } else if (resp instanceof AbortResponse) {
            AbortResponse r = (AbortResponse) resp;
            result.setStatus(r.getStatus());
            result.setMessage(r.getMessage());
            result.setPodId(r.getPodId());
            //Abort: POD_OFFLINE/ABORT_OK/ABORT_NOOP 都视为成
            result.setSuccess(true);
            result.setCostMs(r.getCostMs());
        } else if (resp instanceof SyncResponse) {
            SyncResponse r = (SyncResponse) resp;
            result.setStatus(r.getStatus());
            result.setMessage(r.getMessage());
            result.setPodId(r.getPodId());
            boolean isOffline = "POD_OFFLINE".equals(r.getStatus());
            result.setSuccess(isOffline || "SUCCESS".equals(r.getStatus()));
            result.setCostMs(r.getCostMs());
        }

        result.setTotalCostMs(System.currentTimeMillis() - start);
        return result;
    }

    private SyncTaskResult buildFailedTaskResult(ConsulServiceWatcher.ServiceInstance instance, Throwable e) {
        SyncTaskResult result = new SyncTaskResult();
        result.setInstanceId(instance.getInstanceId());
        result.setHost(instance.getHost());
        result.setPort(instance.getPort());
        result.setSuccess(false);
        result.setStatus("FEIGN_EXCEPTION");
        result.setMessage(describe(e));
        return result;
    }

    /**
     * 收集并汇总广播结     */
    private BroadcastResult collectResults(String syncId,
                                           List<CompletableFuture<SyncTaskResult>> futures,
                                           List<ConsulServiceWatcher.ServiceInstance> targets) {
        BroadcastResult result = new BroadcastResult();
        result.setSyncId(syncId);
        result.setTotalInstances(targets.size());

        for (int i = 0; i < futures.size(); i++) {
            CompletableFuture<SyncTaskResult> future = futures.get(i);
            ConsulServiceWatcher.ServiceInstance instance = targets.get(i);
            try {
                SyncTaskResult taskResult = future.get(0, TimeUnit.MILLISECONDS);
                if (taskResult.isSuccess()) {
                    result.getSuccessInstances().add(taskResult);
                } else {
                    result.getFailedInstances().add(taskResult);
                }
            } catch (Exception e) {
                SyncTaskResult failed = new SyncTaskResult();
                failed.setInstanceId(instance.getInstanceId());
                failed.setHost(instance.getHost());
                failed.setPort(instance.getPort());
                failed.setSuccess(false);
                failed.setStatus("TIMEOUT");
                failed.setMessage("获取结果超时或被取消");
                result.getFailedInstances().add(failed);
            }
        }

        if (!result.getFailedInstances().isEmpty()) {
            result.setOverallStatus("ALL_FAILED");
            logger.warn("广播失败: syncId={}, failed={}", syncId, result.getFailedCount());
        } else {
            result.setOverallStatus("ALL_SUCCESS");
            logger.info("广播成功: syncId={}, success={}", syncId, result.getSuccessCount());
        }
        return result;
    }

    private String describe(Throwable e) {
        if (e == null) return "unknown";
        String msg = e.getMessage();
        if (msg == null) return e.getClass().getSimpleName();
        return msg.length() > 200 ? msg.substring(0, 200) + "..." : msg;
    }

    public int getInstanceCount() {
        return serviceWatcher.getInstanceCount();
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
        clientCache.clear();
    }

    // ======================== 内部类型 ========================

    @FunctionalInterface
    private interface BiCall<C, T, R> {
        R apply(C client, T request);
    }

    public static class SyncTaskResult {
        private String instanceId;
        private String host;
        private int port;
        private String podId;
        private boolean success;
        private String status;
        private String message;
        private long costMs;
        private long totalCostMs;

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

        public String getPodId() {
            return podId;
        }

        public void setPodId(String podId) {
            this.podId = podId;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public long getCostMs() {
            return costMs;
        }

        public void setCostMs(long costMs) {
            this.costMs = costMs;
        }

        public long getTotalCostMs() {
            return totalCostMs;
        }

        public void setTotalCostMs(long totalCostMs) {
            this.totalCostMs = totalCostMs;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            SyncTaskResult that = (SyncTaskResult) o;

            if (port != that.port) return false;
            if (success != that.success) return false;
            if (costMs != that.costMs) return false;
            if (totalCostMs != that.totalCostMs) return false;
            if (instanceId != null ? !instanceId.equals(that.instanceId) : that.instanceId != null) return false;
            if (host != null ? !host.equals(that.host) : that.host != null) return false;
            if (podId != null ? !podId.equals(that.podId) : that.podId != null) return false;
            if (status != null ? !status.equals(that.status) : that.status != null) return false;
            return message != null ? message.equals(that.message) : that.message == null;
        }

        @Override
        public int hashCode() {
            int result = instanceId != null ? instanceId.hashCode() : 0;
            result = 31 * result + (host != null ? host.hashCode() : 0);
            result = 31 * result + port;
            result = 31 * result + (podId != null ? podId.hashCode() : 0);
            result = 31 * result + (success ? 1 : 0);
            result = 31 * result + (status != null ? status.hashCode() : 0);
            result = 31 * result + (message != null ? message.hashCode() : 0);
            result = 31 * result + (int) (costMs ^ (costMs >>> 32));
            result = 31 * result + (int) (totalCostMs ^ (totalCostMs >>> 32));
            return result;
        }

        @Override
        public String toString() {
            return "SyncTaskResult{" +
                    "instanceId='" + instanceId + '\'' +
                    ", host='" + host + '\'' +
                    ", port=" + port +
                    ", podId='" + podId + '\'' +
                    ", success=" + success +
                    ", status='" + status + '\'' +
                    ", message='" + message + '\'' +
                    ", costMs=" + costMs +
                    ", totalCostMs=" + totalCostMs +
                    '}';
        }
    }

    public static class BroadcastResult {
        private String syncId;
        private int totalInstances;
        private List<SyncTaskResult> successInstances = new ArrayList<>();
        private List<SyncTaskResult> failedInstances = new ArrayList<>();
        private List<SyncTaskResult> timeoutInstances = new ArrayList<>();
        private String overallStatus;

        public String getSyncId() {
            return syncId;
        }

        public void setSyncId(String syncId) {
            this.syncId = syncId;
        }

        public int getTotalInstances() {
            return totalInstances;
        }

        public void setTotalInstances(int totalInstances) {
            this.totalInstances = totalInstances;
        }

        public List<SyncTaskResult> getSuccessInstances() {
            return successInstances;
        }

        public void setSuccessInstances(List<SyncTaskResult> successInstances) {
            this.successInstances = successInstances;
        }

        public List<SyncTaskResult> getFailedInstances() {
            return failedInstances;
        }

        public void setFailedInstances(List<SyncTaskResult> failedInstances) {
            this.failedInstances = failedInstances;
        }

        public List<SyncTaskResult> getTimeoutInstances() {
            return timeoutInstances;
        }

        public void setTimeoutInstances(List<SyncTaskResult> timeoutInstances) {
            this.timeoutInstances = timeoutInstances;
        }

        public String getOverallStatus() {
            return overallStatus;
        }

        public void setOverallStatus(String overallStatus) {
            this.overallStatus = overallStatus;
        }

        public int getSuccessCount() { return successInstances.size(); }
        public int getFailedCount() { return failedInstances.size(); }
        public int getTimeoutCount() { return timeoutInstances.size(); }

        public static BroadcastResult noInstances(String syncId) {
            BroadcastResult r = new BroadcastResult();
            r.setSyncId(syncId);
            r.setOverallStatus("NO_INSTANCES");
            return r;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            BroadcastResult that = (BroadcastResult) o;

            if (totalInstances != that.totalInstances) return false;
            if (syncId != null ? !syncId.equals(that.syncId) : that.syncId != null) return false;
            if (successInstances != null ? !successInstances.equals(that.successInstances) : that.successInstances != null)
                return false;
            if (failedInstances != null ? !failedInstances.equals(that.failedInstances) : that.failedInstances != null)
                return false;
            if (timeoutInstances != null ? !timeoutInstances.equals(that.timeoutInstances) : that.timeoutInstances != null)
                return false;
            return overallStatus != null ? overallStatus.equals(that.overallStatus) : that.overallStatus == null;
        }

        @Override
        public int hashCode() {
            int result = syncId != null ? syncId.hashCode() : 0;
            result = 31 * result + totalInstances;
            result = 31 * result + (successInstances != null ? successInstances.hashCode() : 0);
            result = 31 * result + (failedInstances != null ? failedInstances.hashCode() : 0);
            result = 31 * result + (timeoutInstances != null ? timeoutInstances.hashCode() : 0);
            result = 31 * result + (overallStatus != null ? overallStatus.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "BroadcastResult{" +
                    "syncId='" + syncId + '\'' +
                    ", totalInstances=" + totalInstances +
                    ", successInstances=" + successInstances +
                    ", failedInstances=" + failedInstances +
                    ", timeoutInstances=" + timeoutInstances +
                    ", overallStatus='" + overallStatus + '\'' +
                    '}';
        }
    }
}
