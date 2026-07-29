package com.businesslogic.openfeign.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

/**
 * Abort 阶段请求PC 回滚：清理待激活区 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AbortRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String syncId;
    private String transactionCode;
    private String featureCode;
    private String reason;
    private Long timestamp;

    public AbortRequest() {
    }

    public AbortRequest(String syncId, String transactionCode, String featureCode, String reason, Long timestamp) {
        this.syncId = syncId;
        this.transactionCode = transactionCode;
        this.featureCode = featureCode;
        this.reason = reason;
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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
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

        AbortRequest that = (AbortRequest) o;

        if (syncId != null ? !syncId.equals(that.syncId) : that.syncId != null) return false;
        if (transactionCode != null ? !transactionCode.equals(that.transactionCode) : that.transactionCode != null)
            return false;
        if (featureCode != null ? !featureCode.equals(that.featureCode) : that.featureCode != null) return false;
        if (reason != null ? !reason.equals(that.reason) : that.reason != null) return false;
        return timestamp != null ? timestamp.equals(that.timestamp) : that.timestamp == null;
    }

    @Override
    public int hashCode() {
        int result = syncId != null ? syncId.hashCode() : 0;
        result = 31 * result + (transactionCode != null ? transactionCode.hashCode() : 0);
        result = 31 * result + (featureCode != null ? featureCode.hashCode() : 0);
        result = 31 * result + (reason != null ? reason.hashCode() : 0);
        result = 31 * result + (timestamp != null ? timestamp.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "AbortRequest{" +
                "syncId='" + syncId + '\'' +
                ", transactionCode='" + transactionCode + '\'' +
                ", featureCode='" + featureCode + '\'' +
                ", reason='" + reason + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }

    public static AbortRequestBuilder builder() {
        return new AbortRequestBuilder();
    }

    public static class AbortRequestBuilder {
        private String syncId;
        private String transactionCode;
        private String featureCode;
        private String reason;
        private Long timestamp;

        AbortRequestBuilder() {
        }

        public AbortRequestBuilder syncId(String syncId) {
            this.syncId = syncId;
            return this;
        }

        public AbortRequestBuilder transactionCode(String transactionCode) {
            this.transactionCode = transactionCode;
            return this;
        }

        public AbortRequestBuilder featureCode(String featureCode) {
            this.featureCode = featureCode;
            return this;
        }

        public AbortRequestBuilder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public AbortRequestBuilder timestamp(Long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public AbortRequest build() {
            return new AbortRequest(syncId, transactionCode, featureCode, reason, timestamp);
        }

        @Override
        public String toString() {
            return "AbortRequest.AbortRequestBuilder{" +
                    "syncId='" + syncId + '\'' +
                    ", transactionCode='" + transactionCode + '\'' +
                    ", featureCode='" + featureCode + '\'' +
                    ", reason='" + reason + '\'' +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }
}
