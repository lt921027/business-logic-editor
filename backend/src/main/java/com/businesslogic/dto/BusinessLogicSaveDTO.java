package com.businesslogic.dto;

import java.util.List;

/**
 * 业务逻辑保存数据传输对象
 * 用于将前端可视化配置的业务规则传递到后端进行保存
 * 最终会被转换为 Aviator 表达式执 */
public class BusinessLogicSaveDTO {

    /**
     * 业务逻辑名称
     * 用于在列表中展示和检索，作为业务逻辑的唯一标识
     * 示例订单金额计算"用户等级评定"
     */
    private String name;

    /**
     * 业务逻辑描述
     * 对业务逻辑功能的详细描述，帮助理解该逻辑的用     * 示例计算订单总金额，包含商品金额、运费和折扣"
     */
    private String description;

    /**
     * 接口入参 JSON 示例
     * 存储接口入参JSON 格式模板，用于：
     * 1. 前端解析生成字段树，供用户配置时选择字段
     * 2. 作为参考模板，帮助用户了解可用的字段结     * 注意：该字段仅作为示例存储，不参与实际执     * 实际执行时使用的是调用接口时传入的动inputData
     * 示例：{"orderId":"12345","amount":100.00,"shippingFee":10.00}
     */
    private String jsonInput;

    /**
     * 业务逻辑步骤列表
     * 按执行顺序存储所有业务逻辑步骤，每个步骤是一LogicStepDTO 对象
     * 表达式生成器会遍历该列表，生成完整的 Aviator 表达     * 限制：最多支5 个步     */
    private List<LogicStepDTO> logicSteps;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getJsonInput() {
        return jsonInput;
    }

    public void setJsonInput(String jsonInput) {
        this.jsonInput = jsonInput;
    }

    public List<LogicStepDTO> getLogicSteps() {
        return logicSteps;
    }

    public void setLogicSteps(List<LogicStepDTO> logicSteps) {
        this.logicSteps = logicSteps;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BusinessLogicSaveDTO that = (BusinessLogicSaveDTO) o;

        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (description != null ? !description.equals(that.description) : that.description != null) return false;
        if (jsonInput != null ? !jsonInput.equals(that.jsonInput) : that.jsonInput != null) return false;
        return logicSteps != null ? logicSteps.equals(that.logicSteps) : that.logicSteps == null;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (jsonInput != null ? jsonInput.hashCode() : 0);
        result = 31 * result + (logicSteps != null ? logicSteps.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "BusinessLogicSaveDTO{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", jsonInput='" + jsonInput + '\'' +
                ", logicSteps=" + logicSteps +
                '}';
    }
}
