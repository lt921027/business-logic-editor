package com.businesslogic.redisCache;

import com.googlecode.aviator.Expression;

import java.util.Map;
import java.util.Objects;

/**
 * 交易码下的特征及表达式上下文（特征级别版本号方案）
 *
 * <p>features 从 List 改为 Map，便于按特征编码精确查找和更新。
 */
public class TxnExpressionContext {

    /** 交易码 */
    private String txnCode;

    /** 交易码版本号 */
    private long version;

    /** 特征及表达式映射（featureCode → FeatureVersionedExpression） */
    private Map<String, FeatureVersionedExpression> features;

    public TxnExpressionContext() {
    }

    public TxnExpressionContext(String txnCode, long version, Map<String, FeatureVersionedExpression> features) {
        this.txnCode = txnCode;
        this.version = version;
        this.features = features;
    }

    public String getTxnCode() {
        return txnCode;
    }

    public void setTxnCode(String txnCode) {
        this.txnCode = txnCode;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public Map<String, FeatureVersionedExpression> getFeatures() {
        return features;
    }

    public void setFeatures(Map<String, FeatureVersionedExpression> features) {
        this.features = features;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TxnExpressionContext that = (TxnExpressionContext) o;
        return version == that.version
                && Objects.equals(txnCode, that.txnCode)
                && Objects.equals(features, that.features);
    }

    @Override
    public int hashCode() {
        return Objects.hash(txnCode, version, features);
    }

    @Override
    public String toString() {
        return "TxnExpressionContext{"
                + "txnCode='" + txnCode + '\''
                + ", version=" + version
                + ", features=" + features
                + '}';
    }

    /**
     * 单个特征及其编译后的表达式（含特征级别版本号）
     */
    public static class FeatureVersionedExpression {
        /** 特征编码 */
        private String featureCode;
        /** 特征版本号 */
        private long version;
        /** 编译后的表达式对象 */
        private Expression expression;

        public FeatureVersionedExpression() {
        }

        public FeatureVersionedExpression(String featureCode, long version, Expression expression) {
            this.featureCode = featureCode;
            this.version = version;
            this.expression = expression;
        }

        public String getFeatureCode() {
            return featureCode;
        }

        public void setFeatureCode(String featureCode) {
            this.featureCode = featureCode;
        }

        public long getVersion() {
            return version;
        }

        public void setVersion(long version) {
            this.version = version;
        }

        public Expression getExpression() {
            return expression;
        }

        public void setExpression(Expression expression) {
            this.expression = expression;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FeatureVersionedExpression that = (FeatureVersionedExpression) o;
            return version == that.version
                    && Objects.equals(featureCode, that.featureCode)
                    && Objects.equals(expression, that.expression);
        }

        @Override
        public int hashCode() {
            return Objects.hash(featureCode, version, expression);
        }

        @Override
        public String toString() {
            return "FeatureVersionedExpression{"
                    + "featureCode='" + featureCode + '\''
                    + ", version=" + version
                    + ", expression=" + expression
                    + '}';
        }
    }
}
