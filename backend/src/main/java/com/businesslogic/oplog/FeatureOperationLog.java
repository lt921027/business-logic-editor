package com.businesslogic.oplog;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 特征操作日志实体，对应表 {@code feature_operation_log}。
 *
 * <p>记录一次特征新增、修改或删除操作，含修改前后的 Groovy 表达式与步骤快照。
 * 各字段的填充规则：</p>
 *
 * <ul>
 *   <li>{@link #featureId}、{@link #operation}、{@link #requestSnapshot} 由切面从入参与注解解析；</li>
 *   <li>{@link #expressionBefore}、{@link #stepDataBefore}、{@link #stepDataVoBefore}
 *       以及对应的 after 字段，由切面查库获得；</li>
 *   <li>{@link #changedFields}、{@link #success}、{@link #errorMsg}、{@link #createdAt}
 *       由切面计算或补齐；</li>
 *   <li>{@link #operator} 由业务自行补充取值逻辑。</li>
 * </ul>
 */
@TableName("feature_operation_log")
public class FeatureOperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 特征ID（business_logic.id）：新增失败时为空，因为记录尚未生成 */
    private Long featureId;

    /** 操作类型：INSERT/UPDATE/DELETE */
    private String operation;

    /** 修改前 Groovy 表达式 */
    private String expressionBefore;

    /** 修改后 Groovy 表达式 */
    private String expressionAfter;

    /** 修改前表达式 MD5（32位小写） */
    private String expressionHashBefore;

    /** 修改后表达式 MD5（32位小写） */
    private String expressionHashAfter;

    /** 修改前步骤，LogicStep 实体序列化（驼峰字段，含业务外键与时间戳） */
    private String stepDataBefore;

    /** 修改后步骤，LogicStep 实体序列化（驼峰字段，含业务外键与时间戳） */
    private String stepDataAfter;

    /** 变更项：步骤/表达式/无变化，仅在修改成功时填写 */
    private String changedFields;

    /** 请求入参快照 */
    private String requestSnapshot;

    /** 操作人，取值逻辑由业务自行补充 */
    private String operator;

    /** 是否成功：0-失败 1-成功 */
    private Integer success;

    /** 失败原因 */
    private String errorMsg;

    /** 操作时间 */
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getFeatureId() {
        return featureId;
    }

    public void setFeatureId(Long featureId) {
        this.featureId = featureId;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getExpressionBefore() {
        return expressionBefore;
    }

    public void setExpressionBefore(String expressionBefore) {
        this.expressionBefore = expressionBefore;
    }

    public String getExpressionAfter() {
        return expressionAfter;
    }

    public void setExpressionAfter(String expressionAfter) {
        this.expressionAfter = expressionAfter;
    }

    public String getExpressionHashBefore() {
        return expressionHashBefore;
    }

    public void setExpressionHashBefore(String expressionHashBefore) {
        this.expressionHashBefore = expressionHashBefore;
    }

    public String getExpressionHashAfter() {
        return expressionHashAfter;
    }

    public void setExpressionHashAfter(String expressionHashAfter) {
        this.expressionHashAfter = expressionHashAfter;
    }

    public String getStepDataBefore() {
        return stepDataBefore;
    }

    public void setStepDataBefore(String stepDataBefore) {
        this.stepDataBefore = stepDataBefore;
    }

    public String getStepDataAfter() {
        return stepDataAfter;
    }

    public void setStepDataAfter(String stepDataAfter) {
        this.stepDataAfter = stepDataAfter;
    }

    public String getChangedFields() {
        return changedFields;
    }

    public void setChangedFields(String changedFields) {
        this.changedFields = changedFields;
    }

    public String getRequestSnapshot() {
        return requestSnapshot;
    }

    public void setRequestSnapshot(String requestSnapshot) {
        this.requestSnapshot = requestSnapshot;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public Integer getSuccess() {
        return success;
    }

    public void setSuccess(Integer success) {
        this.success = success;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "FeatureOperationLog{id=" + id
                + ", featureId=" + featureId
                + ", operation='" + operation + '\''
                + ", changedFields='" + changedFields + '\''
                + ", operator='" + operator + '\''
                + ", success=" + success
                + ", errorMsg='" + errorMsg + '\''
                + ", createdAt=" + createdAt
                + '}';
    }
}
