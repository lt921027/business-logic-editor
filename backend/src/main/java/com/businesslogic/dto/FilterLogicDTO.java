package com.businesslogic.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * 筛选执行逻辑数据传输对象
 * 定义筛选条件满足或不满足时执行的具体操 * 用于 filterLogics（满足条件时）和 reverseLogics（不满足条件时）
 * 
 * 采用 type + typeValue 的简化结构：
 * - type: 执行操作类型
 * - typeValue: 根据type的不同，typeValue的含义也不同
 */
public class FilterLogicDTO {

    /**
     * 执行逻辑唯一标识 ID
     * 前端用于追踪和管理执行逻辑
     */
    private Long id;

    /**
     * 执行操作类型
     * 决定 typeValue 字段的含义和后续处理逻辑
     * 可选值：
     * - count：计数操     *   - typeValue = "all"：统计所有符合条件的记录     *   - typeValue = "fieldName"：统计指定字段的非空值数     * - sum：求和操     *   - typeValue = "fieldName"：对指定数值字段求     * - distinct：去重操     *   - typeValue = "fieldName"：对指定字段去重
     */
    private String type;

    /**
     * 执行操作的     * 根据 type 字段的值，含义不同     * 
     * type = "count" 时：
     *   - "all"：统计所有记录数     *   - "PH010RA1"：统PH010RA1 字段的非空值数     * 
     * type = "sum" 时：
     *   - "amount"：对 amount 字段求和
     * 
     * type = "distinct" 时：
     *   - "userId"：对 userId 字段去重
     */
    /** 兼容旧数据/旧调用方传入的 "value" 字段名，反序列化时两种 key 都接受，序列化统一输出 typeValue */
    @JsonAlias("value")
    private String typeValue;

    /**
     * 备注说明
     * 对该执行逻辑的文字说     * 帮助理解该操作的作用
     */
    private String comment;

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

    public String getTypeValue() {
        return typeValue;
    }

    public void setTypeValue(String typeValue) {
        this.typeValue = typeValue;
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

        FilterLogicDTO that = (FilterLogicDTO) o;

        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (type != null ? !type.equals(that.type) : that.type != null) return false;
        if (typeValue != null ? !typeValue.equals(that.typeValue) : that.typeValue != null) return false;
        return comment != null ? comment.equals(that.comment) : that.comment == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (typeValue != null ? typeValue.hashCode() : 0);
        result = 31 * result + (comment != null ? comment.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "FilterLogicDTO{" +
                "id=" + id +
                ", type='" + type + '\'' +
                ", typeValue='" + typeValue + '\'' +
                ", comment='" + comment + '\'' +
                '}';
    }
}
