package com.businesslogic.openfeign.server;

import com.businesslogic.cache.FeatureExpressionCache;
import com.businesslogic.openfeign.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 缓存同步 HTTP 服务端（OpenFeign 方案下替gRPC CacheSyncServiceImpl *
 * 设计要点 * - 提供gRPC 实现完全对等4 个端点（prepare/commit/abort/sync * - 业务逻辑直接复用 FeatureExpressionCache，零侵入切换
 * - 异常处理：捕获所有异常后返回对应状态码 + JSON 响应体，便于 Feign 错误解码
 * - 不再使用 Thread.sleep()（gRPC 实现的遗留问题在 Feign 方案中自然消失）
 *
 * 端点状态码约定 * - 200 OK + status=PREPARE_OK: 编译暂存成功
 * - 200 OK + status=PREPARE_FAILED: 编译失败（业务错误，非服务端异常 * - 422 Unprocessable Entity: 业务参数错误（参数校验失败）
 * - 500 Internal Server Error: 未预期的服务端异 *
 * Pod 标识 * - 启动时生8 UUID 前缀作为 podId，标识本服务来源
 * - 便于Pod 场景下追踪响应来自哪个实 */
@RestController
@RequestMapping("/openfeign/sync")
public class CacheSyncController {

    private static final Logger logger = LoggerFactory.getLogger(CacheSyncController.class);

    private final FeatureExpressionCache featureExpressionCache;
    private final String podId;

    public CacheSyncController(FeatureExpressionCache featureExpressionCache) {
        this.featureExpressionCache = featureExpressionCache;
        this.podId = UUID.randomUUID().toString().substring(0, 8);
    }

    // ======================== 两阶段提========================

    /**
     * Prepare 阶段PC 阶段一：编译并暂存到待激活区     */
    @PostMapping("/prepare")
    public ResponseEntity<PrepareResponse> prepare(@RequestBody PrepareRequest request) {
        long start = System.currentTimeMillis();
        try {
            // 参数校验
            String validationError = validatePrepare(request);
            if (validationError != null) {
                return ResponseEntity.status(422).body(
                        PrepareResponse.builder()
                                .syncId(request.getSyncId())
                                .podId(podId)
                                .status("VALIDATION_FAILED")
                                .message(validationError)
                                .costMs(System.currentTimeMillis() - start)
                                .build()
                );
            }

            String syncId = request.getSyncId();
            String txn = request.getTransactionCode();
            String feature = request.getFeatureCode();
            long version = request.getVersion();

            logger.info("[Feign-Prepare] 收到请求: syncId={}, {}#{} v{}", syncId, txn, feature, version);

            FeatureExpressionCache.PrepareResult result =
                    featureExpressionCache.prepareExpression(syncId, txn, feature, version, request.getExpression());

            String status;
            String message = result.getErrorMessage();

            switch (result.getType()) {
                case PREPARE_OK:
                    status = "PREPARE_OK";
                    logger.info("[Feign-Prepare] 成功: syncId={} ({}ms)",
                            syncId, System.currentTimeMillis() - start);
                    break;
                case PREPARE_FAILED:
                    status = "PREPARE_FAILED";
                    logger.warn("[Feign-Prepare] 编译失败: syncId={}, error={}", syncId, message);
                    break;
                case ALREADY_EXISTS:
                    status = "ALREADY_EXISTS";
                    message = "已在待激活区";
                    logger.info("[Feign-Prepare] 幂等命中: syncId={}", syncId);
                    break;
                default:
                    status = "PREPARE_FAILED";
                    message = "未知结果类型";
                    break;
            }

            return ResponseEntity.ok(
                    PrepareResponse.builder()
                            .syncId(syncId)
                            .podId(podId)
                            .status(status)
                            .message(message != null ? message : "")
                            .costMs(System.currentTimeMillis() - start)
                            .build()
            );
        } catch (Exception e) {
            // 编译错误业务可预的错误，包装200 + PREPARE_FAILED
            // 真正的未预期异常才走 500
            logger.error("[Feign-Prepare] 未预期异 syncId={}", request.getSyncId(), e);
            return ResponseEntity.status(500).body(
                    PrepareResponse.builder()
                            .syncId(request.getSyncId())
                            .podId(podId)
                            .status("INTERNAL_ERROR")
                            .message("服务内部错误: " + e.getMessage())
                            .costMs(System.currentTimeMillis() - start)
                            .build()
            );
        }
    }

