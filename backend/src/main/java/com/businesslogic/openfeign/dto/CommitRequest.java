package com.businesslogic.openfeign.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

/**
 * Commit 阶段请求PC 阶段二：原子切换主缓存）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommitRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String syncId;
    private String transactionCode;
    private String featureCode;
    private Long timestamp;

    public CommitRequest() {
    }

    public CommitRequest(String syncId, String transactionCode, String featureCode, Long timestamp) {
        this.syncId = syncId;
        this.transactionCode = transactionCode;
        this.featureCode = featureCode;
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

        CommitRequest that = (CommitRequest) o;

        if (syncId != null ? !syncId.equals(that.syncId) : that.syncId != null) return false;
        if (transactionCode != null ? !transactionCode.equals(that.transactionCode) : that.transactionCode != null)
            return false;
        if (featureCode != null ? !featureCode.equals(that.featureCode) : that.featureCode != null) return false;
        return timestamp != null ? timestamp.equals(that.timestamp) : that.timestamp == null;
    }

    @Override
    public int hashCode() {
        int result = syncId != null ? syncId.hashCode() : 0;
        result = 31 * result + (transactionCode != null ? transactionCode.hashCode() : 0);
        result = 31 * result + (featureCode != null ? featureCode.hashCode() : 0);
        result = 31 * result + (timestamp != null ? timestamp.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "CommitRequest{" +
                "syncId='" + syncId + '\'' +
                ", transactionCode='" + transactionCode + '\'' +
                ", featureCode='" + featureCode + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }

    public static CommitRequestBuilder builder() {
        return new CommitRequestBuilder();
    }

    public static class CommitRequestBuilder {
        private String syncId;
        private String transactionCode;
        private String featureCode;
        private Long timestamp;

        CommitRequestBuilder() {
        }

        public CommitRequestBuilder syncId(String syncId) {
            this.syncId = syncId;
            return this;
        }

        public CommitRequestBuilder transactionCode(String transactionCode) {
            this.transactionCode = transactionCode;
            return this;
        }

        public CommitRequestBuilder featureCode(String featureCode) {
            this.featureCode = featureCode;
            return this;
        }

        public CommitRequestBuilder timestamp(Long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public CommitRequest build() {
            return new CommitRequest(syncId, transactionCode, featureCode, timestamp);
        }

        @Override
        public String toString() {
            return "CommitRequest.CommitRequestBuilder{" +
                    "syncId='" + syncId + '\'' +
                    ", transactionCode='" + transactionCode + '\'' +
                    ", featureCode='" + featureCode + '\'' +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }
}
