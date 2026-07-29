package com.businesslogic.dto;

import java.util.Arrays;
import java.util.List;

/**
 * 计算步骤数据传输对象
 * 用于直接计算模式下，定义单个计算步骤的详细配 * 多个计算步骤按顺序拼接成完整的计算表达式
 */
public class CalculationStepDTO {

    /**
     * 计算步骤唯一标识 ID
     * 前端用于追踪和管理计算步     */
    private Long id;

    /**
     * 逻辑运算     * 当有多个计算步骤时，用于连接前后两个计算步骤
     * 可选值：+（加）（减）（乘）（除     * 示例：第一个步骤后的运算符可能"+" "*"
     */
    private String logicOperator;

    /**
     * 函数分类
     * 该计算步骤使用的函数分类（数组形式，取第一个元素）
     * 可选值：number（数值处理）、string（字符串处理）、date（日期处理）     */
    private String[] functionCategory;

    /**
     * 具体函数名称
     * 在选定的函数分类下，选择的具体函     * 示例     * - 数值处理：max、min、sum、avg、arithmetic（四则运算）
     * - 字符串处理：concat、substring、replace
     * - 日期处理：diff_months、diff_days、add_years
     */
    private String filterFunction;

    /**
     * 操作数（参数）列     * 函数执行所需的参数，每个参数OperandDTO 定义
     * 参数类型包括     * - field（字段）：从 inputData 中读取的字段
     * - operator（运算符）：四则运算中的 +
     * - value（固定值）：用户输入的固定数值或字符     * - step（步骤引用）：引用之前步骤的输出变量
     * 示例：四则运算可能包[字段，运算符，字段，运算符，固定值]
     */
    private List<OperandDTO> operands;

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

    public String[] getFunctionCategory() {
        return functionCategory;
    }

    public void setFunctionCategory(String[] functionCategory) {
        this.functionCategory = functionCategory;
    }

    public String getFilterFunction() {
        return filterFunction;
    }

    public void setFilterFunction(String filterFunction) {
        this.filterFunction = filterFunction;
    }

    public List<OperandDTO> getOperands() {
        return operands;
    }

    public void setOperands(List<OperandDTO> operands) {
        this.operands = operands;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CalculationStepDTO that = (CalculationStepDTO) o;

        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (logicOperator != null ? !logicOperator.equals(that.logicOperator) : that.logicOperator != null) return false;
        if (!Arrays.equals(functionCategory, that.functionCategory)) return false;
        if (filterFunction != null ? !filterFunction.equals(that.filterFunction) : that.filterFunction != null) return false;
        return operands != null ? operands.equals(that.operands) : that.operands == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (logicOperator != null ? logicOperator.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(functionCategory);
        result = 31 * result + (filterFunction != null ? filterFunction.hashCode() : 0);
        result = 31 * result + (operands != null ? operands.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "CalculationStepDTO{" +
                "id=" + id +
                ", logicOperator='" + logicOperator + '\'' +
                ", functionCategory=" + Arrays.toString(functionCategory) +
                ", filterFunction='" + filterFunction + '\'' +
                ", operands=" + operands +
                '}';
    }
}