    /**
     * Commit 阶段PC 阶段二：原子切换主缓存）
     */
    @PostMapping("/commit")
    public ResponseEntity<CommitResponse> commit(@RequestBody CommitRequest request) {
        long start = System.currentTimeMillis();
        try {
            String validationError = validateCommit(request);
            if (validationError != null) {
                return ResponseEntity.status(422).body(
                        CommitResponse.builder()
                                .syncId(request.getSyncId())
                                .podId(podId)
                                .status("VALIDATION_FAILED")
                                .message(validationError)
                                .costMs(System.currentTimeMillis() - start)
                                .build()
                );
            }

            String syncId = request.getSyncId();
            String txn = request.getTransactionCode();
            String feature = request.getFeatureCode();

            logger.info("[Feign-Commit] 收到请求: syncId={}, {}#{}", syncId, txn, feature);

            FeatureExpressionCache.CommitResult result =
                    featureExpressionCache.commitExpression(syncId, txn, feature);

            String status;
            String message = result.getMessage();

            if (result.isOk()) {
                status = "COMMIT_OK";
                logger.info("[Feign-Commit] 成功: syncId={} ({}ms)",
                        syncId, System.currentTimeMillis() - start);
            } else {
                status = "COMMIT_NOT_FOUND";
                logger.warn("[Feign-Commit] 无待激活数 syncId={}", syncId);
            }

            return ResponseEntity.ok(
                    CommitResponse.builder()
                            .syncId(syncId)
                            .podId(podId)
                            .status(status)
                            .message(message != null ? message : "")
                            .costMs(System.currentTimeMillis() - start)
                            .build()
            );
        } catch (Exception e) {
            logger.error("[Feign-Commit] 未预期异 syncId={}", request.getSyncId(), e);
            return ResponseEntity.status(500).body(
                    CommitResponse.builder()
                            .syncId(request.getSyncId())
                            .podId(podId)
                            .status("INTERNAL_ERROR")
                            .message("服务内部错误: " + e.getMessage())
                            .costMs(System.currentTimeMillis() - start)
                            .build()
            );
        }
    }

    /**
     * Abort 阶段PC 回滚：清理待激活区     */
    @PostMapping("/abort")
    public ResponseEntity<AbortResponse> abort(@RequestBody AbortRequest request) {
        long start = System.currentTimeMillis();
        try {
            String validationError = validateAbort(request);
            if (validationError != null) {
                return ResponseEntity.status(422).body(
                        AbortResponse.builder()
                                .syncId(request.getSyncId())
                                .podId(podId)
                                .status("VALIDATION_FAILED")
                                .message(validationError)
                                .costMs(System.currentTimeMillis() - start)
                                .build()
                );
            }

            String syncId = request.getSyncId();
            String txn = request.getTransactionCode();
            String feature = request.getFeatureCode();

            logger.info("[Feign-Abort] 收到请求: syncId={}, {}#{}, reason={}", syncId, txn, feature, request.getReason());

            boolean cleaned = featureExpressionCache.abortExpression(syncId, txn, feature);

            logger.info("[Feign-Abort] 完成: syncId={}, cleaned={}", syncId, cleaned);

            return ResponseEntity.ok(
                    AbortResponse.builder()
                            .syncId(syncId)
                            .podId(podId)
                            .status(cleaned ? "ABORT_OK" : "ABORT_NOOP")
                            .message(cleaned ? "已清理待激活区" : "待激活区无数")
                            .costMs(System.currentTimeMillis() - start)
                            .build()
            );
        } catch (Exception e) {
            logger.error("[Feign-Abort] 未预期异 syncId={}", request.getSyncId(), e);
            return ResponseEntity.status(500).body(
                    AbortResponse.builder()
                            .syncId(request.getSyncId())
                            .podId(podId)
                            .status("INTERNAL_ERROR")
                            .message("服务内部错误: " + e.getMessage())
                            .costMs(System.currentTimeMillis() - start)
                            .build()
            );
        }
    }

