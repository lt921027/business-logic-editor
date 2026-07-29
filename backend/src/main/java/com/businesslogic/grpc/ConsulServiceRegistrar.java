package com.businesslogic.grpc;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.agent.model.NewService;
import org.springframework.beans.factory.annotation.Value;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.UUID;

/**
 * Consul 服务注册 *
 * 核心职责 * - 在应用启动时自动注册服务Consul
 * - 在应用关闭时自动注销服务
 * - 提供健康检查配 *
 * 注册流程 * 1. 应用启动时（@PostConstruct）创建服务注册请 * 2. 注册服务Consul Agent
 * 3. 应用关闭时（@PreDestroy）注销服务
 *
 * 配置项（application.yml）：
 * - consul.host: Consul 地址（默localhost * - consul.port: Consul 端口（默8500 * - consul.service-name: 服务名称（默business-logic-editor * - server.port: HTTP 端口（用于健康检查）
 * - grpc.server.port: gRPC 端口（服务注册端口）
 */
@Component
public class ConsulServiceRegistrar {

    private static final Logger logger = LoggerFactory.getLogger(ConsulServiceRegistrar.class);

    /** Consul 服务地址 */
    @Value("${consul.host:localhost}")
    private String consulHost;

    /** Consul 服务端口 */
    @Value("${consul.port:8500}")
    private int consulPort;

    /** 服务名称 */
    @Value("${consul.service-name:business-logic-editor}")
    private String serviceName;

    /** HTTP 端口（用于健康检查） */
    @Value("${server.port:8080}")
    private int serverPort;

    /** gRPC 端口（服务注册端口） */
    @Value("${grpc.server.port:9090}")
    private int grpcPort;

    /** Consul Java 客户*/
    private ConsulClient consulClient;

    /** 服务注册 ID（唯一标识*/
    private String serviceId;

    /**
     * 注册服务（应用启动时自动调用     */
    @PostConstruct
    public void register() {
        try {
            consulClient = new ConsulClient(consulHost, consulPort);

            // 生成唯一服务 ID
            serviceId = serviceName + "-" + UUID.randomUUID().toString().substring(0, 8);

            // 构建服务注册请求
            NewService newService = new NewService();
            newService.setId(serviceId);
            newService.setName(serviceName);
            newService.setPort(grpcPort);  // 使用 gRPC 端口注册
            newService.setAddress("127.0.0.1");  // 显式设置本地地址

            // 添加健康检查（HTTP 检查）
            NewService.Check check = new NewService.Check();
            check.setHttp("http://localhost:" + serverPort + "/api/grpc/test/health");
            check.setInterval("10s");
            check.setTimeout("5s");
            check.setDeregisterCriticalServiceAfter("30s");
            newService.setCheck(check);

            // 注册服务
            consulClient.agentServiceRegister(newService);

            logger.info("服务注册成功: service={}, id={}, port={}", serviceName, serviceId, grpcPort);
        } catch (Exception e) {
            logger.error("服务注册失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 注销服务（应用关闭时自动调用     */
    @PreDestroy
    public void deregister() {
        if (consulClient != null && serviceId != null) {
            try {
                consulClient.agentServiceDeregister(serviceId);
                logger.info("服务注销成功: service={}, id={}", serviceName, serviceId);
            } catch (Exception e) {
                logger.error("服务注销失败: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * 获取服务注册 ID
     */
    public String getServiceId() {
        return serviceId;
    }
}