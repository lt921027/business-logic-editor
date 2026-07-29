package com.businesslogic.openfeign.client;

import com.businesslogic.openfeign.config.CacheSyncFeignFallbackFactory;
import com.businesslogic.openfeign.config.FeignConfig;
import com.businesslogic.openfeign.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 缓存同步 Feign 客户 *
 * 调用方：业务 Pod 通过该接口调用其Pod 的缓存同步服 * 服务方：每个 Pod 都注册一个本接口指向自己（即 serviceName 指向自己 *        Spring Cloud LoadBalancer + Consul 自动解析
 *
 * 接口映射（与 gRPC 方法一一对应）：
 * - POST /openfeign/sync/prepare  gRPC prepareCache
 * - POST /openfeign/sync/commit   gRPC commitCache
 * - POST /openfeign/sync/abort    gRPC abortCache
 * - POST /openfeign/sync          gRPC syncCache（兼容旧接口 *
 * URL 设计原则 * - 使用统一/openfeign/sync 前缀便于网关路由
 * - 子路径用动词表达动作，避免动宾语结构
 *
 * 性能说明 * - 接口方法返回 Response 对象（同步阻塞），由调用方封CompletableFuture 实现并发
 * - Feign 不支持服务端推送（Stream），故分 3 个端点实2PC 协议
 *
 * @FeignClient 关键参数 * - name: 服务名（Consul 注册名），用于服务发 * - configuration: 引用 FeignConfig 注入超时/重试/连接 * - fallbackFactory: 异常时降级为"Pod 下线视为成功"语义
 * - path: 统一前缀
 */
@FeignClient(
        name = "${spring.application.name:business-logic-editor}",
        path = "/openfeign/sync",
        configuration = FeignConfig.class,
        fallbackFactory = CacheSyncFeignFallbackFactory.class,
        primary = true
)
public interface CacheSyncFeignClient {

    /**
     * Prepare 阶段PC 阶段一：编译并暂存     *
     * @param request 包含 syncId、txn、feature、version、expression
     * @return Prepare 响应
     */
    @PostMapping(value = "/prepare", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    PrepareResponse prepare(@RequestBody PrepareRequest request);

    /**
     * Commit 阶段PC 阶段二：原子切换主缓存）
     *
     * @param request 包含 syncId、txn、feature
     * @return Commit 响应
     */
    @PostMapping(value = "/commit", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    CommitResponse commit(@RequestBody CommitRequest request);

    /**
     * Abort 阶段PC 回滚：清理待激活区     *
     * @param request 包含 syncId、txn、feature、reason
     * @return Abort 响应
     */
    @PostMapping(value = "/abort", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    AbortResponse abort(@RequestBody AbortRequest request);

    /**
     * 单阶段同步（兼容旧接口，向后兼容     *
     * @param request 同步请求
     * @return Sync 响应
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    SyncResponse sync(@RequestBody SyncRequest request);
}
