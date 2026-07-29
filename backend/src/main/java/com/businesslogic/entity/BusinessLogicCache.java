package com.businesslogic.entity;

import java.time.LocalDateTime;

public class BusinessLogicCache {

    private Long id;

    private Long businessLogicId;

    private String cacheKey;

    private String cacheValue;

    private String cacheType;

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

    public String getCacheKey() {
        return cacheKey;
    }

    public void setCacheKey(String cacheKey) {
        this.cacheKey = cacheKey;
    }

    public String getCacheValue() {
        return cacheValue;
    }

    public void setCacheValue(String cacheValue) {
        this.cacheValue = cacheValue;
    }

    public String getCacheType() {
        return cacheType;
    }

    public void setCacheType(String cacheType) {
        this.cacheType = cacheType;
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

        BusinessLogicCache that = (BusinessLogicCache) o;

        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (businessLogicId != null ? !businessLogicId.equals(that.businessLogicId) : that.businessLogicId != null) return false;
        if (cacheKey != null ? !cacheKey.equals(that.cacheKey) : that.cacheKey != null) return false;
        if (cacheValue != null ? !cacheValue.equals(that.cacheValue) : that.cacheValue != null) return false;
        if (cacheType != null ? !cacheType.equals(that.cacheType) : that.cacheType != null) return false;
        if (expireTime != null ? !expireTime.equals(that.expireTime) : that.expireTime != null) return false;
        if (createdAt != null ? !createdAt.equals(that.createdAt) : that.createdAt != null) return false;
        return updatedAt != null ? updatedAt.equals(that.updatedAt) : that.updatedAt == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (businessLogicId != null ? businessLogicId.hashCode() : 0);
        result = 31 * result + (cacheKey != null ? cacheKey.hashCode() : 0);
        result = 31 * result + (cacheValue != null ? cacheValue.hashCode() : 0);
        result = 31 * result + (cacheType != null ? cacheType.hashCode() : 0);
        result = 31 * result + (expireTime != null ? expireTime.hashCode() : 0);
        result = 31 * result + (createdAt != null ? createdAt.hashCode() : 0);
        result = 31 * result + (updatedAt != null ? updatedAt.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "BusinessLogicCache{" +
                "id=" + id +
                ", businessLogicId=" + businessLogicId +
                ", cacheKey='" + cacheKey + '\'' +
                ", cacheValue='" + cacheValue + '\'' +
                ", cacheType='" + cacheType + '\'' +
                ", expireTime=" + expireTime +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
