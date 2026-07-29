package com.businesslogic.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * gRPC Channel 连接池管理器
 * 
 * 核心职责 * - 缓存和管理与目标 Pod gRPC 连接（ManagedChannel * - 避免重复创建连接，提升广播性能
 * - 优雅关闭所有连接，避免资源泄漏
 * 
 * 设计说明 * - 使用 ConcurrentHashMap 实现线程安全Channel 缓存
 * - Key 格式: "host:port"，Value: ManagedChannel 实例
 * - computeIfAbsent 保证同一地址仅创建一Channel
 * - 启用 Keep-Alive 机制，保持长连接活跃
 * 
 * 为什么需Channel 缓存 * - gRPC Channel 创建成本较高（TCP 握手、TLS 协商等）
 * - 缓存后可复用连接，减少延 * - 同一 Pod 在多次广播中会被重复调用
 * 
 * 注意事项 * - Channel 是线程安全的，可被多个线程并发使 * - 应用关闭时必须调shutdown() 释放底层资源
 * - 未关闭的 Channel 会导致连接泄 */
@Component
public class GrpcClientManager {

    private static final Logger logger = LoggerFactory.getLogger(GrpcClientManager.class);

    /**
     * Channel 缓存
     * Key: "host:port" 格式的地址字符     * Value: ManagedChannel 实例
     */
    private final Map<String, ManagedChannel> channelCache = new ConcurrentHashMap<>();

    /**
     * 获取指定地址gRPC Channel
     * 
     * 执行流程     * 1. 构建缓存 Key（host:port     * 2. 检查缓存，若存在则直接返回
     * 3. 若不存在，创建新 Channel 并放入缓     * 
     * Channel 配置说明     * - usePlaintext(): 禁用 TLS（内网通信可接受）
     * - keepAliveTime(30s): 30 秒发送一Keep-Alive 探测
     * - keepAliveTimeout(5s): Keep-Alive 响应超时 5      * 
     * @param host 目标主机地址
     * @param port 目标 gRPC 端口
     * @return ManagedChannel 实例（可复用     */
    public ManagedChannel getChannel(String host, int port) {
        String key = host + ":" + port;
        return channelCache.computeIfAbsent(key, k -> {
            logger.info("创建 gRPC Channel: {}", key);
            return ManagedChannelBuilder.forAddress(host, port)
                    .usePlaintext()  // 内网通信禁用 TLS
                    .keepAliveTime(30, TimeUnit.SECONDS)  // Keep-Alive 间隔
                    .keepAliveTimeout(5, TimeUnit.SECONDS)  // Keep-Alive 超时
                    .build();
        });
    }

    /**
     * 销毁方法（Spring 容器关闭前自动调用）
     * 
     * 执行流程     * 1. 遍历所有缓存的 Channel
     * 2. 调用 shutdownNow() 立即关闭连接
     * 3. 等待最5 秒让连接优雅关闭
     * 4. 清空缓存
     * 
     * 注意     * - shutdownNow() 会立即中断进行中的调     * - awaitTermination() 等待底层资源释放
     * - InterruptedException 需要恢复中断状     */
    @PreDestroy
    public void shutdown() {
        logger.info("关闭所gRPC Channel: count={}", channelCache.size());
        channelCache.values().forEach(channel -> {
            try {
                channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        channelCache.clear();
    }
}