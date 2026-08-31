package com.businesslogic.dto;

import java.util.List;

/**
 * 构建 inputData 请求 DTO
 *
 * <p>前端调用 {@code /business-logic/build-input-data} 时的请求体：</p>
 * <ul>
 *   <li>{@code expression}：表达式（可选），传了则用 Groovy 引擎执行构建好的 inputData</li>
 *   <li>{@code params}：表达式参数列表，每个参数是 {@link JsonPathParamDTO} 对象</li>
 * </ul>
 */
public class BuildInputDataRequestDTO {

    /** 表达式（可选） */
    private String expression;

    /** 表达式参数列表 */
    private List<JsonPathParamDTO> params;

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    public List<JsonPathParamDTO> getParams() {
        return params;
    }

    public void setParams(List<JsonPathParamDTO> params) {
        this.params = params;
    }
}
