package com.businesslogic.vo;

import java.util.List;
import java.util.Objects;

public class CalculationStepVO {

    private Long id;

    private String logicOperator;

    private String functionCategory;

    private String filterFunction;

    private List<OperandVO> operands;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CalculationStepVO that = (CalculationStepVO) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(logicOperator, that.logicOperator) &&
                Objects.equals(functionCategory, that.functionCategory) &&
                Objects.equals(filterFunction, that.filterFunction) &&
                Objects.equals(operands, that.operands);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, logicOperator, functionCategory, filterFunction, operands);
    }

    @Override
    public String toString() {
        return "CalculationStepVO{" +
                "id=" + id +
                ", logicOperator='" + logicOperator + '\'' +
                ", functionCategory='" + functionCategory + '\'' +
                ", filterFunction='" + filterFunction + '\'' +
                ", operands=" + operands +
                '}';
    }
}
