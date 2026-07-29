package com.businesslogic.demo.model;

import java.util.List;

/**
 * JSON 路径解析请求
 */
public class JsonPathRequest {
    
    /**
     * JSON 文本
     */
    private String jsonText;
    
    /**
     * 已选择的字段路径列     */
    private List<String> selectedPaths;

    public String getJsonText() {
        return jsonText;
    }

    public void setJsonText(String jsonText) {
        this.jsonText = jsonText;
    }

    public List<String> getSelectedPaths() {
        return selectedPaths;
    }

    public void setSelectedPaths(List<String> selectedPaths) {
        this.selectedPaths = selectedPaths;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        JsonPathRequest that = (JsonPathRequest) o;

        if (jsonText != null ? !jsonText.equals(that.jsonText) : that.jsonText != null) return false;
        return selectedPaths != null ? selectedPaths.equals(that.selectedPaths) : that.selectedPaths == null;
    }

    @Override
    public int hashCode() {
        int result = jsonText != null ? jsonText.hashCode() : 0;
        result = 31 * result + (selectedPaths != null ? selectedPaths.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "JsonPathRequest{" +
                "jsonText='" + jsonText + '\'' +
                ", selectedPaths=" + selectedPaths +
                '}';
    }
}
