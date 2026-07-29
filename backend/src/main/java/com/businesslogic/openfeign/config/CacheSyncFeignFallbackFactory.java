package com.businesslogic.openfeign.config;

import com.businesslogic.openfeign.client.CacheSyncFeignClient;
import com.businesslogic.openfeign.dto.*;
import feign.FeignException;
import feign.RetryableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Feign 降级工厂
 *
 * 设计意图（与 gRPC 实现"Pod 下线视为成功" 语义对齐）：
 * - 当目Pod 调用失败（连接拒绝、读超时、服务下线），降级为"同步成功"
 * - GrpcBroadcastService 中的处理一致：UNAVAILABLE / DEADLINE_EXCEEDED / CANCELLED 视为成功
 * - 业务级失败（4xx）原样抛出，不降级（避免吞掉真实业务错误 *
 * 实现方式 * - 实现 FallbackFactory<T> 接口，可拿到 cause 异常做精细判 * - 返回Proxy 对象中，方法调用被拦截后create() 返回的目标对 *
 * 注意 * - Feign Fallback 在重试耗尽后才会触 * - 本降级仅目标 Pod 不可场景生效，不会影响业务异常处 */
@Component
public class CacheSyncFeignFallbackFactory implements FallbackFactory<CacheSyncFeignClient> {

    private static final Logger logger = LoggerFactory.getLogger(CacheSyncFeignFallbackFactory.class);

    @Override
    public CacheSyncFeignClient create(Throwable cause) {
        return new CacheSyncFeignClient() {

            @Override
            public PrepareResponse prepare(PrepareRequest request) {
                return handlePrepare(request, cause);
            }

            @Override
            public CommitResponse commit(CommitRequest request) {
                return handleCommit(request, cause);
            }

            @Override
            public AbortResponse abort(AbortRequest request) {
                return handleAbort(request, cause);
            }

            @Override
            public SyncResponse sync(SyncRequest request) {
                return handleSync(request, cause);
            }
        };
    }

    /**
     * Prepare 降级处理
     *
     * 触发降级的典型场景：
     * - FeignException 404/503: 目标 Pod 路径不存在或下线
     * - RetryableException: 重试耗尽后的网络异常
     * - ConnectException: 连接被拒     *
     * 返回 POD_OFFLINE 状态，由调用方识别下线视成
     */
    private PrepareResponse handlePrepare(PrepareRequest request, Throwable cause) {
        // 业务异常不降级，原样抛出
        if (isBusinessError(cause)) {
            logger.warn("[Feign-Fallback] Prepare 业务异常: syncId={}, cause={}",
                    request.getSyncId(), cause.getMessage());
            throw wrapIfNeeded(cause);
        }

        logger.info("[Feign-Fallback] Prepare Pod 不可 视为下线: syncId={}, reason={}",
                request.getSyncId(), describe(cause));

        return PrepareResponse.builder()
                .syncId(request.getSyncId())
                .podId("POD_OFFLINE")
                .status("POD_OFFLINE")
                .message("Pod 不可 " + describe(cause))
                .costMs(0L)
                .build();
    }

    private CommitResponse handleCommit(CommitRequest request, Throwable cause) {
        if (isBusinessError(cause)) {
            throw wrapIfNeeded(cause);
        }

        logger.info("[Feign-Fallback] Commit Pod 不可 视为下线: syncId={}, reason={}",
                request.getSyncId(), describe(cause));

        return CommitResponse.builder()
                .syncId(request.getSyncId())
                .podId("POD_OFFLINE")
                .status("POD_OFFLINE")
                .message("Pod 不可 " + describe(cause))
                .costMs(0L)
                .build();
    }

    private AbortResponse handleAbort(AbortRequest request, Throwable cause) {
        // Abort 阶段：Pod 不可用视为成功（幂等操作        // 语义：调用方发起 Abort 表明要清理，如果目标 Pod 都已经下线，无需清理
        logger.info("[Feign-Fallback] Abort Pod 不可 视为下线（无需清理 syncId={}",
                request.getSyncId());

        return AbortResponse.builder()
                .syncId(request.getSyncId())
                .podId("POD_OFFLINE")
                .status("POD_OFFLINE")
                .message("Pod 不可 Abort 视为成功")
                .costMs(0L)
                .build();
    }

    private SyncResponse handleSync(SyncRequest request, Throwable cause) {
        if (isBusinessError(cause)) {
            throw wrapIfNeeded(cause);
        }

        logger.info("[Feign-Fallback] Sync Pod 不可 syncId={}, reason={}",
                request.getSyncId(), describe(cause));

        return SyncResponse.builder()
                .syncId(request.getSyncId())
                .podId("POD_OFFLINE")
                .status("POD_OFFLINE")
                .message("Pod 不可 " + describe(cause))
                .costMs(0L)
                .build();
    }

    /**
     * 判断是否为业务级错误xx     * 业务错误不降级，由调用方按业务规则处     */
    private boolean isBusinessError(Throwable cause) {
        if (cause instanceof FeignException) {
            int status = ((FeignException) cause).status();
            return status >= 400 && status < 500;
        }
        return false;
    }

    private String describe(Throwable cause) {
        if (cause == null) return "unknown";
        String msg = cause.getMessage();
        return msg != null && msg.length() > 200 ? msg.substring(0, 200) + "..." : msg;
    }

    private RuntimeException wrapIfNeeded(Throwable cause) {
        if (cause instanceof RuntimeException) {
            return (RuntimeException) cause;
        }
        return new RuntimeException(cause);
    }
}
