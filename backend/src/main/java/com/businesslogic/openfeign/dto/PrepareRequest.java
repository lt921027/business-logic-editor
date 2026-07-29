package com.businesslogic.openfeign.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

/**
 * Prepare 阶段请求PC 阶段一：编译并暂存 *
 * 字段gRPC PrepareRequest 保持一致，便于后续迁移对比 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PrepareRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 全局唯一同步 ID（发起方生成），用于 2PC 关联 */
    private String syncId;

    /** 交易*/
    private String transactionCode;

    /** 功能*/
    private String featureCode;

    /** 版本号（-1L 表示删除*/
    private Long version;

    /** Aviator 表达式源码（删除时可为空*/
    private String expression;

    /** 发起时间戳（毫秒），用于链路耗时分析 */
    private Long timestamp;

    public PrepareRequest() {
    }

    public PrepareRequest(String syncId, String transactionCode, String featureCode, Long version, String expression, Long timestamp) {
        this.syncId = syncId;
        this.transactionCode = transactionCode;
        this.featureCode = featureCode;
        this.version = version;
        this.expression = expression;
        this.timestamp = timestamp;
    }

    public String getSyncId() {
        return syncId;
    }

    public void setSyncId(String syncId) {
        this.syncId = syncId;
    }

    public String getTransactionCode() {
        return transactionCode;
    }

    public void setTransactionCode(String transactionCode) {
        this.transactionCode = transactionCode;
    }

    public String getFeatureCode() {
        return featureCode;
    }

    public void setFeatureCode(String featureCode) {
        this.featureCode = featureCode;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PrepareRequest that = (PrepareRequest) o;

        if (syncId != null ? !syncId.equals(that.syncId) : that.syncId != null) return false;
        if (transactionCode != null ? !transactionCode.equals(that.transactionCode) : that.transactionCode != null)
            return false;
        if (featureCode != null ? !featureCode.equals(that.featureCode) : that.featureCode != null) return false;
        if (version != null ? !version.equals(that.version) : that.version != null) return false;
        if (expression != null ? !expression.equals(that.expression) : that.expression != null) return false;
        return timestamp != null ? timestamp.equals(that.timestamp) : that.timestamp == null;
    }

    @Override
    public int hashCode() {
        int result = syncId != null ? syncId.hashCode() : 0;
        result = 31 * result + (transactionCode != null ? transactionCode.hashCode() : 0);
        result = 31 * result + (featureCode != null ? featureCode.hashCode() : 0);
        result = 31 * result + (version != null ? version.hashCode() : 0);
        result = 31 * result + (expression != null ? expression.hashCode() : 0);
        result = 31 * result + (timestamp != null ? timestamp.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "PrepareRequest{" +
                "syncId='" + syncId + '\'' +
                ", transactionCode='" + transactionCode + '\'' +
                ", featureCode='" + featureCode + '\'' +
                ", version=" + version +
                ", expression='" + expression + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }

    public static PrepareRequestBuilder builder() {
        return new PrepareRequestBuilder();
    }

    public static class PrepareRequestBuilder {
        private String syncId;
        private String transactionCode;
        private String featureCode;
        private Long version;
        private String expression;
        private Long timestamp;

        PrepareRequestBuilder() {
        }

        public PrepareRequestBuilder syncId(String syncId) {
            this.syncId = syncId;
            return this;
        }

        public PrepareRequestBuilder transactionCode(String transactionCode) {
            this.transactionCode = transactionCode;
            return this;
        }

        public PrepareRequestBuilder featureCode(String featureCode) {
            this.featureCode = featureCode;
            return this;
        }

        public PrepareRequestBuilder version(Long version) {
            this.version = version;
            return this;
        }

        public PrepareRequestBuilder expression(String expression) {
            this.expression = expression;
            return this;
        }

        public PrepareRequestBuilder timestamp(Long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public PrepareRequest build() {
            return new PrepareRequest(syncId, transactionCode, featureCode, version, expression, timestamp);
        }

        @Override
        public String toString() {
            return "PrepareRequest.PrepareRequestBuilder{" +
                    "syncId='" + syncId + '\'' +
                    ", transactionCode='" + transactionCode + '\'' +
                    ", featureCode='" + featureCode + '\'' +
                    ", version=" + version +
                    ", expression='" + expression + '\'' +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }
}
