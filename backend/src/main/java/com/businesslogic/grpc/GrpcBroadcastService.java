package com.businesslogic.grpc;

import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static io.grpc.Status.Code.DEADLINE_EXCEEDED;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * gRPC 广播协调服务
 *
 * 核心职责 * - ConsulServiceWatcher 获取目标实例列表
 * - 并发向所有目Pod 发gRPC 缓存同步请求
 * - 收集所Pod 的响应结 * - 汇总广播状态（ALL_SUCCESS / PARTIAL_SUCCESS / ALL_FAILED *
 * 广播流程 * 1. 生成 syncId（UUID）标识本次广 * 2. ConsulServiceWatcher 获取当前实例快照
 * 3. 构建 CacheSyncRequest 消息
 * 4. 使用 CompletableFuture 并发调用所Pod
 * 5. 等待所有调用完成或超时0 秒）
 * 6. 收集结果并汇总状 *
 * 设计说明 * - 使用 CachedThreadPool 动态管理并发线 * - 单次调用超时 10 秒，总体超时 30  * - Pod 下线（UNAVAILABLE/DEADLINE_EXCEEDED/CANCELLED）视为同步成 * - 结果分为三类：成功实例、失败实例、超时实 *
 * 为什Pod 下线视为成功 * - Pod 正在 K8s 缩容/滚动更新，已不提供服 * - Pod 启动时会Redis Hash 兜底拉取最新缓 * - 避免因缩容导致整体广播标记为失败
 *
 * 线程安全 * - ExecutorService 保证并发调用安全
 * - CompletableFuture 异步收集结果
 * - 结果收集阶段遍历 futures 列表，无并发修改
 */
@Service
public class GrpcBroadcastService {

    private static final Logger logger = LoggerFactory.getLogger(GrpcBroadcastService.class);

    /** gRPC Channel 管理*/
    private final GrpcClientManager clientManager;

    /** Consul 服务实例监听*/
    private final ConsulServiceWatcher serviceWatcher;

    /** 并发调用线程*/
    private final ExecutorService executorService;

    /**
     * 构造函数（依赖注入     *
     * @param clientManager   gRPC Channel 管理     * @param serviceWatcher  Consul 服务实例监听     */
    public GrpcBroadcastService(GrpcClientManager clientManager, ConsulServiceWatcher serviceWatcher) {
        this.clientManager = clientManager;
        this.serviceWatcher = serviceWatcher;
        this.executorService = Executors.newCachedThreadPool();
    }

