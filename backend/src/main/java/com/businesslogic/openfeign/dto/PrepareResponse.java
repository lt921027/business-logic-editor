package com.businesslogic.openfeign.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

/**
 * Prepare 阶段响应
 *
 * 状态码说明（与 gRPC SyncStatus 对齐）：
 * - PREPARE_OK       编译并暂存成 * - PREPARE_FAILED   编译失败或未预期异常
 * - ALREADY_EXISTS   幂等：当syncId 已在待激活区
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PrepareResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String syncId;
    private String podId;
    private String status;
    private String message;
    /** 服务端处理耗时（不含网络），单ms */
    private Long costMs;

    public PrepareResponse() {
    }

    public PrepareResponse(String syncId, String podId, String status, String message, Long costMs) {
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

        PrepareResponse that = (PrepareResponse) o;

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
        return "PrepareResponse{" +
                "syncId='" + syncId + '\'' +
                ", podId='" + podId + '\'' +
                ", status='" + status + '\'' +
                ", message='" + message + '\'' +
                ", costMs=" + costMs +
                '}';
    }

    public static PrepareResponseBuilder builder() {
        return new PrepareResponseBuilder();
    }

    public static class PrepareResponseBuilder {
        private String syncId;
        private String podId;
        private String status;
        private String message;
        private Long costMs;

        PrepareResponseBuilder() {
        }

        public PrepareResponseBuilder syncId(String syncId) {
            this.syncId = syncId;
            return this;
        }

        public PrepareResponseBuilder podId(String podId) {
            this.podId = podId;
            return this;
        }

        public PrepareResponseBuilder status(String status) {
            this.status = status;
            return this;
        }

        public PrepareResponseBuilder message(String message) {
            this.message = message;
            return this;
        }

        public PrepareResponseBuilder costMs(Long costMs) {
            this.costMs = costMs;
            return this;
        }

        public PrepareResponse build() {
            return new PrepareResponse(syncId, podId, status, message, costMs);
        }

        @Override
        public String toString() {
            return "PrepareResponse.PrepareResponseBuilder{" +
                    "syncId='" + syncId + '\'' +
                    ", podId='" + podId + '\'' +
                    ", status='" + status + '\'' +
                    ", message='" + message + '\'' +
                    ", costMs=" + costMs +
                    '}';
        }
    }
}
