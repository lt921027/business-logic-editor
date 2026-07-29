package com.businesslogic.groovy.redisCache;

import com.businesslogic.groovy.engine.CompiledGroovyScript;

import java.util.Map;
import java.util.Objects;

/**
 * 交易码下的特征及 Groovy 表达式上下文
 *
 * <p>对应 Aviator 的 TxnExpressionContext。
 * 持有编译后的 CompiledGroovyScript 而非 Aviator Expression。
 */
public class GroovyTxnExpressionContext {

    /** 交易码 */
    private String txnCode;

    /** 交易码版本号 */
    private long version;

    /** 特征及表达式映射 */
    private Map<String, FeatureVersionedExpression> features;

    public GroovyTxnExpressionContext() {
    }

    public GroovyTxnExpressionContext(String txnCode, long version,
                                       Map<String, FeatureVersionedExpression> features) {
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
        GroovyTxnExpressionContext that = (GroovyTxnExpressionContext) o;
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
        return "GroovyTxnExpressionContext{"
                + "txnCode='" + txnCode + '\''
                + ", version=" + version
                + ", features=" + features
                + '}';
    }

    /**
     * 单个特征及其编译后的 Groovy 脚本（含特征级别版本号）
     */
    public static class FeatureVersionedExpression {
        private String featureCode;
        private long version;
        private CompiledGroovyScript expression;

        public FeatureVersionedExpression() {
        }

        public FeatureVersionedExpression(String featureCode, long version, CompiledGroovyScript expression) {
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

        public CompiledGroovyScript getExpression() {
            return expression;
        }

        public void setExpression(CompiledGroovyScript expression) {
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