    /**
     * 执行广播同步（核心方法）
     *
     * 执行流程     * 1. 生成 syncId（UUID）标识本次广     * 2. ConsulServiceWatcher 获取当前实例快照
     * 3. 若无实例，返NO_INSTANCES 状     * 4. 构建 CacheSyncRequest 消息
     * 5. 使用 CompletableFuture 并发调用所Pod
     * 6. 等待所有调用完成或超时0 秒）
     * 7. 收集结果并返BroadcastResult
     *
     * @param transactionCode 交易     * @param featureCode     功能     * @param expression      表达式源码（可为 null）
     * @param version         版本号（-1L 表示删除）
     * @return 广播结果（包含各实例的同步状态）
     */
    public BroadcastResult broadcastSync(String transactionCode, String featureCode,
                                         String expression, Long version) {
        String syncId = UUID.randomUUID().toString();
        List<ConsulServiceWatcher.ServiceInstance> targets = serviceWatcher.getInstances();

        // 无目标实例时直接返回
       if (targets.isEmpty()) {
            logger.info("无目标实 syncId={}", syncId);
            return BroadcastResult.noInstances(syncId);
        }

        logger.info("开gRPC 广播: syncId={}, 目标实例数{}, txn={}, feature={}, v={}",
                syncId, targets.size(), transactionCode, featureCode, version);

        // 构建 gRPC 请求消息
        CacheSyncProto.PrepareRequest request = CacheSyncProto.PrepareRequest.newBuilder()
                .setSyncId(syncId)
                .setTransactionCode(transactionCode)
                .setFeatureCode(featureCode)
                .setVersion(version)
                .setExpression(expression != null ? expression : "")
                .setTimestamp(System.currentTimeMillis())
                .build();

        // 并发调用所有目Pod
        List<CompletableFuture<SyncTaskResult>> futures = targets.stream()
                .map(instance -> CompletableFuture.supplyAsync(
                        () -> callInstance(instance, request), executorService))
                .collect(Collectors.toList());

        // 等待所有调用完        
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));

        try {
            // 总体超时 30             
            allFutures.get(30, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            logger.warn("广播超时: syncId={}", syncId);
            futures.forEach(f -> f.cancel(true));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("广播被中 syncId={}", syncId);
        } catch (Exception e) {
            logger.error("广播异常: syncId={}", syncId, e);
        }

        //收集并汇总结
        return collectResults(syncId, futures, targets, request);
    }

    /**
     * 调用单个实例gRPC 服务
     *
     * 执行流程     * 1. Channel 管理器获取连     * 2. 创建 BlockingStub 并设10 秒超     * 3. 调用 syncCache 方法
     * 4. 处理响应或异     *
     * 异常处理     * - UNAVAILABLE:         连接被拒绝，视为 Pod 下线，同步成     * - DEADLINE_EXCEEDED:   调用超时，视Pod 下线，同步成     * - CANCELLED:           请求被取消，视为 Pod 下线，同步成     * - 其他异常:            标记为失     *
     * @param instance 目标实例信息
     * @param request  gRPC 请求消息
     * @return 调用结果（成失败 + 状态信息）
     */
    private SyncTaskResult callInstance(ConsulServiceWatcher.ServiceInstance instance,
                                        CacheSyncProto.PrepareRequest request) {
        SyncTaskResult result = new SyncTaskResult();
        result.setInstanceId(instance.getInstanceId());
        result.setHost(instance.getHost());
        result.setPort(instance.getPort());

        long start = System.currentTimeMillis();
        try {
            // 获取 gRPC Channel（复用已有连接）
            ManagedChannel channel = clientManager.getChannel(instance.getHost(), instance.getPort());

            // 创建阻塞 Stub 并设3 秒超            
            CacheSyncServiceGrpc.CacheSyncServiceBlockingStub stub =
                    CacheSyncServiceGrpc.newBlockingStub(channel)
                            .withDeadlineAfter(3, TimeUnit.SECONDS);

            // 调用 gRPC 服务
            CacheSyncProto.PrepareResponse response = stub.prepareCache(request);

            result.setSuccess(true);
            result.setStatus(response.getStatus());
            result.setCostMs(response.getCostMs());
            result.setMessage(response.getMessage());

        } catch (StatusRuntimeException e) {
            io.grpc.Status.Code code = e.getStatus().getCode();

            // Pod 下线场景：UNAVAILABLE、DEADLINE_EXCEEDED、CANCELLED 视为同步成功
            if (code == io.grpc.Status.Code.UNAVAILABLE ||
                    code == io.grpc.Status.Code.CANCELLED) {

                logger.info("实例不可视为同步成功): instance={}, reason={}",
                        instance.getInstanceId(), e.getStatus().getDescription());
                result.setSuccess(true);
                result.setStatus("POD_OFFLINE_TREATED_AS_SUCCESS");
                result.setMessage("Pod 已下线，视为同步成功: " + e.getStatus().getDescription());

            }else if(code == DEADLINE_EXCEEDED) {
                result.setSuccess(false);
                result.setStatus(code.name());
                result.setMessage("Pod 调用超时: " + e.getStatus().getDescription());
            }else {
                // 其他异常标记为失                
                logger.error("gRPC 调用失败: instance={}, code={}",
                instance.getInstanceId(), code, e);
                result.setSuccess(false);
                result.setStatus("FAILED");
                result.setMessage(e.getMessage());
            }

        } catch (Exception e) {
            logger.error("gRPC 调用异常: instance={}", instance.getInstanceId(), e);
            result.setSuccess(false);
            result.setStatus("FAILED");
            result.setMessage(e.getMessage());
        }

        result.setTotalCostMs(System.currentTimeMillis() - start);
        return result;
    }

    /**
     * 收集并汇总广播结     *
     * 执行流程     * 1. 遍历所CompletableFuture
     * 2. 获取每个任务的结果（SyncTaskResult     * 3. 根据成功/失败/超时分类
     * 4. 计算总体状态：
     *    - 先判断失败集合，有失败直接返ALL_FAILED
     *    - 无失败则判断超时集合，有超时则重     *    - 重试成功后从超时列表移除
     *    - 超时列表为空则返ALL_SUCCESS
     *
     * @param syncId   广播标识
     * @param futures  异步任务列表
     * @param targets  目标实例列表
     * @param request  原始 gRPC 请求消息
     * @return 汇总后的广播结     */
    private BroadcastResult collectResults(String syncId,
                                           List<CompletableFuture<SyncTaskResult>> futures,
                                           List<ConsulServiceWatcher.ServiceInstance> targets,
                                           CacheSyncProto.PrepareRequest request) {
        BroadcastResult result = new BroadcastResult();
        result.setSyncId(syncId);
        result.setTotalInstances(targets.size());

        // 遍历所有异步任务结        
        for (int i = 0; i < futures.size(); i++) {
            CompletableFuture<SyncTaskResult> future = futures.get(i);
            ConsulServiceWatcher.ServiceInstance instance = targets.get(i);
            try {
                SyncTaskResult taskResult = future.get();
                if (taskResult.isSuccess()) {
                    result.getSuccessInstances().add(taskResult);
                }else if(!taskResult.isSuccess() && "DEADLINE_EXCEEDED".equals(taskResult.getStatus())){
                    result.getTimeoutInstances().add(taskResult);
                }else {
                    result.getFailedInstances().add(taskResult);
                }
            } catch (Exception e) {
                // 获取结果异常
                SyncTaskResult failed = new SyncTaskResult();
                failed.setInstanceId(instance.getInstanceId());
                failed.setHost(instance.getHost());
                failed.setPort(instance.getPort());
                failed.setSuccess(false);
                failed.setStatus("ERROR");
                failed.setMessage(e.getMessage());
                result.getFailedInstances().add(failed);
            }
        }

        // 第一步：有失败直接返ALL_FAILED
        if (!result.getFailedInstances().isEmpty()) {
            result.setOverallStatus("ALL_FAILED");
            logger.warn("广播存在失败: syncId={}, 失败={}, 跳过超时重试",
                    syncId, result.getFailedCount());
            return result;
        }

        // 第二步：无失败，检查是否有超时
        if (result.getTimeoutInstances().isEmpty()) {
            result.setOverallStatus("ALL_SUCCESS");
            return result;
        }

        // 第三步：有超时，逐个重试超时实例
        logger.info("开始重试超时实 syncId={}, 超时数{}", syncId, result.getTimeoutCount());
        retryTimeoutInstances(result, request);

        // 第四步：重试后再次校验超时列        
        if (result.getTimeoutInstances().isEmpty()) {
            result.setOverallStatus("ALL_SUCCESS");
            logger.info("超时重试全部成功: syncId={}", syncId);
        } else {
            result.setOverallStatus("PARTIAL_SUCCESS");
            logger.warn("超时重试仍有失败: syncId={}, 剩余超时数{}",
                    syncId, result.getTimeoutCount());
        }

        return result;
    }

    /**
     * 重试超时的实     *
     * 执行流程     * 1. 遍历超时列表中的每个实例
     * 2. 重新发起 gRPC 调用（单次超10 秒）
     * 3. 调用成功则从超时列表中移     * 4. 调用失败则保留在超时列表     *
     * @param result  广播结果对象
     * @param request 原始 gRPC 请求
     */
    private void retryTimeoutInstances(BroadcastResult result, CacheSyncProto.PrepareRequest request) {
        List<SyncTaskResult> stillTimeout = new ArrayList<>();

        for (SyncTaskResult timeout : result.getTimeoutInstances()) {
            try {
                logger.info("重试超时实例: syncId={}, instance={}:{}", 
                        result.getSyncId(), timeout.getHost(), timeout.getPort());

                // 重新调用该实                
                SyncTaskResult retryResult = retrySingleInstance(timeout, request);
                if (retryResult.isSuccess()) {
                    // 重试成功，加入成功列                   
                    result.getSuccessInstances().add(retryResult);
                    logger.info("重试成功: syncId={}, instance={}", 
                            result.getSyncId(), retryResult.getInstanceId());
                } else {
                    // 重试失败，保留在超时列表
                    stillTimeout.add(timeout);
                    logger.warn("重试失败: syncId={}, instance={}, status={}", 
                            result.getSyncId(), timeout.getInstanceId(), retryResult.getStatus());
                }
            } catch (Exception e) {
                // 重试异常，保留在超时列表
                stillTimeout.add(timeout);
                logger.error("重试异常: syncId={}, instance={}", 
                        result.getSyncId(), timeout.getInstanceId(), e);
            }
        }

        // 更新超时列表为仍未超时的实例
        result.setTimeoutInstances(stillTimeout);
    }

    /**
     * 重试单个实例gRPC 调用
     *
     * @param timeout 超时的实例信     * @param request 原始 gRPC 请求
     * @return 重试结果
     */
    private SyncTaskResult retrySingleInstance(SyncTaskResult timeout, 
                                               CacheSyncProto.PrepareRequest request) {
        SyncTaskResult result = new SyncTaskResult();
        result.setInstanceId(timeout.getInstanceId());
        result.setHost(timeout.getHost());
        result.setPort(timeout.getPort());

        long start = System.currentTimeMillis();
        try {
            ManagedChannel channel = clientManager.getChannel(timeout.getHost(), timeout.getPort());

            CacheSyncServiceGrpc.CacheSyncServiceBlockingStub stub =
                    CacheSyncServiceGrpc.newBlockingStub(channel)
                            .withDeadlineAfter(10, TimeUnit.SECONDS);

            CacheSyncProto.PrepareResponse response = stub.prepareCache(request);


            result.setSuccess(true);
            result.setStatus(response.getStatus());
            result.setCostMs(response.getCostMs());
            result.setMessage(response.getMessage());

        } catch (StatusRuntimeException e) {
            io.grpc.Status.Code code = e.getStatus().getCode();

            // Pod 下线场景视为成功
            if (code == io.grpc.Status.Code.UNAVAILABLE ||
                    code == DEADLINE_EXCEEDED ||
                    code == io.grpc.Status.Code.CANCELLED) {

                result.setSuccess(true);
                result.setStatus("POD_OFFLINE_TREATED_AS_SUCCESS");
                result.setMessage("Pod 已下线，视为同步成功");
            } else {
                result.setSuccess(false);
                result.setStatus("RETRY_FAILED");
                result.setMessage(e.getMessage());
            }
        } catch (Exception e) {
            result.setSuccess(false);
            result.setStatus("RETRY_ERROR");
            result.setMessage(e.getMessage());
        }

        result.setTotalCostMs(System.currentTimeMillis() - start);
        return result;
    }

    /**
     * 获取当前目标实例数量
     * 
     * @return 实例数量
     */
    public int getInstanceCount() {
        return serviceWatcher.getInstanceCount();
    }

    /**
     * 单次调用结果
     *
     * 字段说明     * - instanceId:  实例唯一标识
     * - host:        实例主机地址
     * - port:        实例 gRPC 端口
     * - success:     调用是否成功
     * - status:      调用状态（SUCCESS / FAILED / POD_OFFLINE_TREATED_AS_SUCCESS     * - message:     附加信息
     * - costMs:      gRPC 调用耗时（服务端处理时间     * - totalCostMs: 总耗时（含网络传输     */
    public static class SyncTaskResult {
        private String instanceId;
        private String host;
        private int port;
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
            return port == that.port &&
                    success == that.success &&
                    costMs == that.costMs &&
                    totalCostMs == that.totalCostMs &&
                    Objects.equals(instanceId, that.instanceId) &&
                    Objects.equals(host, that.host) &&
                    Objects.equals(status, that.status) &&
                    Objects.equals(message, that.message);
        }

        @Override
        public int hashCode() {
            return Objects.hash(instanceId, host, port, success, status, message, costMs, totalCostMs);
        }

        @Override
        public String toString() {
            return "SyncTaskResult{" +
                    "instanceId='" + instanceId + '\'' +
                    ", host='" + host + '\'' +
                    ", port=" + port +
                    ", success=" + success +
                    ", status='" + status + '\'' +
                    ", message='" + message + '\'' +
                    ", costMs=" + costMs +
                    ", totalCostMs=" + totalCostMs +
                    '}';
        }
    }

    /**
     * 广播汇总结     *
     * 字段说明     * - syncId:            广播唯一标识
     * - totalInstances:    目标实例总数
     * - successInstances:  成功实例列表
     * - failedInstances:   失败实例列表
     * - timeoutInstances:  超时实例列表（仅记录标识     * - overallStatus:     总体状态（ALL_SUCCESS / PARTIAL_SUCCESS / ALL_FAILED / NO_INSTANCES     */
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BroadcastResult that = (BroadcastResult) o;
            return totalInstances == that.totalInstances &&
                    Objects.equals(syncId, that.syncId) &&
                    Objects.equals(successInstances, that.successInstances) &&
                    Objects.equals(failedInstances, that.failedInstances) &&
                    Objects.equals(timeoutInstances, that.timeoutInstances) &&
                    Objects.equals(overallStatus, that.overallStatus);
        }

        @Override
        public int hashCode() {
            return Objects.hash(syncId, totalInstances, successInstances, failedInstances, timeoutInstances, overallStatus);
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

        /**
         * 创建无实例的广播结果
         *
         * @param syncId 广播标识
         * @return BroadcastResult 实例
         */
        public static BroadcastResult noInstances(String syncId) {
            BroadcastResult result = new BroadcastResult();
            result.setSyncId(syncId);
            result.setOverallStatus("NO_INSTANCES");
            return result;
        }
    }
}
