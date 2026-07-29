package com.businesslogic.openfeign.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

/**
 * 单阶段同步请求（兼容旧接口，向后兼容 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SyncRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String syncId;
    private String transactionCode;
    private String featureCode;
    private Long version;
    private String expression;
    private Long timestamp;

    public SyncRequest() {
    }

    public SyncRequest(String syncId, String transactionCode, String featureCode, Long version, String expression, Long timestamp) {
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

        SyncRequest that = (SyncRequest) o;

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
        return "SyncRequest{" +
                "syncId='" + syncId + '\'' +
                ", transactionCode='" + transactionCode + '\'' +
                ", featureCode='" + featureCode + '\'' +
                ", version=" + version +
                ", expression='" + expression + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }

    public static SyncRequestBuilder builder() {
        return new SyncRequestBuilder();
    }

    public static class SyncRequestBuilder {
        private String syncId;
        private String transactionCode;
        private String featureCode;
        private Long version;
        private String expression;
        private Long timestamp;

        SyncRequestBuilder() {
        }

        public SyncRequestBuilder syncId(String syncId) {
            this.syncId = syncId;
            return this;
        }

        public SyncRequestBuilder transactionCode(String transactionCode) {
            this.transactionCode = transactionCode;
            return this;
        }

        public SyncRequestBuilder featureCode(String featureCode) {
            this.featureCode = featureCode;
            return this;
        }

        public SyncRequestBuilder version(Long version) {
            this.version = version;
            return this;
        }

        public SyncRequestBuilder expression(String expression) {
            this.expression = expression;
            return this;
        }

        public SyncRequestBuilder timestamp(Long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public SyncRequest build() {
            return new SyncRequest(syncId, transactionCode, featureCode, version, expression, timestamp);
        }

        @Override
        public String toString() {
            return "SyncRequest.SyncRequestBuilder{" +
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
