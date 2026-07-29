package com.businesslogic.grpc;

import com.businesslogic.cache.FeatureExpressionCache;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.springframework.beans.factory.annotation.Autowired;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;

/**
 * gRPC 服务启动 * 
 * 核心职责 * - Spring Boot 应用启动时创建并启动 gRPC Server
 * - 注册 CacheSyncServiceImpl 服务实现
 * - 在应用关闭时优雅关闭 gRPC Server
 * 
 * 启动流程 * 1. Spring 容器初始化完成后（@PostConstruct * 2. 创建 CacheSyncServiceImpl 实例（注FeatureExpressionCache * 3. 使用 ServerBuilder 构建 gRPC Server
 * 4. 注册缓存同步服务
 * 5. 启动 Server 并监听指定端 * 
 * 设计说明 * - 使用 @Component 注册Spring Bean
 * - 端口通过 application.yml 配置（默9090 * - 使用 @PreDestroy 确保应用关闭时释放资 * - Server 实例为类成员变量，便于生命周期管 * 
 * 配置项（application.yml）：
 * - grpc.server.port: gRPC 监听端口（默9090 * 
 * 注意事项 * - gRPC Server Spring Boot HTTP Server 并行运行
 * - 需确保 gRPC 端口不与 HTTP 端口冲突
 * - Server 启动失败会抛IOException，阻止应用启 */
@Component
public class GrpcServer {

    private static final Logger logger = LoggerFactory.getLogger(GrpcServer.class);

    /** gRPC 监听端口 */
    @Value("${grpc.server.port:9090}")
    private int port;

    /** 表达式缓存服务（用于构建 CacheSyncServiceImpl*/
    @Autowired
    private FeatureExpressionCache featureExpressionCache;

    /** gRPC Server 实例 */
    private Server server;

    /**
     * 启动方法（Spring 容器初始化完成后自动调用     * 
     * 执行流程     * 1. 创建 CacheSyncServiceImpl 实例（注featureExpressionCache     * 2. 使用 ServerBuilder.forPort() 构建 Server
     * 3. 注册 CacheSyncServiceImpl 服务
     * 4. 调用 build() start() 启动 Server
     * 5. 记录启动日志（包含端口和 podId     * 
     * @throws IOException Server 启动失败时抛出（如端口被占用     */
    @PostConstruct
    public void start() throws IOException {
        CacheSyncServiceImpl serviceImpl = new CacheSyncServiceImpl(featureExpressionCache);

        server = ServerBuilder.forPort(port)
                .addService(serviceImpl)
                .build()
                .start();

        logger.info("gRPC Server 启动: port={}, podId={}", port, serviceImpl.getPodId());
    }

    /**
     * 停止方法（Spring 容器关闭前自动调用）
     * 
     * 执行流程     * 1. 检Server 是否已创     * 2. 调用 shutdown() 优雅关闭 Server
     * 3. 记录关闭日志
     * 
     * 注意     * - shutdown() 不会立即中断进行中的调用
     * - Server 会等待当前请求处理完成后再关     * - 若需立即关闭，可使用 shutdownNow()
     */
    @PreDestroy
    public void stop() {
        if (server != null) {
            logger.info("gRPC Server 关闭..");
            server.shutdown();
        }
    }
}