package com.businesslogic.openfeign.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

/**
 * Abort 阶段响应
 *
 * 状态码 * - ABORT_OK     清理成功
 * - ABORT_NOOP   待激活区无数据（幂等成功 * - ABORT_FAILED 异常
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AbortResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String syncId;
    private String podId;
    private String status;
    private String message;
    private Long costMs;

    public AbortResponse() {
    }

    public AbortResponse(String syncId, String podId, String status, String message, Long costMs) {
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

        AbortResponse that = (AbortResponse) o;

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
        return "AbortResponse{" +
                "syncId='" + syncId + '\'' +
                ", podId='" + podId + '\'' +
                ", status='" + status + '\'' +
                ", message='" + message + '\'' +
                ", costMs=" + costMs +
                '}';
    }

    public static AbortResponseBuilder builder() {
        return new AbortResponseBuilder();
    }

    public static class AbortResponseBuilder {
        private String syncId;
        private String podId;
        private String status;
        private String message;
        private Long costMs;

        AbortResponseBuilder() {
        }

        public AbortResponseBuilder syncId(String syncId) {
            this.syncId = syncId;
            return this;
        }

        public AbortResponseBuilder podId(String podId) {
            this.podId = podId;
            return this;
        }

        public AbortResponseBuilder status(String status) {
            this.status = status;
            return this;
        }

        public AbortResponseBuilder message(String message) {
            this.message = message;
            return this;
        }

        public AbortResponseBuilder costMs(Long costMs) {
            this.costMs = costMs;
            return this;
        }

        public AbortResponse build() {
            return new AbortResponse(syncId, podId, status, message, costMs);
        }

        @Override
        public String toString() {
            return "AbortResponse.AbortResponseBuilder{" +
                    "syncId='" + syncId + '\'' +
                    ", podId='" + podId + '\'' +
                    ", status='" + status + '\'' +
                    ", message='" + message + '\'' +
                    ", costMs=" + costMs +
                    '}';
        }
    }
}
