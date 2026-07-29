package com.businesslogic.demo.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.businesslogic.demo.model.ArrayFilterRequest;
import com.businesslogic.demo.model.ArrayFilterResponse;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 数组过滤服务（支持复杂逻辑 */
@Service
public class ArrayFilterService {
    
    @Autowired(required = false)
    private FunctionRegistry functionRegistry;
    
    /**
     * 执行数组过滤（支持复杂逻辑     */
    public ArrayFilterResponse filterArray(ArrayFilterRequest request) {
        ArrayFilterResponse response = new ArrayFilterResponse();
        
        try {
            // 验证输入
            if (request.getJsonText() == null || request.getJsonText().trim().isEmpty()) {
                response.setSuccess(false);
                response.setErrorMessage("JSON 文本不能为空");
                return response;
            }
            
            if (request.getArrayPath() == null || request.getArrayPath().trim().isEmpty()) {
                response.setSuccess(false);
                response.setErrorMessage("数组路径不能为空");
                return response;
            }
            
            if (request.getRootGroup() == null || request.getRootGroup().getItems() == null || request.getRootGroup().getItems().isEmpty()) {
                response.setSuccess(false);
                response.setErrorMessage("过滤条件不能为空");
                return response;
            }
            
            // 解析 JSON
            ReadContext ctx = JsonPath.parse(request.getJsonText());
            
            // 获取原始数组
            Object originalArray = ctx.read(request.getArrayPath());

            // 转换为fastjson的JSONArray
            JSONArray array;
            if (originalArray instanceof List) {
                // JsonPath可能返回List
                array = JSONArray.parseArray(JSONObject.toJSONString(originalArray));
            } else if (originalArray instanceof JSONArray) {
                // JsonPath可能返回JSONArray
                array = JSONArray.parseArray(originalArray.toString());
            } else if (originalArray instanceof JSONArray) {
                // 已经是fastjson的JSONArray
                array = (JSONArray) originalArray;
            } else {
                response.setSuccess(false);
                response.setErrorMessage("指定路径不是数组类型");
                return response;
            }
            
            response.setOriginalSize(array.size());
            
            //生成 JSONPath 过滤表达
            String filterExpression = generateFilterExpression(request.getArrayPath(), request.getRootGroup());
            response.setGeneratedJsonPath(filterExpression);
            
            // 执行过滤（使用递归评估            
            List<ArrayFilterResponse.FilterResult> filteredResults = new ArrayList<>();
            for (int i = 0; i < array.size(); i++) {
                Object item = array.get(i);
                if (evaluateLogicGroup(item, request.getRootGroup())) {
                    ArrayFilterResponse.FilterResult result = new ArrayFilterResponse.FilterResult();
                    result.setIndex(i);
                    result.setValue(item);
                    result.setFormattedValue(formatObject(item));
                    filteredResults.add(result);
                }
            }
            
            response.setResults(filteredResults);
            response.setFilteredSize(filteredResults.size());
            response.setSuccess(true);
            
        } catch (Exception e) {
            response.setSuccess(false);
            response.setErrorMessage("过滤失败" + e.getMessage());
            e.printStackTrace();
        }
        
        return response;
    }
    
    /**
     * 生成 JSONPath 过滤表达式（用于显示     */
    private String generateFilterExpression(String arrayPath, ArrayFilterRequest.LogicGroup rootGroup) {
        // $['order'].items[*] 转换$['order'].items
        String basePath = arrayPath.replace("[*]", "");
        
        StringBuilder expression = new StringBuilder();
        expression.append(basePath);
        expression.append("[?(");
        
        //递归生成条件表达
        expression.append(generateGroupExpression(rootGroup));
        
        expression.append(")]");
        return expression.toString();
    }
    
