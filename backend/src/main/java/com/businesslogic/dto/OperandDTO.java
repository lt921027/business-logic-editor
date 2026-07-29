package com.businesslogic.dto;

public class OperandDTO {

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

        OperandDTO that = (OperandDTO) o;

        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (type != null ? !type.equals(that.type) : that.type != null) return false;
        if (typeValue != null ? !typeValue.equals(that.typeValue) : that.typeValue != null) return false;
        return tip != null ? tip.equals(that.tip) : that.tip == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (typeValue != null ? typeValue.hashCode() : 0);
        result = 31 * result + (tip != null ? tip.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "OperandDTO{" +
                "id=" + id +
                ", type='" + type + '\'' +
                ", typeValue=" + typeValue +
                ", tip='" + tip + '\'' +
                '}';
    }
}
