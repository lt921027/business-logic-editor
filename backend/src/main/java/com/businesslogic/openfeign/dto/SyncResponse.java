package com.businesslogic.openfeign.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

/**
 * 单阶段同步响 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SyncResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String syncId;
    private String podId;
    private String status;
    private String message;
    private Long costMs;

    public SyncResponse() {
    }

    public SyncResponse(String syncId, String podId, String status, String message, Long costMs) {
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

        SyncResponse that = (SyncResponse) o;

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
        return "SyncResponse{" +
                "syncId='" + syncId + '\'' +
                ", podId='" + podId + '\'' +
                ", status='" + status + '\'' +
                ", message='" + message + '\'' +
                ", costMs=" + costMs +
                '}';
    }

    public static SyncResponseBuilder builder() {
        return new SyncResponseBuilder();
    }

    public static class SyncResponseBuilder {
        private String syncId;
        private String podId;
        private String status;
        private String message;
        private Long costMs;

        SyncResponseBuilder() {
        }

        public SyncResponseBuilder syncId(String syncId) {
            this.syncId = syncId;
            return this;
        }

        public SyncResponseBuilder podId(String podId) {
            this.podId = podId;
            return this;
        }

        public SyncResponseBuilder status(String status) {
            this.status = status;
            return this;
        }

        public SyncResponseBuilder message(String message) {
            this.message = message;
            return this;
        }

        public SyncResponseBuilder costMs(Long costMs) {
            this.costMs = costMs;
            return this;
        }

        public SyncResponse build() {
            return new SyncResponse(syncId, podId, status, message, costMs);
        }

        @Override
        public String toString() {
            return "SyncResponse.SyncResponseBuilder{" +
                    "syncId='" + syncId + '\'' +
                    ", podId='" + podId + '\'' +
                    ", status='" + status + '\'' +
                    ", message='" + message + '\'' +
                    ", costMs=" + costMs +
                    '}';
        }
    }
}
