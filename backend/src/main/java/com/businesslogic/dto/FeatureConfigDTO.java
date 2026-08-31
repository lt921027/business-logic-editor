package com.businesslogic.dto;

import lombok.Data;

import java.util.List;

public class FeatureConfigDTO {

    private  String featureName;
    private String featureCode;
    private String featureDefaultValue;
    private String featureDataType;
    private String status;
    private String version;
    private String featureLogicDesc;
    private String tradeCode;
    private String queryType;
    private String messageCode;
    private String messageId;
    private String createdBy;
    private String createdTime;
    private String updatedBy;
    private String updatedTime;
    private String runExpress;//特征脚本
    private List<LogicStepDTO> logicSteps;
    private List<JsonPathParamDTO> jsonPathParams;

    public String getFeatureName() {
        return featureName;
    }

    public void setFeatureName(String featureName) {
        this.featureName = featureName;
    }

    public String getFeatureCode() {
        return featureCode;
    }

    public void setFeatureCode(String featureCode) {
        this.featureCode = featureCode;
    }

    public String getFeatureDefaultValue() {
        return featureDefaultValue;
    }

    public void setFeatureDefaultValue(String featureDefaultValue) {
        this.featureDefaultValue = featureDefaultValue;
    }

    public String getFeatureDataType() {
        return featureDataType;
    }

    public void setFeatureDataType(String featureDataType) {
        this.featureDataType = featureDataType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getFeatureLogicDesc() {
        return featureLogicDesc;
    }

    public void setFeatureLogicDesc(String featureLogicDesc) {
        this.featureLogicDesc = featureLogicDesc;
    }

    public String getTradeCode() {
        return tradeCode;
    }

    public void setTradeCode(String tradeCode) {
        this.tradeCode = tradeCode;
    }

    public String getQueryType() {
        return queryType;
    }

    public void setQueryType(String queryType) {
        this.queryType = queryType;
    }

    public String getMessageCode() {
        return messageCode;
    }

    public void setMessageCode(String messageCode) {
        this.messageCode = messageCode;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(String createdTime) {
        this.createdTime = createdTime;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public String getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(String updatedTime) {
        this.updatedTime = updatedTime;
    }

    public String getRunExpress() {
        return runExpress;
    }

    public void setRunExpress(String runExpress) {
        this.runExpress = runExpress;
    }

    public List<LogicStepDTO> getLogicSteps() {
        return logicSteps;
    }

    public void setLogicSteps(List<LogicStepDTO> logicSteps) {
        this.logicSteps = logicSteps;
    }

    public List<JsonPathParamDTO> getJsonPathParams() {
        return jsonPathParams;
    }

    public void setJsonPathParams(List<JsonPathParamDTO> jsonPathParams) {
        this.jsonPathParams = jsonPathParams;
    }
}