    /**
     * 递归生成逻辑组的表达     */
    private String generateGroupExpression(ArrayFilterRequest.LogicGroup group) {
        if (group.getItems() == null || group.getItems().isEmpty()) {
            return "true";
        }
        
        List<String> itemExpressions = new ArrayList<>();
        for (ArrayFilterRequest.ConditionItem item : group.getItems()) {
            if ("condition".equals(item.getType())) {
                itemExpressions.add(generateConditionExpression(item.getCondition()));
            } else if ("group".equals(item.getType()) && item.getGroup() != null) {
                itemExpressions.add("(" + generateGroupExpression(item.getGroup()) + ")");
            }
        }
        
        if (itemExpressions.isEmpty()) {
            return "true";
        }
        
        return String.join(" " + group.getOperator() + " ", itemExpressions);
    }
    
    /**
     * 生成单个条件的表达式
     */
    private String generateConditionExpression(ArrayFilterRequest.SimpleCondition condition) {
        String field = condition.getField();
        String operator = condition.getOperator();
        String value = condition.getValue();
        String valueType = condition.getValueType();
        Boolean useFunction = condition.getUseFunction();
        String functionName = condition.getFunctionName();
        List<String> functionParams = condition.getFunctionParams();
        Boolean negate = condition.getNegate();
        
        //格式化
        String formattedValue = formatValue(value, valueType);
        
        //构建字段表达
        String fieldExpr;
        if (Boolean.TRUE.equals(useFunction) && functionName != null && !functionName.isEmpty()) {
            fieldExpr = buildFunctionExpression(field, functionName, functionParams);
        } else {
            fieldExpr = "@." + field;
        }
        
        // 构建运算符表达式
        String expr = buildOperatorExpression(fieldExpr, operator, formattedValue, valueType);
        
        // 处理取反
        if (Boolean.TRUE.equals(negate)) {
            expr = "!(" + expr + ")";
        }
        
        return expr;
    }
    
    /**
     * 递归评估逻辑     */
    @SuppressWarnings("unchecked")
    private boolean evaluateLogicGroup(Object item, ArrayFilterRequest.LogicGroup group) {
        if (group.getItems() == null || group.getItems().isEmpty()) {
            return true;
        }
        
        // JSONPath 返回的是 LinkedHashMap
        if (!(item instanceof Map)) {
            return false;
        }
        
        Map<String, Object> map = (Map<String, Object>) item;
        boolean andLogic = "AND".equals(group.getOperator());
        
        for (ArrayFilterRequest.ConditionItem conditionItem : group.getItems()) {
            boolean result;
            
            if ("condition".equals(conditionItem.getType())) {
                result = evaluateCondition(map, conditionItem.getCondition());
            } else if ("group".equals(conditionItem.getType()) && conditionItem.getGroup() != null) {
                result = evaluateLogicGroup(item, conditionItem.getGroup());
            } else {
                result = true;
            }
            
            if (andLogic) {
                // AND 逻辑：只要有一个为 false 就返false
                if (!result) {
                    return false;
                }
            } else {
                // OR 逻辑：只要有一个为 true 就返true
                if (result) {
                    return true;
                }
            }
        }
        
        // AND 逻辑所有都true 才返true，OR 逻辑所有都false 返回 false
        return andLogic;
    }
    
