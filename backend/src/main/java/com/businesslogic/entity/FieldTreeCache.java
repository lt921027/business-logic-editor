package com.businesslogic.entity;

import java.time.LocalDateTime;

public class FieldTreeCache {

    private Long id;

    private String jsonInputHash;

    private String fieldTreeJson;

    private LocalDateTime expireTime;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getJsonInputHash() {
        return jsonInputHash;
    }

    public void setJsonInputHash(String jsonInputHash) {
        this.jsonInputHash = jsonInputHash;
    }

    public String getFieldTreeJson() {
        return fieldTreeJson;
    }

    public void setFieldTreeJson(String fieldTreeJson) {
        this.fieldTreeJson = fieldTreeJson;
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

        FieldTreeCache that = (FieldTreeCache) o;

        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (jsonInputHash != null ? !jsonInputHash.equals(that.jsonInputHash) : that.jsonInputHash != null) return false;
        if (fieldTreeJson != null ? !fieldTreeJson.equals(that.fieldTreeJson) : that.fieldTreeJson != null) return false;
        if (expireTime != null ? !expireTime.equals(that.expireTime) : that.expireTime != null) return false;
        if (createdAt != null ? !createdAt.equals(that.createdAt) : that.createdAt != null) return false;
        return updatedAt != null ? updatedAt.equals(that.updatedAt) : that.updatedAt == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (jsonInputHash != null ? jsonInputHash.hashCode() : 0);
        result = 31 * result + (fieldTreeJson != null ? fieldTreeJson.hashCode() : 0);
        result = 31 * result + (expireTime != null ? expireTime.hashCode() : 0);
        result = 31 * result + (createdAt != null ? createdAt.hashCode() : 0);
        result = 31 * result + (updatedAt != null ? updatedAt.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "FieldTreeCache{" +
                "id=" + id +
                ", jsonInputHash='" + jsonInputHash + '\'' +
                ", fieldTreeJson='" + fieldTreeJson + '\'' +
                ", expireTime=" + expireTime +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
