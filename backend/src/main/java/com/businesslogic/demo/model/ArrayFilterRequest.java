package com.businesslogic.demo.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 数组过滤请求（支持复杂逻辑 */
public class ArrayFilterRequest {
    
    /**
     * JSON 文本
     */
    private String jsonText;
    
    /**
     * 数组JSONPath 路径
     * 例如['order'].items[*]
     */
    private String arrayPath;
    
    /**
     * 根逻辑组（支持嵌套     */
    private LogicGroup rootGroup;

    public ArrayFilterRequest() {
    }

    public ArrayFilterRequest(String jsonText, String arrayPath, LogicGroup rootGroup) {
        this.jsonText = jsonText;
        this.arrayPath = arrayPath;
        this.rootGroup = rootGroup;
    }

    public String getJsonText() {
        return jsonText;
    }

    public void setJsonText(String jsonText) {
        this.jsonText = jsonText;
    }

    public String getArrayPath() {
        return arrayPath;
    }

    public void setArrayPath(String arrayPath) {
        this.arrayPath = arrayPath;
    }

    public LogicGroup getRootGroup() {
        return rootGroup;
    }

    public void setRootGroup(LogicGroup rootGroup) {
        this.rootGroup = rootGroup;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ArrayFilterRequest that = (ArrayFilterRequest) o;

        if (jsonText != null ? !jsonText.equals(that.jsonText) : that.jsonText != null) return false;
        if (arrayPath != null ? !arrayPath.equals(that.arrayPath) : that.arrayPath != null) return false;
        return rootGroup != null ? rootGroup.equals(that.rootGroup) : that.rootGroup == null;
    }