    /**
     * 评估单个条件
     */
    @SuppressWarnings("unchecked")
    private boolean evaluateCondition(Map<String, Object> map, ArrayFilterRequest.SimpleCondition condition) {
        //获取字段值（支持嵌套路径
        Object fieldValue = getNestedFieldValue(map, condition.getField());
        if (fieldValue == null && !"null".equals(condition.getValueType())) {
            return false;
        }
        
        //如果使用了函数，先执行函
        if (Boolean.TRUE.equals(condition.getUseFunction()) && condition.getFunctionName() != null) {
            fieldValue = applyFunction(fieldValue, condition.getFunctionName(), condition.getFunctionParams());
        }
        
        String operator = condition.getOperator();
        String value = condition.getValue();
        String valueType = condition.getValueType();
        
        try {
            // 根据类型进行比较
            if ("number".equals(valueType)) {
                if (fieldValue == null) return false;
                double fieldValueNum = Double.parseDouble(fieldValue.toString());
                double valueNum = Double.parseDouble(value);
                
                boolean result = compareNumbers(fieldValueNum, operator, valueNum);
                return Boolean.TRUE.equals(condition.getNegate()) ? !result : result;
            } else if ("boolean".equals(valueType)) {
                boolean fieldValueBool = Boolean.parseBoolean(fieldValue.toString());
                boolean valueBool = Boolean.parseBoolean(value);
                
                boolean result;
                if ("==".equals(operator)) {
                    result = fieldValueBool == valueBool;
                } else if ("!=".equals(operator)) {
                    result = fieldValueBool != valueBool;
                } else {
                    result = false;
                }
                return Boolean.TRUE.equals(condition.getNegate()) ? !result : result;
            } else {
                //字符串比
                String fieldValueStr = fieldValue == null ? "" : fieldValue.toString();
                
                boolean result = compareStrings(fieldValueStr, operator, value);
                return Boolean.TRUE.equals(condition.getNegate()) ? !result : result;
            }
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 数字比较
     */
    private boolean compareNumbers(double fieldValue, String operator, double value) {
        switch (operator) {
            case "==": return fieldValue == value;
            case "!=": return fieldValue != value;
            case ">": return fieldValue > value;
            case "<": return fieldValue < value;
            case ">=": return fieldValue >= value;
            case "<=": return fieldValue <= value;
            default: return fieldValue == value;
        }
    }
    
    /**
     * 字符串比     */
    private boolean compareStrings(String fieldValue, String operator, String value) {
        switch (operator) {
            case "==": return fieldValue.equals(value);
            case "!=": return !fieldValue.equals(value);
            case "contains": return fieldValue.contains(value);
            case "startsWith": return fieldValue.startsWith(value);
            case "endsWith": return fieldValue.endsWith(value);
            case ">": return fieldValue.compareTo(value) > 0;
            case "<": return fieldValue.compareTo(value) < 0;
            case ">=": return fieldValue.compareTo(value) >= 0;
            case "<=": return fieldValue.compareTo(value) <= 0;
            default: return fieldValue.equals(value);
        }
    }
    
    /**
     * 格式化     */
    private String formatValue(String value, String valueType) {
        if ("number".equals(valueType) || "boolean".equals(valueType)) {
            return value;
        }
        //字符串类型需要加引号并转
        return "'" + value.replace("'", "\\'") + "'";
    }
    
    /**
     * 构建运算符表达式（用于生JSONPath     */
    private String buildOperatorExpression(String field, String operator, String formattedValue, String valueType) {
        switch (operator) {
            case "==": return field + " == " + formattedValue;
            case "!=": return field + " != " + formattedValue;
            case ">": return field + " > " + formattedValue;
            case "<": return field + " < " + formattedValue;
            case ">=": return field + " >= " + formattedValue;
            case "<=": return field + " <= " + formattedValue;
            case "contains": return field + " =~ /.*" + escapeRegex(formattedValue) + ".*/";
            case "startsWith": return field + " =~ /^" + escapeRegex(formattedValue) + ".*/";
            case "endsWith": return field + " =~ /.*" + escapeRegex(formattedValue) + "$/";
            default: return field + " == " + formattedValue;
        }
    }
    
    /**
     * 构建函数表达     */
    private String buildFunctionExpression(String field, String functionName, List<String> params) {
        switch (functionName.toLowerCase()) {
            case "length":
                return "jsonpath:length(" + field + ")";
            case "size":
                return "jsonpath:size(" + field + ")";
            case "touppercase":
                return "jsonpath:toUpperCase(" + field + ")";
            case "tolowercase":
                return "jsonpath:toLowerCase(" + field + ")";
            case "substring":
                if (params != null && params.size() >= 1) {
                    String start = params.get(0);
                    if (params.size() >= 2 && params.get(1) != null && !params.get(1).isEmpty()) {
                        String end = params.get(1);
                        return "jsonpath:substring(" + field + "," + start + "," + end + ")";
                    } else {
                        return "jsonpath:substring(" + field + "," + start + ")";
                    }
                }
                return field;
            default:
                return field;
        }
    }
    
    /**
     * 转义正则表达式特殊字     */
    private String escapeRegex(String value) {
        String rawValue = value.replace("'", "");
        return rawValue
            .replace("\\", "\\\\")
            .replace(".", "\\.")
            .replace("*", "\\*")
            .replace("?", "\\?")
            .replace("+", "\\+")
            .replace("^", "\\^")
            .replace("$", "\\$")
            .replace("[", "\\[")
            .replace("]", "\\]")
            .replace("(", "\\(")
            .replace(")", "\\)")
            .replace("{", "\\{")
            .replace("}", "\\}")
            .replace("|", "\\|");
    }
    
    /**
     * 获取嵌套字段     */
    @SuppressWarnings("unchecked")
    private Object getNestedFieldValue(Map<String, Object> map, String fieldPath) {
        if (fieldPath == null || fieldPath.isEmpty()) {
            return null;
        }
        
        String[] parts = fieldPath.split("\\.");
        Object current = map;
        
        for (String part : parts) {
            if (current == null) {
                return null;
            }
            
            if (part.contains("[")) {
                current = getArrayElement(current, part);
            } else if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                return null;
            }
        }
        
        return current;
    }
    
    /**
     * 获取数组元素
     */
    @SuppressWarnings("unchecked")
    private Object getArrayElement(Object arrayObj, String indexStr) {
        try {
            int index = Integer.parseInt(indexStr.replaceAll("[^0-9]", ""));
            
            if (arrayObj instanceof List) {
                List<?> list = (List<?>) arrayObj;
                return index < list.size() ? list.get(index) : null;
            }
            
            if (arrayObj.getClass().isArray()) {
                return index < java.lang.reflect.Array.getLength(arrayObj) ? 
                    java.lang.reflect.Array.get(arrayObj, index) : null;
            }
            
            return null;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 应用函数
     */
    private Object applyFunction(Object value, String functionName, List<String> params) {
        //优先使用注册的函
        if (functionRegistry != null && functionRegistry.hasFunction(functionName)) {
            BuiltinFunction function = functionRegistry.getFunction(functionName);
            return function.execute(value, params);
        }
        
        // 内置函数
        return executeBuiltinFunction(value, functionName, params);
    }
    
    /**
     * 执行内置函数
     */
    private Object executeBuiltinFunction(Object value, String functionName, List<String> params) {
        switch (functionName.toLowerCase()) {
            case "length":
                if (value instanceof String) {
                    return ((String) value).length();
                } else if (value instanceof Collection) {
                    return ((Collection<?>) value).size();
                } else if (value instanceof Map) {
                    return ((Map<?, ?>) value).size();
                } else if (value != null && value.getClass().isArray()) {
                    return java.lang.reflect.Array.getLength(value);
                }
                return 0;
                
            case "size":
                if (value instanceof Collection) {
                    return ((Collection<?>) value).size();
                } else if (value instanceof Map) {
                    return ((Map<?, ?>) value).size();
                }
                return 0;
                
            case "touppercase":
                if (value instanceof String) {
                    return ((String) value).toUpperCase();
                }
                return value != null ? value.toString().toUpperCase() : "";
                
            case "tolowercase":
                if (value instanceof String) {
                    return ((String) value).toLowerCase();
                }
                return value != null ? value.toString().toLowerCase() : "";
                
            case "substring":
                if (value instanceof String) {
                    String str = (String) value;
                    if (params != null && params.size() >= 1) {
                        try {
                            int start = Integer.parseInt(params.get(0));
                            if (params.size() >= 2 && params.get(1) != null && !params.get(1).isEmpty()) {
                                int end = Integer.parseInt(params.get(1));
                                return str.substring(start, Math.min(end, str.length()));
                            } else {
                                return str.substring(start);
                            }
                        } catch (NumberFormatException e) {
                            return str;
                        }
                    }
                    return str;
                }
                return value != null ? value.toString() : "";
                
            default:
                return value;
        }
    }
    
    /**
     * 格式化对象为字符     */
    private String formatObject(Object obj) {
        if (obj == null) {
            return "null";
        }
        if (obj instanceof String) {
            return (String) obj;
        }
        
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (Exception e) {
            return obj.toString();
        }
    }
}
