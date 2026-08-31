package com.businesslogic.dto;

/**
 * 表达式参数节点 DTO
 *
 * <p>前端回传表达式的参数时，每个参数节点包含三个字段：</p>
 * <ul>
 *   <li>{@code jsonPath}：节点在 JSON 中的路径（如 "A.B.C"、"$.A.B"、"arr[0].name"）</li>
 *   <li>{@code value}：用户输入的值；当 jsonType 为 object/array 时，用户直接输入 JSON 字符串</li>
 *   <li>{@code jsonType}：节点类型（string、number、boolean、null、object、array）</li>
 * </ul>
 *
 * <p>由 {@link com.businesslogic.util.InputDataBuilder#buildInputDataJson} 消费，
 * 将参数列表还原为表达式可用的 inputData JSON 字符串。</p>
 */
public class JsonPathParamDTO {

    /** 节点在 JSON 中的路径 */
    private String jsonPath;

    /** 用户输入的值；object/array 类型时为 JSON 字符串 */
    private Object value;

    /** 节点类型：string、number、boolean、null、object、array */
    private String jsonType;

    /** 结果 */
    private String result;

    public JsonPathParamDTO() {
    }

    public JsonPathParamDTO(String jsonPath, Object value, String jsonType) {
        this.jsonPath = jsonPath;
        this.value = value;
        this.jsonType = jsonType;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getJsonPath() {
        return jsonPath;
    }

    public void setJsonPath(String jsonPath) {
        this.jsonPath = jsonPath;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public String getJsonType() {
        return jsonType;
    }

    public void setJsonType(String jsonType) {
        this.jsonType = jsonType;
    }

    @Override
    public String toString() {
        return "JsonPathParamDTO{" +
                "jsonPath='" + jsonPath + '\'' +
                ", value=" + value +
                ", jsonType='" + jsonType + '\'' +
                '}';
    }
}
