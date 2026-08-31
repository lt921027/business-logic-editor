package com.businesslogic.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

@TableName("logic_step")
public class LogicStep {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long businessLogicId;

    private Integer stepOrder;

    private String functionCategory;

    private String field;

    private String functionName;

    private String params;

    private String customExpression;

    private String outputVar;

    private String comment;

    private String filterScope;

    private String mappedField;

    private String calculationSteps;

    private String filterItems;

    @TableField("filter_logic")
    private String filterLogics;

    @TableField("reverse_logic")
    private String reverseLogics;

    private Boolean collapsed;

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

    public Long getBusinessLogicId() {
        return businessLogicId;
    }

    public void setBusinessLogicId(Long businessLogicId) {
        this.businessLogicId = businessLogicId;
    }

    public Integer getStepOrder() {
        return stepOrder;
    }

    public void setStepOrder(Integer stepOrder) {
        this.stepOrder = stepOrder;
    }

    public String getFunctionCategory() {
        return functionCategory;
    }

    public void setFunctionCategory(String functionCategory) {
        this.functionCategory = functionCategory;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getFunctionName() {
        return functionName;
    }

    public void setFunctionName(String functionName) {
        this.functionName = functionName;
    }

    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        this.params = params;
    }

    public String getCustomExpression() {
        return customExpression;
    }

    public void setCustomExpression(String customExpression) {
        this.customExpression = customExpression;
    }

    public String getOutputVar() {
        return outputVar;
    }

    public void setOutputVar(String outputVar) {
        this.outputVar = outputVar;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getFilterScope() {
        return filterScope;
    }

    public void setFilterScope(String filterScope) {
        this.filterScope = filterScope;
    }

    public String getMappedField() {
        return mappedField;
    }

    public void setMappedField(String mappedField) {
        this.mappedField = mappedField;
    }

    public String getCalculationSteps() {
        return calculationSteps;
    }

    public void setCalculationSteps(String calculationSteps) {
        this.calculationSteps = calculationSteps;
    }

    public String getFilterItems() {
        return filterItems;
    }

    public void setFilterItems(String filterItems) {
        this.filterItems = filterItems;
    }

    public String getFilterLogics() {
        return filterLogics;
    }

    public void setFilterLogics(String filterLogics) {
        this.filterLogics = filterLogics;
    }

    public String getReverseLogics() {
        return reverseLogics;
    }

    public void setReverseLogics(String reverseLogics) {
        this.reverseLogics = reverseLogics;
    }

    public Boolean getCollapsed() {
        return collapsed;
    }

    public void setCollapsed(Boolean collapsed) {
        this.collapsed = collapsed;
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

        LogicStep logicStep = (LogicStep) o;

        if (id != null ? !id.equals(logicStep.id) : logicStep.id != null) return false;
        if (businessLogicId != null ? !businessLogicId.equals(logicStep.businessLogicId) : logicStep.businessLogicId != null) return false;
        if (stepOrder != null ? !stepOrder.equals(logicStep.stepOrder) : logicStep.stepOrder != null) return false;
        if (functionCategory != null ? !functionCategory.equals(logicStep.functionCategory) : logicStep.functionCategory != null) return false;
        if (field != null ? !field.equals(logicStep.field) : logicStep.field != null) return false;
        if (functionName != null ? !functionName.equals(logicStep.functionName) : logicStep.functionName != null) return false;
        if (params != null ? !params.equals(logicStep.params) : logicStep.params != null) return false;
        if (customExpression != null ? !customExpression.equals(logicStep.customExpression) : logicStep.customExpression != null) return false;
        if (outputVar != null ? !outputVar.equals(logicStep.outputVar) : logicStep.outputVar != null) return false;
        if (comment != null ? !comment.equals(logicStep.comment) : logicStep.comment != null) return false;
        if (filterScope != null ? !filterScope.equals(logicStep.filterScope) : logicStep.filterScope != null) return false;
        if (mappedField != null ? !mappedField.equals(logicStep.mappedField) : logicStep.mappedField != null) return false;
        if (calculationSteps != null ? !calculationSteps.equals(logicStep.calculationSteps) : logicStep.calculationSteps != null) return false;
        if (filterItems != null ? !filterItems.equals(logicStep.filterItems) : logicStep.filterItems != null) return false;
        if (filterLogics != null ? !filterLogics.equals(logicStep.filterLogics) : logicStep.filterLogics != null) return false;
        if (reverseLogics != null ? !reverseLogics.equals(logicStep.reverseLogics) : logicStep.reverseLogics != null) return false;
        if (collapsed != null ? !collapsed.equals(logicStep.collapsed) : logicStep.collapsed != null) return false;
        if (createdAt != null ? !createdAt.equals(logicStep.createdAt) : logicStep.createdAt != null) return false;
        if (updatedAt != null ? !updatedAt.equals(logicStep.updatedAt) : logicStep.updatedAt != null) return false;
        return deleted != null ? deleted.equals(logicStep.deleted) : logicStep.deleted == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (businessLogicId != null ? businessLogicId.hashCode() : 0);
        result = 31 * result + (stepOrder != null ? stepOrder.hashCode() : 0);
        result = 31 * result + (functionCategory != null ? functionCategory.hashCode() : 0);
        result = 31 * result + (field != null ? field.hashCode() : 0);
        result = 31 * result + (functionName != null ? functionName.hashCode() : 0);
        result = 31 * result + (params != null ? params.hashCode() : 0);
        result = 31 * result + (customExpression != null ? customExpression.hashCode() : 0);
        result = 31 * result + (outputVar != null ? outputVar.hashCode() : 0);
        result = 31 * result + (comment != null ? comment.hashCode() : 0);
        result = 31 * result + (filterScope != null ? filterScope.hashCode() : 0);
        result = 31 * result + (mappedField != null ? mappedField.hashCode() : 0);
        result = 31 * result + (calculationSteps != null ? calculationSteps.hashCode() : 0);
        result = 31 * result + (filterItems != null ? filterItems.hashCode() : 0);
        result = 31 * result + (filterLogics != null ? filterLogics.hashCode() : 0);
        result = 31 * result + (reverseLogics != null ? reverseLogics.hashCode() : 0);
        result = 31 * result + (collapsed != null ? collapsed.hashCode() : 0);
        result = 31 * result + (createdAt != null ? createdAt.hashCode() : 0);
        result = 31 * result + (updatedAt != null ? updatedAt.hashCode() : 0);
        result = 31 * result + (deleted != null ? deleted.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "LogicStep{" +
                "id=" + id +
                ", businessLogicId=" + businessLogicId +
                ", stepOrder=" + stepOrder +
                ", functionCategory='" + functionCategory + '\'' +
                ", field='" + field + '\'' +
                ", functionName='" + functionName + '\'' +
                ", params='" + params + '\'' +
                ", customExpression='" + customExpression + '\'' +
                ", outputVar='" + outputVar + '\'' +
                ", comment='" + comment + '\'' +
                ", filterScope='" + filterScope + '\'' +
                ", mappedField='" + mappedField + '\'' +
                ", calculationSteps='" + calculationSteps + '\'' +
                ", filterItems='" + filterItems + '\'' +
                ", filterLogics='" + filterLogics + '\'' +
                ", reverseLogics='" + reverseLogics + '\'' +
                ", collapsed=" + collapsed +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", deleted=" + deleted +
                '}';
    }
}
