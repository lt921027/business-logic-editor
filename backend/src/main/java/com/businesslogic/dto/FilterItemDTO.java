package com.businesslogic.dto;

import java.util.Arrays;
import java.util.List;

/**
 * 筛选条件数据传输对 * 定义筛选模式下的具体条件，支持嵌套条件组和逻辑组合
 */
public class FilterItemDTO {

    /**
     * 筛选条件唯一标识 ID
     * 前端用于追踪和管理筛选条     */
    private Long id;

    /**
     * 条件类型
     * 可选值：
     * - condition（条件）：单个筛选条件，包含函数或字段比     * - group（组）：条件组，包含多个子条件或子组，支持嵌     */
    private String type;

    /**
     * 逻辑运算     * type group 时，用于连接组内多个条件的逻辑关系
     * 可选值：
     * - AND（与）：所有条件都必须满足
     * - OR（或）：至少一个条件满足即     */
    private String logicOperator;

    /**
     * 函数分类
     * type condition 时，指定使用的函数分类（数组形式，取第一个元素）
     * 可选值：string（字符串）、number（数值）、date（日期）
     */
    private String[] functionCategory;

    /**
     * 具体函数名称
     * type condition 时，选择的具体函     * 示例     * - 字符串函数：startsWith、endsWith、contains、substring
     * - 数值函数：greaterThan、lessThan、equals、between
     * - 日期函数：isBefore、isAfter、isEqual、diff_months
     */
    private String filterFunction;

    /**
     * 操作数（参数）列     * 函数执行所需的参数，每个参数OperandDTO 定义
     * 参数类型包括     * - field（字段）：从 inputData 中读取的字段
     * - value（固定值）：用户输入的固定数值或字符     * 示例：比较两个字段可能是 [字段 1, 字段 2]
     */
    private List<OperandDTO> operands;

    /**
     * 嵌套层级
     * 表示该条件在嵌套结构中的层级深度
     * 用于前端渲染嵌套结构和限制最大嵌套深     */
    private Integer level;

    /**
     * 子条子组列表
     * type group 时，包含的子条件或子     * 支持递归嵌套，形成复杂的条件树结     */
    private List<FilterItemDTO> items;

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

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public List<FilterItemDTO> getItems() {
        return items;
    }

    public void setItems(List<FilterItemDTO> items) {
        this.items = items;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FilterItemDTO that = (FilterItemDTO) o;

        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (type != null ? !type.equals(that.type) : that.type != null) return false;
        if (logicOperator != null ? !logicOperator.equals(that.logicOperator) : that.logicOperator != null)
            return false;
        if (!Arrays.equals(functionCategory, that.functionCategory)) return false;
        if (filterFunction != null ? !filterFunction.equals(that.filterFunction) : that.filterFunction != null)
            return false;
        if (operands != null ? !operands.equals(that.operands) : that.operands != null) return false;
        if (level != null ? !level.equals(that.level) : that.level != null) return false;
        return items != null ? items.equals(that.items) : that.items == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (logicOperator != null ? logicOperator.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(functionCategory);
        result = 31 * result + (filterFunction != null ? filterFunction.hashCode() : 0);
        result = 31 * result + (operands != null ? operands.hashCode() : 0);
        result = 31 * result + (level != null ? level.hashCode() : 0);
        result = 31 * result + (items != null ? items.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "FilterItemDTO{" +
                "id=" + id +
                ", type='" + type + '\'' +
                ", logicOperator='" + logicOperator + '\'' +
                ", functionCategory=" + Arrays.toString(functionCategory) +
                ", filterFunction='" + filterFunction + '\'' +
                ", operands=" + operands +
                ", level=" + level +
                ", items=" + items +
                '}';
    }
}
