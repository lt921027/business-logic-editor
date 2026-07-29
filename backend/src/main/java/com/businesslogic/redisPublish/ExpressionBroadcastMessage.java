package com.businesslogic.redisPublish;

import java.util.Objects;

public class ExpressionBroadcastMessage {

    private String expressionId;

    private String transactionCode;

    private String featureCode;

    private Long version;

    private String expression;

    private String action;

    private Long timestamp;

    public ExpressionBroadcastMessage() {
    }

    public ExpressionBroadcastMessage(String expressionId, String transactionCode, String featureCode, Long version, String expression, String action, Long timestamp) {
        this.expressionId = expressionId;
        this.transactionCode = transactionCode;
        this.featureCode = featureCode;
        this.version = version;
        this.expression = expression;
        this.action = action;
        this.timestamp = timestamp;
    }

    public String getExpressionId() {
        return expressionId;
    }

    public void setExpressionId(String expressionId) {
        this.expressionId = expressionId;
    }

    public String getTransactionCode() {
        return transactionCode;
    }

    public void setTransactionCode(String transactionCode) {
        this.transactionCode = transactionCode;
    }

    public String getFeatureCode() {
        return featureCode;
    }

    public void setFeatureCode(String featureCode) {
        this.featureCode = featureCode;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExpressionBroadcastMessage that = (ExpressionBroadcastMessage) o;
        return Objects.equals(expressionId, that.expressionId) && Objects.equals(transactionCode, that.transactionCode) && Objects.equals(featureCode, that.featureCode) && Objects.equals(version, that.version) && Objects.equals(expression, that.expression) && Objects.equals(action, that.action) && Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expressionId, transactionCode, featureCode, version, expression, action, timestamp);
    }

    @Override
    public String toString() {
        return "ExpressionBroadcastMessage{" +
                "expressionId='" + expressionId + '\'' +
                ", transactionCode='" + transactionCode + '\'' +
                ", featureCode='" + featureCode + '\'' +
                ", version=" + version +
                ", expression='" + expression + '\'' +
                ", action='" + action + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String expressionId;
        private String transactionCode;
        private String featureCode;
        private Long version;
        private String expression;
        private String action;
        private Long timestamp;

        private Builder() {
        }

        public Builder expressionId(String expressionId) {
            this.expressionId = expressionId;
            return this;
        }

        public Builder transactionCode(String transactionCode) {
            this.transactionCode = transactionCode;
            return this;
        }

        public Builder featureCode(String featureCode) {
            this.featureCode = featureCode;
            return this;
        }

        public Builder version(Long version) {
            this.version = version;
            return this;
        }

        public Builder expression(String expression) {
            this.expression = expression;
            return this;
        }

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder timestamp(Long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public ExpressionBroadcastMessage build() {
            return new ExpressionBroadcastMessage(expressionId, transactionCode, featureCode, version, expression, action, timestamp);
        }
    }
}
