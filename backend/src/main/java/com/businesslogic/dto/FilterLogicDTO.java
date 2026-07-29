package com.businesslogic.dto;

/**
 * 筛选执行逻辑数据传输对象
 * 定义筛选条件满足或不满足时执行的具体操 * 用于 filterLogic（满足条件时）和 reverseLogic（不满足条件时）
 * 
 * 采用 type + value 的简化结构：
 * - type: 执行操作类型
 * - value: 根据type的不同，value的含义也不同
 */
public class FilterLogicDTO {

    /**
     * 执行逻辑唯一标识 ID
     * 前端用于追踪和管理执行逻辑
     */
    private Long id;

    /**
     * 执行操作类型
     * 决定 value 字段的含义和后续处理逻辑
     * 可选值：
     * - count：计数操     *   - value = "all"：统计所有符合条件的记录     *   - value = "fieldName"：统计指定字段的非空值数     * - sum：求和操     *   - value = "fieldName"：对指定数值字段求     * - distinct：去重操     *   - value = "fieldName"：对指定字段去重
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
    private String value;

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

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
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
        if (value != null ? !value.equals(that.value) : that.value != null) return false;
        return comment != null ? comment.equals(that.comment) : that.comment == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (value != null ? value.hashCode() : 0);
        result = 31 * result + (comment != null ? comment.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "FilterLogicDTO{" +
                "id=" + id +
                ", type='" + type + '\'' +
                ", value='" + value + '\'' +
                ", comment='" + comment + '\'' +
                '}';
    }
}
