package com.businesslogic.demo.model;

import java.util.List;

/**
 * AviatorScript 生成响应
 */
public class AviatorResponse {
    
    /**
     * 是否成功
     */
    private boolean success;
    
    /**
     * 生成AviatorScript 表达     */
    private String aviatorScript;
    
    /**
     * 分步表达式列     */
    private List<StepExpression> stepExpressions;
    
    /**
     * 错误信息
     */
    private String errorMessage;

    public AviatorResponse() {
    }

    public AviatorResponse(boolean success, String aviatorScript, List<StepExpression> stepExpressions, String errorMessage) {
        this.success = success;
        this.aviatorScript = aviatorScript;
        this.stepExpressions = stepExpressions;
        this.errorMessage = errorMessage;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getAviatorScript() {
        return aviatorScript;
    }

    public void setAviatorScript(String aviatorScript) {
        this.aviatorScript = aviatorScript;
    }

    public List<StepExpression> getStepExpressions() {
        return stepExpressions;
    }

    public void setStepExpressions(List<StepExpression> stepExpressions) {
        this.stepExpressions = stepExpressions;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AviatorResponse that = (AviatorResponse) o;

        if (success != that.success) return false;
        if (aviatorScript != null ? !aviatorScript.equals(that.aviatorScript) : that.aviatorScript != null)
            return false;
        if (stepExpressions != null ? !stepExpressions.equals(that.stepExpressions) : that.stepExpressions != null)
            return false;
        return errorMessage != null ? errorMessage.equals(that.errorMessage) : that.errorMessage == null;
    }

    @Override
    public int hashCode() {
        int result = (success ? 1 : 0);
        result = 31 * result + (aviatorScript != null ? aviatorScript.hashCode() : 0);
        result = 31 * result + (stepExpressions != null ? stepExpressions.hashCode() : 0);
        result = 31 * result + (errorMessage != null ? errorMessage.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "AviatorResponse{" +
                "success=" + success +
                ", aviatorScript='" + aviatorScript + '\'' +
                ", stepExpressions=" + stepExpressions +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }

    public static AviatorResponseBuilder builder() {
        return new AviatorResponseBuilder();
    }

    public static class AviatorResponseBuilder {
        private boolean success;
        private String aviatorScript;
        private List<StepExpression> stepExpressions;
        private String errorMessage;

        AviatorResponseBuilder() {
        }

        public AviatorResponseBuilder success(boolean success) {
            this.success = success;
            return this;
        }

        public AviatorResponseBuilder aviatorScript(String aviatorScript) {
            this.aviatorScript = aviatorScript;
            return this;
        }

        public AviatorResponseBuilder stepExpressions(List<StepExpression> stepExpressions) {
            this.stepExpressions = stepExpressions;
            return this;
        }

        public AviatorResponseBuilder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public AviatorResponse build() {
            return new AviatorResponse(success, aviatorScript, stepExpressions, errorMessage);
        }

        @Override
        public String toString() {
            return "AviatorResponse.AviatorResponseBuilder{" +
                    "success=" + success +
                    ", aviatorScript='" + aviatorScript + '\'' +
                    ", stepExpressions=" + stepExpressions +
                    ", errorMessage='" + errorMessage + '\'' +
                    '}';
        }
    }
    
    /**
     * 步骤表达     */
    public static class StepExpression {
        private int stepIndex;
        private String comment;
        private String expression;
        private String outputVar;

        public StepExpression() {
        }

        public StepExpression(int stepIndex, String comment, String expression, String outputVar) {
            this.stepIndex = stepIndex;
            this.comment = comment;
            this.expression = expression;
            this.outputVar = outputVar;
        }

        public int getStepIndex() {
            return stepIndex;
        }

        public void setStepIndex(int stepIndex) {
            this.stepIndex = stepIndex;
        }

        public String getComment() {
            return comment;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }

        public String getExpression() {
            return expression;
        }

        public void setExpression(String expression) {
            this.expression = expression;
        }

        public String getOutputVar() {
            return outputVar;
        }

        public void setOutputVar(String outputVar) {
            this.outputVar = outputVar;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            StepExpression that = (StepExpression) o;

            if (stepIndex != that.stepIndex) return false;
            if (comment != null ? !comment.equals(that.comment) : that.comment != null) return false;
            if (expression != null ? !expression.equals(that.expression) : that.expression != null) return false;
            return outputVar != null ? outputVar.equals(that.outputVar) : that.outputVar == null;
        }

        @Override
        public int hashCode() {
            int result = stepIndex;
            result = 31 * result + (comment != null ? comment.hashCode() : 0);
            result = 31 * result + (expression != null ? expression.hashCode() : 0);
            result = 31 * result + (outputVar != null ? outputVar.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "StepExpression{" +
                    "stepIndex=" + stepIndex +
                    ", comment='" + comment + '\'' +
                    ", expression='" + expression + '\'' +
                    ", outputVar='" + outputVar + '\'' +
                    '}';
        }

        public static StepExpressionBuilder builder() {
            return new StepExpressionBuilder();
        }

        public static class StepExpressionBuilder {
            private int stepIndex;
            private String comment;
            private String expression;
            private String outputVar;

            StepExpressionBuilder() {
            }

            public StepExpressionBuilder stepIndex(int stepIndex) {
                this.stepIndex = stepIndex;
                return this;
            }

            public StepExpressionBuilder comment(String comment) {
                this.comment = comment;
                return this;
            }

            public StepExpressionBuilder expression(String expression) {
                this.expression = expression;
                return this;
            }

            public StepExpressionBuilder outputVar(String outputVar) {
                this.outputVar = outputVar;
                return this;
            }

            public StepExpression build() {
                return new StepExpression(stepIndex, comment, expression, outputVar);
            }

            @Override
            public String toString() {
                return "StepExpression.StepExpressionBuilder{" +
                        "stepIndex=" + stepIndex +
                        ", comment='" + comment + '\'' +
                        ", expression='" + expression + '\'' +
                        ", outputVar='" + outputVar + '\'' +
                        '}';
            }
        }
    }
}
