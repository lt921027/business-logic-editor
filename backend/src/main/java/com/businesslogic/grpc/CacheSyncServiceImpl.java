package com.businesslogic.grpc;

import com.businesslogic.cache.FeatureExpressionCache;
import io.grpc.stub.StreamObserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * gRPC 缓存同步服务端实现（支持两阶段提交）
 *
 * 核心职责 * - 接收来自发起方（Service A）的缓存同步请求
 * - 执行本地缓存更新（新更新/删除表达式）
 * - 返回同步结果（成失败 + 耗时 *
 * 支持gRPC 方法 * 原有单阶段：
 * - syncCache(): 直接编译并写入主缓存（旧接口，向后兼容）
 *
 * 两阶段提交（2PC）：
 * - prepareCache(): 编译表达式并存入待激活区（stagingCache），不修改主缓存
 * - commitCache():  从待激活区取出预编译对象，原子替换到主缓存
 * - abortCache():   清理待激活区对应条目
 *
 * 设计说明 * - 继承 CacheSyncServiceImplBase（gRPC 服务端基类）
 * - 使用构造函数注FeatureExpressionCache
 * - 每个实例生成唯一 podId UUID 前缀），用于标识响应来源
 * - 记录操作耗时（costMs），便于性能监控
 * - 异常处理：捕获所有异常，返回 FAILED 状态而非抛出异常
 *
 * 线程安全 * - gRPC Server 为每个请求分配独立线 * - FeatureExpressionCache 内部使用 ConcurrentHashMap 保证线程安全
 * - 本类无状态成员变量（podId），天然线程安全
 */
