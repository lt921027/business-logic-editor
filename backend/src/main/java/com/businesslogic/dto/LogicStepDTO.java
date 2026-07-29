package com.businesslogic.dto;

import java.util.Arrays;
import java.util.List;

/**
 * 业务逻辑步骤数据传输对象
 * 代表一个完整的业务逻辑处理单元，包含步骤的所有配置信 */
public class LogicStepDTO {

    /**
     * 步骤唯一标识 ID
     * 前端用于追踪和管理步骤，确保操作的正确     */
    private Long id;

    /**
     * 步骤执行顺序号（1 开始）
     * 确保业务逻辑按正确的顺序执行
     * 在保存和加载时保持步骤的顺序一     */
    private Integer stepOrder;

    /**
     * 函数分类
     * 指定该步骤使用的函数分类（数组形式，取第一个元素），决定后续可用的具体函数和参数配置方     * 可选值：
     * - direct：直接计算（四则运算、数值处理）
     * - filter：筛选（数组筛选、条件过滤）
     * - string：字符串处理（拼接、截取、转换）
     * - number：数值处理（数学计算、聚合运算）
     * - date：日期处理（日期计算、格式化     * - custom：自定义表达式（高级自定义逻辑     */
    private String[] functionCategory;

    /**
     * 主字     * 该步骤主要处理的输入字段
     * 在简单模式下使用，用于前端字段树的选中状态显     * 示例amount"order.items"
     */
    private String field;

    /**
     * 函数名称
     * 选择的具体函数名称，表达式生成器根据此字段生成对应的函数调用
     * 示例max"sum"concat"substring"
     */
    private String functionName;

    /**
     * 函数参数列表
     * 存储函数执行所需的参数，参数可以是：
     * - 字段名："amount"
     * - 固定值："100"0.9"
     * - 其他步骤的输出变量："step1"
     * 示例：["field1", "field2"]、["step1", "0.9"]
     */
    private List<String> params;

    /**
     * 自定义表达式
     * functionCategory custom 时使用，支持高级用户编写自定Aviator 表达     * 提供最大的灵活     * 示例field1 > 100 ? field1 * 0.9 : field1"
     */
    private String customExpression;

    /**
     * 输出变量     * 该步骤执行结果的输出变量名，用于     * 1. 步骤间引用：后续步骤可以通过此变量名引用当前步骤的结     * 2. 调试追踪：便于查看每个步骤的执行结果
     * 默认值：step1, step2, step3...
     * 示例totalAmount"discountedPrice"
     */
    private String outputVar;

    /**
     * 步骤备注说明
     * 对该步骤的文字说明，帮助理解该步骤的作用
     * 降低后续维护的理解成     * 示例计算商品金额加运应用会员折扣"
     */
    private String comment;

    /**
     * 筛选范     * 在筛选模式下，指定在哪个数组字段上进行筛     * 决定筛选操作的数据范围
     * 示例order.items"user.orders"
     */
    private String filterScope;

    /**
     * 映射字段
     * 筛选后映射到的输出字段
     * 用于后续步骤的引     */
    private String mappedField;

    /**
     * 计算步骤列表（直接计算模式）
     * 支持多步计算组合，每个计算步骤包含：
     * - 参数类型（字运算固定步骤引用     * - 参数     * - 运算符（+     * 按顺序拼接成完整的计算表达式
     */
    private List<CalculationStepDTO> calculationSteps;

    /**
     * 筛选条件列     * 定义筛选的具体条件，支持：
     * - 嵌套条件     * - 多个条件的逻辑组合（AND/OR     * 条件类型     * - 函数条件：使用字符串/数日期函数
     * - 字段条件：字段比较（等于、大于、小于等     */
    private List<FilterItemDTO> filterItems;

    /**
     * 满足条件时执行的操作列表
     * 定义筛选条件满足时的处理逻辑，支持的操作类型     * - 计数（count     * - 求和（sum     * - 返回值（returnValue     * - 去重（distinct     * - 新参数（returnNewParam     */
    private List<FilterLogicDTO> filterLogic;

    /**
     * 条件不满足时执行的操作列     * 提供条件不满足时的备选处理逻辑
     * filterLogic 形成完整的条件分     */
    private List<FilterLogicDTO> reverseLogic;

    /**
     * 折叠状态（前端 UI 状态）
     * 仅用于前端界面展示，不参与表达式生成和执     * true：步骤折叠显示；false：步骤展开显示
     */
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

    public String[] getFunctionCategory() {
        return functionCategory;
    }

    public void setFunctionCategory(String[] functionCategory) {
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

    public List<CalculationStepDTO> getCalculationSteps() {
        return calculationSteps;
    }

    public void setCalculationSteps(List<CalculationStepDTO> calculationSteps) {
        this.calculationSteps = calculationSteps;
    }

    public List<FilterItemDTO> getFilterItems() {
        return filterItems;
    }

    public void setFilterItems(List<FilterItemDTO> filterItems) {
        this.filterItems = filterItems;
    }

    public List<FilterLogicDTO> getFilterLogic() {
        return filterLogic;
    }

    public void setFilterLogic(List<FilterLogicDTO> filterLogic) {
        this.filterLogic = filterLogic;
    }

    public List<FilterLogicDTO> getReverseLogic() {
        return reverseLogic;
    }

    public void setReverseLogic(List<FilterLogicDTO> reverseLogic) {
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

        LogicStepDTO that = (LogicStepDTO) o;

        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (stepOrder != null ? !stepOrder.equals(that.stepOrder) : that.stepOrder != null) return false;
        if (!Arrays.equals(functionCategory, that.functionCategory)) return false;
        if (field != null ? !field.equals(that.field) : that.field != null) return false;
        if (functionName != null ? !functionName.equals(that.functionName) : that.functionName != null) return false;
        if (params != null ? !params.equals(that.params) : that.params != null) return false;
        if (customExpression != null ? !customExpression.equals(that.customExpression) : that.customExpression != null)
            return false;
        if (outputVar != null ? !outputVar.equals(that.outputVar) : that.outputVar != null) return false;
        if (comment != null ? !comment.equals(that.comment) : that.comment != null) return false;
        if (filterScope != null ? !filterScope.equals(that.filterScope) : that.filterScope != null) return false;
        if (mappedField != null ? !mappedField.equals(that.mappedField) : that.mappedField != null) return false;
        if (calculationSteps != null ? !calculationSteps.equals(that.calculationSteps) : that.calculationSteps != null)
            return false;
        if (filterItems != null ? !filterItems.equals(that.filterItems) : that.filterItems != null) return false;
        if (filterLogic != null ? !filterLogic.equals(that.filterLogic) : that.filterLogic != null) return false;
        if (reverseLogic != null ? !reverseLogic.equals(that.reverseLogic) : that.reverseLogic != null) return false;
        return collapsed != null ? collapsed.equals(that.collapsed) : that.collapsed == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (stepOrder != null ? stepOrder.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(functionCategory);
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
        result = 31 * result + (filterLogic != null ? filterLogic.hashCode() : 0);
        result = 31 * result + (reverseLogic != null ? reverseLogic.hashCode() : 0);
        result = 31 * result + (collapsed != null ? collapsed.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "LogicStepDTO{" +
                "id=" + id +
                ", stepOrder=" + stepOrder +
                ", functionCategory=" + Arrays.toString(functionCategory) +
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
