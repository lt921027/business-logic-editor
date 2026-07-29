package com.businesslogic.demo.model;

import java.util.List;

/**
 * 业务逻辑步骤请求
 */
public class BusinessLogicRequest {
    
    /**
     * 接口入参 JSON
     */
    private String jsonInput;
    
    /**
     * 业务逻辑步骤列表
     */
    private List<LogicStep> steps;

    public String getJsonInput() {
        return jsonInput;
    }

    public void setJsonInput(String jsonInput) {
        this.jsonInput = jsonInput;
    }

    public List<LogicStep> getSteps() {
        return steps;
    }

    public void setSteps(List<LogicStep> steps) {
        this.steps = steps;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BusinessLogicRequest that = (BusinessLogicRequest) o;

        if (jsonInput != null ? !jsonInput.equals(that.jsonInput) : that.jsonInput != null) return false;
        return steps != null ? steps.equals(that.steps) : that.steps == null;
    }

    @Override
    public int hashCode() {
        int result = jsonInput != null ? jsonInput.hashCode() : 0;
        result = 31 * result + (steps != null ? steps.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "BusinessLogicRequest{" +
                "jsonInput='" + jsonInput + '\'' +
                ", steps=" + steps +
                '}';
    }
    
    /**
     * 业务逻辑步骤
     */
    public static class LogicStep {
        /**
         * 操作类型：field, function, expression, condition
         */
        private String operationType;
        
        /**
         * 字段路径
         */
        private String field;
        
        /**
         * 函数名称
         */
        private String functionName;
        
        /**
         * 函数参数
         */
        private List<String> params;
        
        /**
         * 表达         */
        private String expression;
        
        /**
         * 条件列表
         */
        private List<Condition> conditions;
        
        /**
         * 输出变量         */
        private String outputVar;
        
        /**
         * 备注说明
         */
        private String comment;

        public String getOperationType() {
            return operationType;
        }

        public void setOperationType(String operationType) {
            this.operationType = operationType;
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

        public String getExpression() {
            return expression;
        }

        public void setExpression(String expression) {
            this.expression = expression;
        }

        public List<Condition> getConditions() {
            return conditions;
        }

        public void setConditions(List<Condition> conditions) {
            this.conditions = conditions;
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            LogicStep logicStep = (LogicStep) o;

            if (operationType != null ? !operationType.equals(logicStep.operationType) : logicStep.operationType != null)
                return false;
            if (field != null ? !field.equals(logicStep.field) : logicStep.field != null) return false;
            if (functionName != null ? !functionName.equals(logicStep.functionName) : logicStep.functionName != null)
                return false;
            if (params != null ? !params.equals(logicStep.params) : logicStep.params != null) return false;
            if (expression != null ? !expression.equals(logicStep.expression) : logicStep.expression != null)
                return false;
            if (conditions != null ? !conditions.equals(logicStep.conditions) : logicStep.conditions != null)
                return false;
            if (outputVar != null ? !outputVar.equals(logicStep.outputVar) : logicStep.outputVar != null)
                return false;
            return comment != null ? comment.equals(logicStep.comment) : logicStep.comment == null;
        }

        @Override
        public int hashCode() {
            int result = operationType != null ? operationType.hashCode() : 0;
            result = 31 * result + (field != null ? field.hashCode() : 0);
            result = 31 * result + (functionName != null ? functionName.hashCode() : 0);
            result = 31 * result + (params != null ? params.hashCode() : 0);
            result = 31 * result + (expression != null ? expression.hashCode() : 0);
            result = 31 * result + (conditions != null ? conditions.hashCode() : 0);
            result = 31 * result + (outputVar != null ? outputVar.hashCode() : 0);
            result = 31 * result + (comment != null ? comment.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "LogicStep{" +
                    "operationType='" + operationType + '\'' +
                    ", field='" + field + '\'' +
                    ", functionName='" + functionName + '\'' +
                    ", params=" + params +
                    ", expression='" + expression + '\'' +
                    ", conditions=" + conditions +
                    ", outputVar='" + outputVar + '\'' +
                    ", comment='" + comment + '\'' +
                    '}';
        }
        
        /**
         * 条件
         */
        public static class Condition {
            /**
             * 字段
             */
            private String field;
            
            /**
             * 操作             */
            private String operator;
            
            /**
             * 值类型：value, field
             */
            private String valueType;
            
            /**
             *              */
            private String value;
            
            /**
             * 值字             */
            private String valueField;
            
            /**
             * 逻辑运算符：AND, OR
             */
            private String logicOperator;

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

            public String getValueType() {
                return valueType;
            }

            public void setValueType(String valueType) {
                this.valueType = valueType;
            }

            public String getValue() {
                return value;
            }

            public void setValue(String value) {
                this.value = value;
            }

            public String getValueField() {
                return valueField;
            }

            public void setValueField(String valueField) {
                this.valueField = valueField;
            }

            public String getLogicOperator() {
                return logicOperator;
            }

            public void setLogicOperator(String logicOperator) {
                this.logicOperator = logicOperator;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;

                Condition condition = (Condition) o;

                if (field != null ? !field.equals(condition.field) : condition.field != null) return false;
                if (operator != null ? !operator.equals(condition.operator) : condition.operator != null) return false;
                if (valueType != null ? !valueType.equals(condition.valueType) : condition.valueType != null)
                    return false;
                if (value != null ? !value.equals(condition.value) : condition.value != null) return false;
                if (valueField != null ? !valueField.equals(condition.valueField) : condition.valueField != null)
                    return false;
                return logicOperator != null ? logicOperator.equals(condition.logicOperator) : condition.logicOperator == null;
            }

            @Override
            public int hashCode() {
                int result = field != null ? field.hashCode() : 0;
                result = 31 * result + (operator != null ? operator.hashCode() : 0);
                result = 31 * result + (valueType != null ? valueType.hashCode() : 0);
                result = 31 * result + (value != null ? value.hashCode() : 0);
                result = 31 * result + (valueField != null ? valueField.hashCode() : 0);
                result = 31 * result + (logicOperator != null ? logicOperator.hashCode() : 0);
                return result;
            }

            @Override
            public String toString() {
                return "Condition{" +
                        "field='" + field + '\'' +
                        ", operator='" + operator + '\'' +
                        ", valueType='" + valueType + '\'' +
                        ", value='" + value + '\'' +
                        ", valueField='" + valueField + '\'' +
                        ", logicOperator='" + logicOperator + '\'' +
                        '}';
            }
        }
    }
}
