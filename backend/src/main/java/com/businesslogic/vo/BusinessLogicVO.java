package com.businesslogic.vo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public class BusinessLogicVO {

    private Long id;

    private String name;

    private String description;

    private String jsonInput;

    private String aviatorExpression;

    private String groovyExpression;

    private Integer stepCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private List<LogicStepVO> logicSteps;

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

    public List<LogicStepVO> getLogicSteps() {
        return logicSteps;
    }

    public void setLogicSteps(List<LogicStepVO> logicSteps) {
        this.logicSteps = logicSteps;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BusinessLogicVO that = (BusinessLogicVO) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(name, that.name) &&
                Objects.equals(description, that.description) &&
                Objects.equals(jsonInput, that.jsonInput) &&
                Objects.equals(aviatorExpression, that.aviatorExpression) &&
                Objects.equals(groovyExpression, that.groovyExpression) &&
                Objects.equals(stepCount, that.stepCount) &&
                Objects.equals(createdAt, that.createdAt) &&
                Objects.equals(updatedAt, that.updatedAt) &&
                Objects.equals(logicSteps, that.logicSteps);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, jsonInput, aviatorExpression, stepCount, createdAt, updatedAt, logicSteps);
    }

    @Override
    public String toString() {
        return "BusinessLogicVO{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", jsonInput='" + jsonInput + '\'' +
                ", aviatorExpression='" + aviatorExpression + '\'' +
                ", stepCount=" + stepCount +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", logicSteps=" + logicSteps +
                '}';
    }
}
