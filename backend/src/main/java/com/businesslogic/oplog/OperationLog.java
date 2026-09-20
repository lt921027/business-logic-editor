package com.businesslogic.oplog;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 操作日志实体，对应表 {@code operation_log}。
 *
 * <p>字段刻意保持精简：只记录"谁、在什么时间、对哪个对象的哪一类操作、做了什么"，
 * 具体业务内容由展示端拿 {@link #bizId} 回查对应数据表获得。</p>
 *
 * <p>{@link #bizType}、{@link #bizId}、{@link #operation}、{@link #operationDesc}、
 * {@link #operator} 由开发人员在业务代码里赋值（见 {@link OpLogContext}）；
 * {@link #success}、{@link #errorMsg}、{@link #createdAt} 由切面自动补齐
 * （见 {@link OperationLogAspect}）。</p>
 *
 * <p>本表不加逻辑删除字段，也不参与 MyBatis-Plus 的 {@code logic-delete-field} 配置，
 * 日志只增不删。</p>
 */
@TableName("operation_log")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对象类型：SOURCE-源报文 FEATURE-特征配置 */
    private String bizType;

    /** 对象ID：源报文编号 或 特征配置ID */
    private String bizId;

    /** 操作类型：INSERT/UPDATE/DELETE */
    private String operation;

    /** 描述文字，展示端直接显示 */
    private String operationDesc;

    /** 是否成功：0-失败 1-成功 */
    private Integer success;

    /** 失败原因 */
    private String errorMsg;

    /** 操作人，由开发人员在登记时填写 */
    private String operator;

    /** 操作时间 */
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getBizId() {
        return bizId;
    }

    public void setBizId(String bizId) {
        this.bizId = bizId;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getOperationDesc() {
        return operationDesc;
    }

    public void setOperationDesc(String operationDesc) {
        this.operationDesc = operationDesc;
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

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "OperationLog{id=" + id
                + ", bizType='" + bizType + '\''
                + ", bizId='" + bizId + '\''
                + ", operation='" + operation + '\''
                + ", operationDesc='" + operationDesc + '\''
                + ", success=" + success
                + ", errorMsg='" + errorMsg + '\''
                + ", operator='" + operator + '\''
                + ", createdAt=" + createdAt
                + '}';
    }
}
