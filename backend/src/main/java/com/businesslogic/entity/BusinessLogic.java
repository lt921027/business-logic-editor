package com.businesslogic.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

@TableName("business_logic")
public class BusinessLogic {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private String jsonInput;

    private String aviatorExpression;

    private String groovyExpression;

    private Integer stepCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getJsonInput() {
        return jsonInput;
    }

    public void setJsonInput(String jsonInput) {
        this.jsonInput = jsonInput;
    }

    public String getAviatorExpression() {
        return aviatorExpression;
    }

    public void setAviatorExpression(String aviatorExpression) {
        this.aviatorExpression = aviatorExpression;
    }

    public String getGroovyExpression() {
        return groovyExpression;
    }

    public void setGroovyExpression(String groovyExpression) {
        this.groovyExpression = groovyExpression;
    }

    public Integer getStepCount() {
        return stepCount;
    }

    public void setStepCount(Integer stepCount) {
        this.stepCount = stepCount;
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

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BusinessLogic that = (BusinessLogic) o;

        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (description != null ? !description.equals(that.description) : that.description != null) return false;
        if (jsonInput != null ? !jsonInput.equals(that.jsonInput) : that.jsonInput != null) return false;
        if (aviatorExpression != null ? !aviatorExpression.equals(that.aviatorExpression) : that.aviatorExpression != null) return false;
        if (groovyExpression != null ? !groovyExpression.equals(that.groovyExpression) : that.groovyExpression != null) return false;
        if (stepCount != null ? !stepCount.equals(that.stepCount) : that.stepCount != null) return false;
        if (createdAt != null ? !createdAt.equals(that.createdAt) : that.createdAt != null) return false;
        if (updatedAt != null ? !updatedAt.equals(that.updatedAt) : that.updatedAt != null) return false;
        return deleted != null ? deleted.equals(that.deleted) : that.deleted == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (jsonInput != null ? jsonInput.hashCode() : 0);
        result = 31 * result + (aviatorExpression != null ? aviatorExpression.hashCode() : 0);
        result = 31 * result + (groovyExpression != null ? groovyExpression.hashCode() : 0);
        result = 31 * result + (stepCount != null ? stepCount.hashCode() : 0);
        result = 31 * result + (createdAt != null ? createdAt.hashCode() : 0);
        result = 31 * result + (updatedAt != null ? updatedAt.hashCode() : 0);
        result = 31 * result + (deleted != null ? deleted.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "BusinessLogic{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", jsonInput='" + jsonInput + '\'' +
                ", aviatorExpression='" + aviatorExpression + '\'' +
                ", groovyExpression='" + groovyExpression + '\'' +
                ", stepCount=" + stepCount +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", deleted=" + deleted +
                '}';
    }
}
