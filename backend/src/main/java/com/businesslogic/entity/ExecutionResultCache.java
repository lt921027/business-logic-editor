package com.businesslogic.entity;

import java.time.LocalDateTime;

public class ExecutionResultCache {

    private Long id;

    private Long businessLogicId;

    private String inputDataHash;

    private String resultData;

    private Integer executionTimeMs;

    private Boolean success;

    private String errorMessage;

    private LocalDateTime expireTime;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBusinessLogicId() {
        return businessLogicId;
    }

    public void setBusinessLogicId(Long businessLogicId) {
        this.businessLogicId = businessLogicId;
    }

    public String getInputDataHash() {
        return inputDataHash;
    }

    public void setInputDataHash(String inputDataHash) {
        this.inputDataHash = inputDataHash;
    }

    public String getResultData() {
        return resultData;
    }

    public void setResultData(String resultData) {
        this.resultData = resultData;
    }

    public Integer getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(Integer executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ExecutionResultCache that = (ExecutionResultCache) o;

        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (businessLogicId != null ? !businessLogicId.equals(that.businessLogicId) : that.businessLogicId != null) return false;
        if (inputDataHash != null ? !inputDataHash.equals(that.inputDataHash) : that.inputDataHash != null) return false;
        if (resultData != null ? !resultData.equals(that.resultData) : that.resultData != null) return false;
        if (executionTimeMs != null ? !executionTimeMs.equals(that.executionTimeMs) : that.executionTimeMs != null) return false;
        if (success != null ? !success.equals(that.success) : that.success != null) return false;
        if (errorMessage != null ? !errorMessage.equals(that.errorMessage) : that.errorMessage != null) return false;
        if (expireTime != null ? !expireTime.equals(that.expireTime) : that.expireTime != null) return false;
        if (createdAt != null ? !createdAt.equals(that.createdAt) : that.createdAt != null) return false;
        return updatedAt != null ? updatedAt.equals(that.updatedAt) : that.updatedAt == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (businessLogicId != null ? businessLogicId.hashCode() : 0);
        result = 31 * result + (inputDataHash != null ? inputDataHash.hashCode() : 0);
        result = 31 * result + (resultData != null ? resultData.hashCode() : 0);
        result = 31 * result + (executionTimeMs != null ? executionTimeMs.hashCode() : 0);
        result = 31 * result + (success != null ? success.hashCode() : 0);
        result = 31 * result + (errorMessage != null ? errorMessage.hashCode() : 0);
        result = 31 * result + (expireTime != null ? expireTime.hashCode() : 0);
        result = 31 * result + (createdAt != null ? createdAt.hashCode() : 0);
        result = 31 * result + (updatedAt != null ? updatedAt.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ExecutionResultCache{" +
                "id=" + id +
                ", businessLogicId=" + businessLogicId +
                ", inputDataHash='" + inputDataHash + '\'' +
                ", resultData='" + resultData + '\'' +
                ", executionTimeMs=" + executionTimeMs +
                ", success=" + success +
                ", errorMessage='" + errorMessage + '\'' +
                ", expireTime=" + expireTime +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
