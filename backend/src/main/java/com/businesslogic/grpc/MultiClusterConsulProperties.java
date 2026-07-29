package com.businesslogic.grpc;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Consul 集群配置
 *
 * 配置示例（application.yml）：
 * <pre>
 * consul:
 *   service-name: business-logic-editor
 *   watch-timeout: 10
 *   watch-interval: 15
 *   # 兼容旧版单集群配置（clusters 为空时使用）
 *   host: localhost
 *   port: 8500
 *   # 新增多集群配 *   clusters:
 *     - name: cluster-a
 *       host: 10.0.1.10
 *       port: 8500
 *       datacenter: dc1
 *       enabled: true
 *     - name: cluster-b
 *       host: 10.0.2.10
 *       port: 8500
 *       datacenter: dc2
 *       enabled: true
 * </pre>
 *
 * 设计说明 * - 集群名（name）作为全局唯一标识，用于日志、事件、聚合视 * - 任意集群宕机不影响其他集群独立工作（故障隔离 * - clusters 为空时自动降级为单集群模式（使用 consul.host/port */
@Configuration
@ConfigurationProperties(prefix = "consul")
public class MultiClusterConsulProperties {

    /** 全局：目标服务名（所有集群共用） */
    private String serviceName = "business-logic-editor";

    /** Blocking Query 阻塞等待时间（秒*/
    private int watchTimeout = 10;

    /** 单集群兜底配置（clusters 为空时使用） */
    private String host = "localhost";
    private int port = 8500;

    /** 多集群配*/
    private List<ClusterConfig> clusters = new ArrayList<>();

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public int getWatchTimeout() {
        return watchTimeout;
    }

    public void setWatchTimeout(int watchTimeout) {
        this.watchTimeout = watchTimeout;
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

    public List<ClusterConfig> getClusters() {
        return clusters;
    }

    public void setClusters(List<ClusterConfig> clusters) {
        this.clusters = clusters;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MultiClusterConsulProperties that = (MultiClusterConsulProperties) o;
        return watchTimeout == that.watchTimeout &&
                port == that.port &&
                Objects.equals(serviceName, that.serviceName) &&
                Objects.equals(host, that.host) &&
                Objects.equals(clusters, that.clusters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceName, watchTimeout, host, port, clusters);
    }

    @Override
    public String toString() {
        return "MultiClusterConsulProperties{" +
                "serviceName='" + serviceName + '\'' +
                ", watchTimeout=" + watchTimeout +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", clusters=" + clusters +
                '}';
    }

    public static class ClusterConfig {
        /** 集群逻辑名（必填，全局唯一*/
        private String name;
        /** Consul 节点地址 */
        private String host;
        /** Consul 端口 */
        private int port = 8500;
        /** Consul 数据中心标识（仅用于日志*/
        private String datacenter = "default";
        /** 是否启用此集*/
        private boolean enabled = true;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
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

        public String getDatacenter() {
            return datacenter;
        }

        public void setDatacenter(String datacenter) {
            this.datacenter = datacenter;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ClusterConfig that = (ClusterConfig) o;
            return port == that.port &&
                    enabled == that.enabled &&
                    Objects.equals(name, that.name) &&
                    Objects.equals(host, that.host) &&
                    Objects.equals(datacenter, that.datacenter);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, host, port, datacenter, enabled);
        }

        @Override
        public String toString() {
            return "ClusterConfig{" +
                    "name='" + name + '\'' +
                    ", host='" + host + '\'' +
                    ", port=" + port +
                    ", datacenter='" + datacenter + '\'' +
                    ", enabled=" + enabled +
                    '}';
        }
    }
}
