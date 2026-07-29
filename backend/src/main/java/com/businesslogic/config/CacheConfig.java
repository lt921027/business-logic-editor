package com.businesslogic.config;

import java.time.LocalDateTime;

public class CacheConfig {

    private Long id;

    private String configKey;

    private String configValue;

    private String configType;

    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigValue() {
        return configValue;
    }

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }

    public String getConfigType() {
        return configType;
    }

    public void setConfigType(String configType) {
        this.configType = configType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

        CacheConfig that = (CacheConfig) o;

        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (configKey != null ? !configKey.equals(that.configKey) : that.configKey != null) return false;
        if (configValue != null ? !configValue.equals(that.configValue) : that.configValue != null) return false;
        if (configType != null ? !configType.equals(that.configType) : that.configType != null) return false;
        if (description != null ? !description.equals(that.description) : that.description != null) return false;
        if (createdAt != null ? !createdAt.equals(that.createdAt) : that.createdAt != null) return false;
        return updatedAt != null ? updatedAt.equals(that.updatedAt) : that.updatedAt == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (configKey != null ? configKey.hashCode() : 0);
        result = 31 * result + (configValue != null ? configValue.hashCode() : 0);
        result = 31 * result + (configType != null ? configType.hashCode() : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (createdAt != null ? createdAt.hashCode() : 0);
        result = 31 * result + (updatedAt != null ? updatedAt.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "CacheConfig{" +
                "id=" + id +
                ", configKey='" + configKey + '\'' +
                ", configValue='" + configValue + '\'' +
                ", configType='" + configType + '\'' +
                ", description='" + description + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
