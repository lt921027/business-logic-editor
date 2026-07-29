package com.businesslogic.openfeign.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

/**
 * Commit 阶段响应
 *
 * 状态码 * - COMMIT_OK          切换成功
 * - COMMIT_NOT_FOUND   待激活区无数据（可能 Prepare 未执行或Abort * - COMMIT_FAILED      切换异常
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommitResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String syncId;
    private String podId;
    private String status;
    private String message;
    private Long costMs;

    public CommitResponse() {
    }

    public CommitResponse(String syncId, String podId, String status, String message, Long costMs) {
        this.syncId = syncId;
        this.podId = podId;
        this.status = status;
        this.message = message;
        this.costMs = costMs;
    }

    public String getSyncId() {
        return syncId;
    }

    public void setSyncId(String syncId) {
        this.syncId = syncId;
    }

    public String getPodId() {
        return podId;
    }

    public void setPodId(String podId) {
        this.podId = podId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getCostMs() {
        return costMs;
    }

    public void setCostMs(Long costMs) {
        this.costMs = costMs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CommitResponse that = (CommitResponse) o;

        if (syncId != null ? !syncId.equals(that.syncId) : that.syncId != null) return false;
        if (podId != null ? !podId.equals(that.podId) : that.podId != null) return false;
        if (status != null ? !status.equals(that.status) : that.status != null) return false;
        if (message != null ? !message.equals(that.message) : that.message != null) return false;
        return costMs != null ? costMs.equals(that.costMs) : that.costMs == null;
    }

    @Override
    public int hashCode() {
        int result = syncId != null ? syncId.hashCode() : 0;
        result = 31 * result + (podId != null ? podId.hashCode() : 0);
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (message != null ? message.hashCode() : 0);
        result = 31 * result + (costMs != null ? costMs.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "CommitResponse{" +
                "syncId='" + syncId + '\'' +
                ", podId='" + podId + '\'' +
                ", status='" + status + '\'' +
                ", message='" + message + '\'' +
                ", costMs=" + costMs +
                '}';
    }

    public static CommitResponseBuilder builder() {
        return new CommitResponseBuilder();
    }

    public static class CommitResponseBuilder {
        private String syncId;
        private String podId;
        private String status;
        private String message;
        private Long costMs;

        CommitResponseBuilder() {
        }

        public CommitResponseBuilder syncId(String syncId) {
            this.syncId = syncId;
            return this;
        }

        public CommitResponseBuilder podId(String podId) {
            this.podId = podId;
            return this;
        }

        public CommitResponseBuilder status(String status) {
            this.status = status;
            return this;
        }

        public CommitResponseBuilder message(String message) {
            this.message = message;
            return this;
        }

        public CommitResponseBuilder costMs(Long costMs) {
            this.costMs = costMs;
            return this;
        }

        public CommitResponse build() {
            return new CommitResponse(syncId, podId, status, message, costMs);
        }

        @Override
        public String toString() {
            return "CommitResponse.CommitResponseBuilder{" +
                    "syncId='" + syncId + '\'' +
                    ", podId='" + podId + '\'' +
                    ", status='" + status + '\'' +
                    ", message='" + message + '\'' +
                    ", costMs=" + costMs +
                    '}';
        }
    }
}