    // ======================== 单阶段同步（向后兼容========================

    /**
     * 单阶段同步（旧接口兼容）
     */
    @PostMapping
    public ResponseEntity<SyncResponse> sync(@RequestBody SyncRequest request) {
        long start = System.currentTimeMillis();
        try {
            String validationError = validateSync(request);
            if (validationError != null) {
                return ResponseEntity.status(422).body(
                        SyncResponse.builder()
                                .syncId(request.getSyncId())
                                .podId(podId)
                                .status("VALIDATION_FAILED")
                                .message(validationError)
                                .costMs(System.currentTimeMillis() - start)
                                .build()
                );
            }

            String txn = request.getTransactionCode();
            String feature = request.getFeatureCode();
            long version = request.getVersion();

            featureExpressionCache.cacheExpression(txn, feature, version, request.getExpression());
            logger.info("[Feign-Sync] 完成: {}#{} v{}", txn, feature, version);

            return ResponseEntity.ok(
                    SyncResponse.builder()
                            .syncId(request.getSyncId())
                            .podId(podId)
                            .status("SUCCESS")
                            .costMs(System.currentTimeMillis() - start)
                            .build()
            );
        } catch (Exception e) {
            logger.error("[Feign-Sync] 异常: syncId={}", request.getSyncId(), e);
            return ResponseEntity.status(500).body(
                    SyncResponse.builder()
                            .syncId(request.getSyncId())
                            .podId(podId)
                            .status("FAILED")
                            .message(e.getMessage())
                            .costMs(System.currentTimeMillis() - start)
                            .build()
            );
        }
    }

    // ======================== 校验工具 ========================

    private String validatePrepare(PrepareRequest req) {
        if (req == null) return "请求体不能为";
        if (isBlank(req.getSyncId())) return "syncId 不能为空";
        if (isBlank(req.getTransactionCode())) return "transactionCode 不能为空";
        if (isBlank(req.getFeatureCode())) return "featureCode 不能为空";
        if (req.getVersion() == null) return "version 不能为空";
        if (req.getVersion() != -1L && isBlank(req.getExpression())) return "新增/更新expression 不能为空";
        return null;
    }

    private String validateCommit(CommitRequest req) {
        if (req == null) return "请求体不能为";
        if (isBlank(req.getSyncId())) return "syncId 不能为空";
        if (isBlank(req.getTransactionCode())) return "transactionCode 不能为空";
        if (isBlank(req.getFeatureCode())) return "featureCode 不能为空";
        return null;
    }

    private String validateAbort(AbortRequest req) {
        if (req == null) return "请求体不能为";
        if (isBlank(req.getSyncId())) return "syncId 不能为空";
        if (isBlank(req.getTransactionCode())) return "transactionCode 不能为空";
        if (isBlank(req.getFeatureCode())) return "featureCode 不能为空";
        return null;
    }

    private String validateSync(SyncRequest req) {
        if (req == null) return "请求体不能为";
        if (isBlank(req.getTransactionCode())) return "transactionCode 不能为空";
        if (isBlank(req.getFeatureCode())) return "featureCode 不能为空";
        if (req.getVersion() == null) return "version 不能为空";
        return null;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    public String getPodId() {
        return podId;
    }
}
