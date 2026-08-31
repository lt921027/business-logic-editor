package com.businesslogic.vo;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.Objects;

public class FilterLogicVO {

    private Long id;

    private String type;

    /** 兼容旧数据/旧调用方传入的 "value" 字段名，反序列化时两种 key 都接受，序列化统一输出 typeValue */
    @JsonAlias("value")
    private String typeValue;

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
        FilterLogicVO that = (FilterLogicVO) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(type, that.type) &&
                Objects.equals(typeValue, that.typeValue) &&
                Objects.equals(comment, that.comment);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, typeValue, comment);
    }

    @Override
    public String toString() {
        return "FilterLogicVO{" +
                "id=" + id +
                ", type='" + type + '\'' +
                ", typeValue='" + typeValue + '\'' +
                ", comment='" + comment + '\'' +
                '}';
    }
}
