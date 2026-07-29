package com.businesslogic.vo;

import java.util.List;
import java.util.Objects;

public class FilterItemVO {

    private Long id;

    private String type;

    private String logicOperator;

    private String functionCategory;

    private String filterFunction;

    private List<OperandVO> operands;

    private Integer level;

    private List<FilterItemVO> items;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLogicOperator() {
        return logicOperator;
    }

    public void setLogicOperator(String logicOperator) {
        this.logicOperator = logicOperator;
    }

    public String getFunctionCategory() {
        return functionCategory;
    }

    public void setFunctionCategory(String functionCategory) {
        this.functionCategory = functionCategory;
    }

    public String getFilterFunction() {
        return filterFunction;
    }

    public void setFilterFunction(String filterFunction) {
        this.filterFunction = filterFunction;
    }

    public List<OperandVO> getOperands() {
        return operands;
    }

    public void setOperands(List<OperandVO> operands) {
        this.operands = operands;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public List<FilterItemVO> getItems() {
        return items;
    }

    public void setItems(List<FilterItemVO> items) {
        this.items = items;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FilterItemVO that = (FilterItemVO) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(type, that.type) &&
                Objects.equals(logicOperator, that.logicOperator) &&
                Objects.equals(functionCategory, that.functionCategory) &&
                Objects.equals(filterFunction, that.filterFunction) &&
                Objects.equals(operands, that.operands) &&
                Objects.equals(level, that.level) &&
                Objects.equals(items, that.items);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, logicOperator, functionCategory, filterFunction, operands, level, items);
    }

    @Override
    public String toString() {
        return "FilterItemVO{" +
                "id=" + id +
                ", type='" + type + '\'' +
                ", logicOperator='" + logicOperator + '\'' +
                ", functionCategory='" + functionCategory + '\'' +
                ", filterFunction='" + filterFunction + '\'' +
                ", operands=" + operands +
                ", level=" + level +
                ", items=" + items +
                '}';
    }
}