    @Override
    public int hashCode() {
        int result = jsonText != null ? jsonText.hashCode() : 0;
        result = 31 * result + (arrayPath != null ? arrayPath.hashCode() : 0);
        result = 31 * result + (rootGroup != null ? rootGroup.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ArrayFilterRequest{" +
                "jsonText='" + jsonText + '\'' +
                ", arrayPath='" + arrayPath + '\'' +
                ", rootGroup=" + rootGroup +
                '}';
    }

    /**
     * 逻辑组（支持 AND/OR 嵌套     */
    public static class LogicGroup {
        /**
         * 逻辑运算符：AND OR
         */
        private String operator = "AND";
        
        /**
         * 子条件列表（可以是简单条件或嵌套逻辑组）
         */
        private List<ConditionItem> items = new ArrayList<>();

        public LogicGroup() {
        }

        public LogicGroup(String operator, List<ConditionItem> items) {
            this.operator = operator;
            this.items = items;
        }

        public String getOperator() {
            return operator;
        }

        public void setOperator(String operator) {
            this.operator = operator;
        }

        public List<ConditionItem> getItems() {
            return items;
        }

        public void setItems(List<ConditionItem> items) {
            this.items = items;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            LogicGroup that = (LogicGroup) o;

            if (operator != null ? !operator.equals(that.operator) : that.operator != null) return false;
            return items != null ? items.equals(that.items) : that.items == null;
        }

        @Override
        public int hashCode() {
            int result = operator != null ? operator.hashCode() : 0;
            result = 31 * result + (items != null ? items.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "LogicGroup{" +
                    "operator='" + operator + '\'' +
                    ", items=" + items +
                    '}';
        }
    }
    
    /**
     * 条件项（可以是简单条件或逻辑组）
     */
    public static class ConditionItem {
        /**
         * 类型：condition（简单条件）group（逻辑组）
         */
        private String type = "condition";
        
        /**
         * 简单条件（type condition 时使用）
         */
        private SimpleCondition condition;
        
        /**
         * 逻辑组（type group 时使用）
         */
        private LogicGroup group;

        public ConditionItem() {
        }

        public ConditionItem(String type, SimpleCondition condition, LogicGroup group) {
            this.type = type;
            this.condition = condition;
            this.group = group;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public SimpleCondition getCondition() {
            return condition;
        }

        public void setCondition(SimpleCondition condition) {
            this.condition = condition;
        }

        public LogicGroup getGroup() {
            return group;
        }

        public void setGroup(LogicGroup group) {
            this.group = group;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConditionItem that = (ConditionItem) o;

            if (type != null ? !type.equals(that.type) : that.type != null) return false;
            if (condition != null ? !condition.equals(that.condition) : that.condition != null) return false;
            return group != null ? group.equals(that.group) : that.group == null;
        }

        @Override
        public int hashCode() {
            int result = type != null ? type.hashCode() : 0;
            result = 31 * result + (condition != null ? condition.hashCode() : 0);
            result = 31 * result + (group != null ? group.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "ConditionItem{" +
                    "type='" + type + '\'' +
                    ", condition=" + condition +
                    ", group=" + group +
                    '}';
        }
    }
    
    /**
     * 简单条     */
    public static class SimpleCondition {
        
        /**
         * 字段名（支持嵌套路径，如 address.city         */
        private String field;
        
        /**
         * 运算         * 支持=, !=, >, <, >=, <=, contains, startsWith, endsWith, in, notIn
         */
        private String operator;
        
        /**
         * 比较         */
        private String value;
        
        /**
         * 值类型：string, number, boolean, array
         */
        private String valueType;
        
        /**
         * 是否使用函数
         */
        private Boolean useFunction = false;
        
        /**
         * 函数名（length, size, toUpperCase 等）
         */
        private String functionName;
        
        /**
         * 函数参数（可选）
         */
        private List<String> functionParams;
        
        /**
         * 是否取反
         */
        private Boolean negate = false;

        public SimpleCondition() {
        }

        public SimpleCondition(String field, String operator, String value, String valueType, Boolean useFunction, String functionName, List<String> functionParams, Boolean negate) {
            this.field = field;
            this.operator = operator;
            this.value = value;
            this.valueType = valueType;
            this.useFunction = useFunction;
            this.functionName = functionName;
            this.functionParams = functionParams;
            this.negate = negate;
        }

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public String getOperator() {
            return operator;
        }

        public void setOperator(String operator) {
            this.operator = operator;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getValueType() {
            return valueType;
        }

        public void setValueType(String valueType) {
            this.valueType = valueType;
        }

        public Boolean getUseFunction() {
            return useFunction;
        }

        public void setUseFunction(Boolean useFunction) {
            this.useFunction = useFunction;
        }

        public String getFunctionName() {
            return functionName;
        }

        public void setFunctionName(String functionName) {
            this.functionName = functionName;
        }

        public List<String> getFunctionParams() {
            return functionParams;
        }

        public void setFunctionParams(List<String> functionParams) {
            this.functionParams = functionParams;
        }

        public Boolean getNegate() {
            return negate;
        }

        public void setNegate(Boolean negate) {
            this.negate = negate;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            SimpleCondition that = (SimpleCondition) o;

            if (field != null ? !field.equals(that.field) : that.field != null) return false;
            if (operator != null ? !operator.equals(that.operator) : that.operator != null) return false;
            if (value != null ? !value.equals(that.value) : that.value != null) return false;
            if (valueType != null ? !valueType.equals(that.valueType) : that.valueType != null) return false;
            if (useFunction != null ? !useFunction.equals(that.useFunction) : that.useFunction != null) return false;
            if (functionName != null ? !functionName.equals(that.functionName) : that.functionName != null)
                return false;
            if (functionParams != null ? !functionParams.equals(that.functionParams) : that.functionParams != null)
                return false;
            return negate != null ? negate.equals(that.negate) : that.negate == null;
        }

        @Override
        public int hashCode() {
            int result = field != null ? field.hashCode() : 0;
            result = 31 * result + (operator != null ? operator.hashCode() : 0);
            result = 31 * result + (value != null ? value.hashCode() : 0);
            result = 31 * result + (valueType != null ? valueType.hashCode() : 0);
            result = 31 * result + (useFunction != null ? useFunction.hashCode() : 0);
            result = 31 * result + (functionName != null ? functionName.hashCode() : 0);
            result = 31 * result + (functionParams != null ? functionParams.hashCode() : 0);
            result = 31 * result + (negate != null ? negate.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "SimpleCondition{" +
                    "field='" + field + '\'' +
                    ", operator='" + operator + '\'' +
                    ", value='" + value + '\'' +
                    ", valueType='" + valueType + '\'' +
                    ", useFunction=" + useFunction +
                    ", functionName='" + functionName + '\'' +
                    ", functionParams=" + functionParams +
                    ", negate=" + negate +
                    '}';
        }
    }
}
