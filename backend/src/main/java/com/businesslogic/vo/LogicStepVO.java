package com.businesslogic.vo;

import java.util.List;
import java.util.Objects;

public class LogicStepVO {

    private Long id;

    private Integer stepOrder;

    /**
     * 函数分类
     * 查询接口返回字符串类型，用于前端页面展示
     * 保存时由前端转换为数组格     */
    private String functionCategory;

    private String field;

    private String functionName;

    private List<String> params;

    private String customExpression;

    private String outputVar;

    private String comment;

    private String filterScope;

    private String mappedField;

    private List<CalculationStepVO> calculationSteps;

    private List<FilterItemVO> filterItems;

    private List<FilterLogicVO> filterLogic;

    private List<FilterLogicVO> reverseLogic;

    private Boolean collapsed;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public List<String> getParams() {
        return params;
    }

    public void setParams(List<String> params) {
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

    public List<CalculationStepVO> getCalculationSteps() {
        return calculationSteps;
    }

    public void setCalculationSteps(List<CalculationStepVO> calculationSteps) {
        this.calculationSteps = calculationSteps;
    }

    public List<FilterItemVO> getFilterItems() {
        return filterItems;
    }

    public void setFilterItems(List<FilterItemVO> filterItems) {
        this.filterItems = filterItems;
    }

    public List<FilterLogicVO> getFilterLogic() {
        return filterLogic;
    }

    public void setFilterLogic(List<FilterLogicVO> filterLogic) {
        this.filterLogic = filterLogic;
    }

    public List<FilterLogicVO> getReverseLogic() {
        return reverseLogic;
    }

    public void setReverseLogic(List<FilterLogicVO> reverseLogic) {
        this.reverseLogic = reverseLogic;
    }

    public Boolean getCollapsed() {
        return collapsed;
    }

    public void setCollapsed(Boolean collapsed) {
        this.collapsed = collapsed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LogicStepVO that = (LogicStepVO) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(stepOrder, that.stepOrder) &&
                Objects.equals(functionCategory, that.functionCategory) &&
                Objects.equals(field, that.field) &&
                Objects.equals(functionName, that.functionName) &&
                Objects.equals(params, that.params) &&
                Objects.equals(customExpression, that.customExpression) &&
                Objects.equals(outputVar, that.outputVar) &&
                Objects.equals(comment, that.comment) &&
                Objects.equals(filterScope, that.filterScope) &&
                Objects.equals(mappedField, that.mappedField) &&
                Objects.equals(calculationSteps, that.calculationSteps) &&
                Objects.equals(filterItems, that.filterItems) &&
                Objects.equals(filterLogic, that.filterLogic) &&
                Objects.equals(reverseLogic, that.reverseLogic) &&
                Objects.equals(collapsed, that.collapsed);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, stepOrder, functionCategory, field, functionName, params, customExpression, outputVar, comment, filterScope, mappedField, calculationSteps, filterItems, filterLogic, reverseLogic, collapsed);
    }

    @Override
    public String toString() {
        return "LogicStepVO{" +
                "id=" + id +
                ", stepOrder=" + stepOrder +
                ", functionCategory='" + functionCategory + '\'' +
                ", field='" + field + '\'' +
                ", functionName='" + functionName + '\'' +
                ", params=" + params +
                ", customExpression='" + customExpression + '\'' +
                ", outputVar='" + outputVar + '\'' +
                ", comment='" + comment + '\'' +
                ", filterScope='" + filterScope + '\'' +
                ", mappedField='" + mappedField + '\'' +
                ", calculationSteps=" + calculationSteps +
                ", filterItems=" + filterItems +
                ", filterLogic=" + filterLogic +
                ", reverseLogic=" + reverseLogic +
                ", collapsed=" + collapsed +
                '}';
    }
}
