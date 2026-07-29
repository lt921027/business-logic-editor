package com.businesslogic.demo.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 数组过滤响应
 */
public class ArrayFilterResponse {
    
    /**
     * 是否成功
     */
    private boolean success;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 过滤后的结果列表
     */
    private List<FilterResult> results = new ArrayList<>();
    
    /**
     * 原始数组大小
     */
    private int originalSize;
    
    /**
     * 过滤后数组大     */
    private int filteredSize;
    
    /**
     * 生成JSONPath 表达     */
    private String generatedJsonPath;

    public ArrayFilterResponse() {
    }

    public ArrayFilterResponse(boolean success, String errorMessage, List<FilterResult> results, int originalSize, int filteredSize, String generatedJsonPath) {
        this.success = success;
        this.errorMessage = errorMessage;
        this.results = results;
        this.originalSize = originalSize;
        this.filteredSize = filteredSize;
        this.generatedJsonPath = generatedJsonPath;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public List<FilterResult> getResults() {
        return results;
    }

    public void setResults(List<FilterResult> results) {
        this.results = results;
    }

    public int getOriginalSize() {
        return originalSize;
    }

    public void setOriginalSize(int originalSize) {
        this.originalSize = originalSize;
    }

    public int getFilteredSize() {
        return filteredSize;
    }

    public void setFilteredSize(int filteredSize) {
        this.filteredSize = filteredSize;
    }

    public String getGeneratedJsonPath() {
        return generatedJsonPath;
    }

    public void setGeneratedJsonPath(String generatedJsonPath) {
        this.generatedJsonPath = generatedJsonPath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ArrayFilterResponse that = (ArrayFilterResponse) o;

        if (success != that.success) return false;
        if (originalSize != that.originalSize) return false;
        if (filteredSize != that.filteredSize) return false;
        if (errorMessage != null ? !errorMessage.equals(that.errorMessage) : that.errorMessage != null)
            return false;
        if (results != null ? !results.equals(that.results) : that.results != null) return false;
        return generatedJsonPath != null ? generatedJsonPath.equals(that.generatedJsonPath) : that.generatedJsonPath == null;
    }

    @Override
    public int hashCode() {
        int result = (success ? 1 : 0);
        result = 31 * result + (errorMessage != null ? errorMessage.hashCode() : 0);
        result = 31 * result + (results != null ? results.hashCode() : 0);
        result = 31 * result + originalSize;
        result = 31 * result + filteredSize;
        result = 31 * result + (generatedJsonPath != null ? generatedJsonPath.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ArrayFilterResponse{" +
                "success=" + success +
                ", errorMessage='" + errorMessage + '\'' +
                ", results=" + results +
                ", originalSize=" + originalSize +
                ", filteredSize=" + filteredSize +
                ", generatedJsonPath='" + generatedJsonPath + '\'' +
                '}';
    }

    /**
     * 过滤结果     */
    public static class FilterResult {
        
        /**
         * 索引
         */
        private int index;
        
        /**
         *          */
        private Object value;
        
        /**
         * 格式化后的值（JSON 字符串）
         */
        private String formattedValue;

        public FilterResult() {
        }

        public FilterResult(int index, Object value, String formattedValue) {
            this.index = index;
            this.value = value;
            this.formattedValue = formattedValue;
        }

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public Object getValue() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
        }

        public String getFormattedValue() {
            return formattedValue;
        }

        public void setFormattedValue(String formattedValue) {
            this.formattedValue = formattedValue;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            FilterResult that = (FilterResult) o;

            if (index != that.index) return false;
            if (value != null ? !value.equals(that.value) : that.value != null) return false;
            return formattedValue != null ? formattedValue.equals(that.formattedValue) : that.formattedValue == null;
        }

        @Override
        public int hashCode() {
            int result = index;
            result = 31 * result + (value != null ? value.hashCode() : 0);
            result = 31 * result + (formattedValue != null ? formattedValue.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "FilterResult{" +
                    "index=" + index +
                    ", value=" + value +
                    ", formattedValue='" + formattedValue + '\'' +
                    '}';
        }
    }
}