public class CacheSyncServiceImpl extends CacheSyncServiceGrpc.CacheSyncServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(CacheSyncServiceImpl.class);


    /** 表达式缓存服务，负责编译和缓Aviator 表达式（含两阶段提交支持*/
    private final FeatureExpressionCache featureExpressionCache;

    /** 当前 Pod 的唯一标识 UUID 前缀），用于标识响应来源 */
    private final String podId;

    /**
     * 构造函     *
     * @param featureExpressionCache 表达式缓存服务（已注Spring 容器     */
    public CacheSyncServiceImpl(FeatureExpressionCache featureExpressionCache) {
        this.featureExpressionCache = featureExpressionCache;
        this.podId = UUID.randomUUID().toString().substring(0, 8);
    }

    // ======================== 原有单阶段同步方========================

    /**
     * 处理缓存同步请求（gRPC 服务端核心方- 旧接口）
     *
     * 执行流程     * 1. 记录请求开始时     * 2. 提取请求参数（txn、feature、version、expression     * 3. 根据 version 判断操作类型     *    - version == -1L: 调用 cacheExpression(txn, feature, -1, "") 删除缓存
     *    - version > 0:    调用 cacheExpression(txn, feature, version, expression) 更新缓存
     * 4. 构建成功响应（status=SUCCESS     * 5. 通过 responseObserver 发送响应并完成调用
     *
     * 注意：此方法直接修改主缓存，不经过两阶段提交流程     * 新业务建议使prepareCache + commitCache 两阶段方式     *
     * @param request          客户端发送的缓存同步请求
     * @param responseObserver 响应观察者，用于向客户端发送响     */
    @Override
    public void syncCache(CacheSyncProto.CacheSyncRequest request,
                          StreamObserver<CacheSyncProto.CacheSyncResponse> responseObserver) {
        long start = System.currentTimeMillis();
        try {
            String txn = request.getTransactionCode();
            String feature = request.getFeatureCode();
            long version = request.getVersion();
            String expression = request.getExpression();

            // version == -1L 表示删除缓存操作
            if (version == -1L) {
                featureExpressionCache.cacheExpression(txn, feature, version, "");
                logger.info("[gRPC-Sync] 缓存删除: {}#{} v{}", txn, feature, version);
            } else {
                //version > 0 表示新增或更新缓存操
                featureExpressionCache.cacheExpression(txn, feature, version, expression);
                logger.info("[gRPC-Sync] 缓存更新: {}#{} v{}", txn, feature, version);
            }

            // 构建成功响应
            CacheSyncProto.CacheSyncResponse response = CacheSyncProto.CacheSyncResponse.newBuilder()
                    .setSyncId(request.getSyncId())
                    .setPodId(podId)
                    .setStatus(CacheSyncProto.SyncStatus.SUCCESS)
                    .setCostMs(System.currentTimeMillis() - start)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            // 构建失败响应
            logger.error("[gRPC-Sync] 缓存同步失败: syncId={}", request.getSyncId(), e);
            CacheSyncProto.CacheSyncResponse response = CacheSyncProto.CacheSyncResponse.newBuilder()
                    .setSyncId(request.getSyncId())
                    .setPodId(podId)
                    .setStatus(CacheSyncProto.SyncStatus.FAILED)
                    .setMessage(e.getMessage())
                    .setCostMs(System.currentTimeMillis() - start)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    // ======================== 两阶段提交方========================

    /**
     * 处理 Prepare 阶段请求（两阶段提交 - 阶段一     *
     * 执行流程     * 1. 记录请求开始时     * 2. 提取请求参数（syncId、txn、feature、version、expression     * 3. 调用 FeatureExpressionCache.prepareExpression():
     *    a. 编译表达式（生成 Aviator 预编译对象）
     *    b. 存入 stagingCache（待激活区），不影mainCache
     *    c. 返回 PREPARE_OK / PREPARE_FAILED / ALREADY_EXISTS
     * 4. 构建 PrepareResponse 并返回给发起     *
     * 设计说明     * - 此阶段仅编译和暂存，不修改主缓存
     * - 编译失败的表达式不会进入 stagingCache
     * - 幂等处理：同一 syncId 重复调用返回 ALREADY_EXISTS
     * - 发起方根据所Pod Prepare 结果决定是否进入 Commit 阶段
     *
     * @param request          Prepare 请求消息（syncId + 表达式源码）
     * @param responseObserver 响应观察     */
    @Override
    public void prepareCache(CacheSyncProto.PrepareRequest request,
                             StreamObserver<CacheSyncProto.PrepareResponse> responseObserver)  {
        long start = System.currentTimeMillis();

        /* try {
            logger.info("开始等");
            Thread.sleep(4000);
        } catch (InterruptedException e) {

        } */
        try {
            // 提取请求参数
            String syncId = request.getSyncId();
            String txn = request.getTransactionCode();
            String feature = request.getFeatureCode();
            long version = request.getVersion();
            String expression = request.getExpression();

            logger.info("[gRPC-Prepare] 收到请求: syncId={}, {}#{} v{}", syncId, txn, feature, version);

            // 调用 FeatureExpressionCache prepareExpression 方法
            // 此方法会            // 1. 编译 Aviator 表达            // 2. 将预编译对象存入 stagingCache（待激活区            // 3. 不影mainCache（主缓存            
            FeatureExpressionCache.PrepareResult result = featureExpressionCache.prepareExpression(syncId, txn, feature, version, expression);

            //根据结果构建响应状态
            String status;
            String message = result.getErrorMessage();

            switch (result.getType()) {
                case PREPARE_OK:
                    status = CacheSyncProto.SyncStatus.PREPARE_OK;
                    logger.info("[gRPC-Prepare] 成功: syncId={}, {}#{} v{} (耗时{}ms)",
                            syncId, txn, feature, version, System.currentTimeMillis() - start);
                    break;
                case PREPARE_FAILED:
                    status = CacheSyncProto.SyncStatus.PREPARE_FAILED;
                    logger.warn("[gRPC-Prepare] 失败(编译错误): syncId={}, {}#{} v{}, error={}",
                            syncId, txn, feature, version, message);
                    break;
                case ALREADY_EXISTS:
                    status = CacheSyncProto.SyncStatus.ALREADY_EXISTS;
                    message = "已在待激活区";
                    logger.info("[gRPC-Prepare] 幂等: syncId={}, {}#{} (已在待激活区)", syncId, txn, feature);
                    break;
                default:
                    status = CacheSyncProto.SyncStatus.PREPARE_FAILED;
                    message = "未知结果类型";
                    break;
            }

            // 构建并返Prepare 响应
            CacheSyncProto.PrepareResponse response = CacheSyncProto.PrepareResponse.newBuilder()
                    .setSyncId(syncId)
                    .setPodId(podId)
                    .setStatus(status)
                    .setMessage(message != null ? message : "")
                    .setCostMs(System.currentTimeMillis() - start)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            //构建失败响应（未预期的运行时异常
            logger.error("[gRPC-Prepare] 未预期异 syncId={}", request.getSyncId(), e);
            CacheSyncProto.PrepareResponse response = CacheSyncProto.PrepareResponse.newBuilder()
                    .setSyncId(request.getSyncId())
                    .setPodId(podId)
                    .setStatus(CacheSyncProto.SyncStatus.PREPARE_FAILED)
                    .setMessage("服务内部错误: " + e.getMessage())
                    .setCostMs(System.currentTimeMillis() - start)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    /**
     * 处理 Commit 阶段请求（两阶段提交 - 阶段二）
     *
     * 执行流程     * 1. 记录请求开始时     * 2. 提取请求参数（syncId、txn、feature     * 3. 调用 FeatureExpressionCache.commitExpression():
     *    a. stagingCache 取出预编译对象（Prepare 阶段暂存的）
     *    b. 使用 ConcurrentHashMap.put() 原子写入 mainCache
     *    c. 清理 stagingCache 中对应条     * 4. 构建 CommitResponse 并返回给发起     *
     * 设计说明     * - 此阶段执行真正的缓存切换
     * - ConcurrentHashMap.put() 是原子操作，无需额外加锁
     * - put() 仅锁定对segment，不影响其他 key 的读     * - 读操作始终从 mainCache 获取，commit 期间不阻塞读
     * - 所Pod 同时收到 Commit 请求后同步切换表达式版本
     *
     * @param request          Commit 请求消息（syncId     * @param responseObserver 响应观察     */
    @Override
    public void commitCache(CacheSyncProto.CommitRequest request,
                            StreamObserver<CacheSyncProto.CommitResponse> responseObserver) {
        long start = System.currentTimeMillis();
        try {
            // 提取请求参数
            String syncId = request.getSyncId();
            String txn = request.getTransactionCode();
            String feature = request.getFeatureCode();

            logger.info("[gRPC-Commit] 收到请求: syncId={}, {}#{}", syncId, txn, feature);

            // 调用 FeatureExpressionCache commitExpression 方法
            // 此方法会            // 1. stagingCache 取出预编译对            // 2. 使用 ConcurrentHashMap.put() 原子写入 mainCache
            // 3. 清理 stagingCache
            FeatureExpressionCache.CommitResult result =
                    featureExpressionCache.commitExpression(syncId, txn, feature);

            //根据结果构建响应状
            String status;
            String message = result.getMessage();

            if (result.isOk()) {
                status = CacheSyncProto.SyncStatus.COMMIT_OK;
                logger.info("[gRPC-Commit] 成功: syncId={}, {}#{} (耗时{}ms)",
                        syncId, txn, feature, System.currentTimeMillis() - start);
            } else {
                status = CacheSyncProto.SyncStatus.COMMIT_NOT_FOUND;
                logger.warn("[gRPC-Commit] 失败(无待激活数: syncId={}, {}#{}", syncId, txn, feature);
            }

            // 构建并返Commit 响应
            CacheSyncProto.CommitResponse response = CacheSyncProto.CommitResponse.newBuilder()
                    .setSyncId(syncId)
                    .setPodId(podId)
                    .setStatus(status)
                    .setMessage(message != null ? message : "")
                    .setCostMs(System.currentTimeMillis() - start)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            // 构建失败响应
            logger.error("[gRPC-Commit] 未预期异 syncId={}", request.getSyncId(), e);
            CacheSyncProto.CommitResponse response = CacheSyncProto.CommitResponse.newBuilder()
                    .setSyncId(request.getSyncId())
                    .setPodId(podId)
                    .setStatus("COMMIT_FAILED")
                    .setMessage("服务内部错误: " + e.getMessage())
                    .setCostMs(System.currentTimeMillis() - start)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    /**
     * 处理 Abort 阶段请求（两阶段提交 - 回滚     *
     * 执行流程     * 1. 记录请求开始时     * 2. 提取请求参数（syncId、txn、feature     * 3. 调用 FeatureExpressionCache.abortExpression():
     *    a. stagingCache 移除对应条目
     *    b. 不影mainCache（主缓存保持不变     * 4. 构建 AbortResponse 并返回给发起     *
     * 触发场景     * - Prepare 阶段任一 Pod 编译失败 发起方向所Pod 发Abort
     * - Prepare 阶段任一 Pod 下线 发起方将下线视为成功，但其他 Pod 需 Abort
     * - Prepare 阶段网络超时 发起方超时后向已成功Pod 发Abort
     *
     * 设计说明     * - 仅清stagingCache，不影响 mainCache
     * - 幂等操作：重Abort 不会出错（stagingCache 无数据也返回成功     * - 确保 stagingCache 不会残留无效数据
     *
     * @param request          Abort 请求消息（syncId     * @param responseObserver 响应观察     */
    @Override
    public void abortCache(CacheSyncProto.AbortRequest request,
                           StreamObserver<CacheSyncProto.AbortResponse> responseObserver) {
        long start = System.currentTimeMillis();
        try {
            // 提取请求参数
            String syncId = request.getSyncId();
            String txn = request.getTransactionCode();
            String feature = request.getFeatureCode();

            logger.info("[gRPC-Abort] 收到请求: syncId={}, {}#{}", syncId, txn, feature);

            // 调用 FeatureExpressionCache abortExpression 方法
            // 此方法会            // 1. stagingCache 移除对应条目
            //2. 不影mainCache（主缓存保持不变
            boolean cleaned = featureExpressionCache.abortExpression(syncId, txn, feature);

            // 构建并返Abort 响应
            CacheSyncProto.AbortResponse response = CacheSyncProto.AbortResponse.newBuilder()
                    .setSyncId(syncId)
                    .setPodId(podId)
                    .setStatus(cleaned ? "ABORT_OK" : "ABORT_NOOP")
                    .setMessage(cleaned ? "已清理待激活区" : "待激活区无数")
                    .setCostMs(System.currentTimeMillis() - start)
                    .build();

            logger.info("[gRPC-Abort] 完成: syncId={}, {}#{}, cleaned={}", syncId, txn, feature, cleaned);

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            // 构建失败响应
            logger.error("[gRPC-Abort] 未预期异 syncId={}", request.getSyncId(), e);
            CacheSyncProto.AbortResponse response = CacheSyncProto.AbortResponse.newBuilder()
                    .setSyncId(request.getSyncId())
                    .setPodId(podId)
                    .setStatus("ABORT_FAILED")
                    .setMessage("服务内部错误: " + e.getMessage())
                    .setCostMs(System.currentTimeMillis() - start)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    /**
     * 获取当前 Pod 的唯一标识
     *
     * @return podId UUID 前缀     */
    public String getPodId() {
        return podId;
    }
}