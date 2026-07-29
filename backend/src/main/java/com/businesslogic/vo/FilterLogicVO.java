package com.businesslogic.vo;

import java.util.Objects;

public class FilterLogicVO {

    private Long id;

    private String type;
    private String value;

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
        FilterLogicVO that = (FilterLogicVO) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(type, that.type) &&
                Objects.equals(value, that.value) &&
                Objects.equals(comment, that.comment);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, value, comment);
    }

    @Override
    public String toString() {
        return "FilterLogicVO{" +
                "id=" + id +
                ", type='" + type + '\'' +
                ", value='" + value + '\'' +
                ", comment='" + comment + '\'' +
                '}';
    }
}
