package com.businesslogic.vo;

import java.util.Objects;

public class OperandVO {

    private Long id;

    private String type;

    private Object typeValue;

    private String tip;

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

    public Object getTypeValue() {
        return typeValue;
    }

    public void setTypeValue(Object typeValue) {
        this.typeValue = typeValue;
    }

    public String getTip() {
        return tip;
    }

    public void setTip(String tip) {
        this.tip = tip;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OperandVO operandVO = (OperandVO) o;
        return Objects.equals(id, operandVO.id) &&
                Objects.equals(type, operandVO.type) &&
                Objects.equals(typeValue, operandVO.typeValue) &&
                Objects.equals(tip, operandVO.tip);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, typeValue, tip);
    }

    @Override
    public String toString() {
        return "OperandVO{" +
                "id=" + id +
                ", type='" + type + '\'' +
                ", typeValue=" + typeValue +
                ", tip='" + tip + '\'' +
                '}';
    }
}
