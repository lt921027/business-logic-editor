package com.businesslogic.grpc;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 多集群场景下的服务实例模 *
 * 关键差异（vs 单集ServiceInstance）：
 * - 多了 clusterName 字段，标识实例来自哪Consul 集群
 * - 多了 datacenter 字段
 * - 提供 globalId() 复合全局唯一标识，避免不同集群下 instanceId 冲突
 * - 显式表达 healthy 状态（Consul 已通过 setPassing(true) 过滤，但保留字段便于扩展 */
public class ClusteredServiceInstance {

    /** 来源集群名（cluster-a、cluster-b*/
    private String clusterName;

    /** Consul 数据中心 */
    private String datacenter;

    /** 服务名（business-logic-editor*/
    private String serviceName;

    /** Consul 注册 ID（单集群内唯一*/
    private String instanceId;

    /** 实例 IP */
    private String host;

    /** 实例端口 */
    private int port;

    /** 是否健康（passing*/
    private boolean healthy;

    /** 实例标签列表 */
    private List<String> tags = Collections.emptyList();

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public String getDatacenter() {
        return datacenter;
    }

    public void setDatacenter(String datacenter) {
        this.datacenter = datacenter;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

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

    public boolean isHealthy() {
        return healthy;
    }

    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClusteredServiceInstance that = (ClusteredServiceInstance) o;
        return port == that.port &&
                healthy == that.healthy &&
                Objects.equals(clusterName, that.clusterName) &&
                Objects.equals(datacenter, that.datacenter) &&
                Objects.equals(serviceName, that.serviceName) &&
                Objects.equals(instanceId, that.instanceId) &&
                Objects.equals(host, that.host) &&
                Objects.equals(tags, that.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clusterName, datacenter, serviceName, instanceId, host, port, healthy, tags);
    }

    @Override
    public String toString() {
        return "ClusteredServiceInstance{" +
                "clusterName='" + clusterName + '\'' +
                ", datacenter='" + datacenter + '\'' +
                ", serviceName='" + serviceName + '\'' +
                ", instanceId='" + instanceId + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", healthy=" + healthy +
                ", tags=" + tags +
                '}';
    }

    /**
     * 复合全局唯一 ID：clusterName + instanceId
     * 用于聚合层做去重
     */
    public String globalId() {
        return clusterName + ":" + instanceId;
    }

    /**
     * 网络地址：host:port（业务调用时使用     */
    public String address() {
        return host + ":" + port;
    }
}
