package com.businesslogic.demo.service;

import com.businesslogic.demo.model.AviatorResponse;
import com.businesslogic.demo.model.AviatorResponse.StepExpression;
import com.businesslogic.demo.model.BusinessLogicRequest;
import com.businesslogic.demo.model.BusinessLogicRequest.LogicStep;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AviatorScript 表达式生成服 */
@Service
public class AviatorExpressionService {
    
    /**
     * 根据业务逻辑步骤生成 AviatorScript 表达     */
    public AviatorResponse generateExpression(BusinessLogicRequest request) {
        try {
            if (request.getSteps() == null || request.getSteps().isEmpty()) {
                return AviatorResponse.builder()
                        .success(false)
                        .errorMessage("业务逻辑步骤不能为空")
                        .build();
            }
            
            List<StepExpression> stepExpressions = new ArrayList<>();
            StringBuilder fullScript = new StringBuilder();
            
            //添加注释
            fullScript.append("// ============================================\n");
            fullScript.append("// AviatorScript 业务逻辑表达式\n");
            fullScript.append("// ============================================\n\n");
            
            // 处理每个步骤
            for (int i = 0; i < request.getSteps().size(); i++) {
                LogicStep step = request.getSteps().get(i);
                String expression = generateStepExpression(step, i);
                
                if (expression != null && !expression.trim().isEmpty()) {
                    String outputVar = step.getOutputVar() != null && !step.getOutputVar().isEmpty() 
                            ? step.getOutputVar() 
                            : "result" + (i + 1);
                    
                    StepExpression stepExpr = StepExpression.builder()
                            .stepIndex(i)
                            .comment(step.getComment())
                            .expression(expression)
                            .outputVar(outputVar)
                            .build();
                    
                    stepExpressions.add(stepExpr);
                    
                    //添加到完整脚
                    fullScript.append("// 步骤 ").append(i + 1);
                    if (step.getComment() != null && !step.getComment().isEmpty()) {
                        fullScript.append(": ").append(step.getComment());
                    }
                    fullScript.append("\n");
                    fullScript.append(outputVar).append(" = ").append(expression).append(";\n\n");
                }
            }
            
            //添加完整表达
            if (!stepExpressions.isEmpty()) {
                fullScript.append("// ============================================\n");
                fullScript.append("// 完整表达式（按顺序执行）\n");
                fullScript.append("// ============================================\n");
                
                String fullExpression = stepExpressions.stream()
                        .map(se -> se.getOutputVar() + " = " + se.getExpression())
                        .collect(Collectors.joining("; "));
                
                fullScript.append(fullExpression).append(";\n");
            }
            
            return AviatorResponse.builder()
                    .success(true)
                    .aviatorScript(fullScript.toString())
                    .stepExpressions(stepExpressions)
                    .build();
                    
        } catch (Exception e) {
            return AviatorResponse.builder()
                    .success(false)
                    .errorMessage("生成 AviatorScript 表达式失败：" + e.getMessage())
                    .build();
        }
    }
    
    /**
     * 生成单个步骤的表达式
     */
    private String generateStepExpression(LogicStep step, int index) {
        if (step.getOperationType() == null) {
            return "";
        }
        
        switch (step.getOperationType()) {
            case "field":
                return generateFieldExpression(step);
            
            case "function":
                return generateFunctionExpression(step);
            
            case "expression":
                return generateCustomExpression(step);
            
            case "condition":
                return generateConditionExpression(step);
            
            default:
                return "";
        }
    }
    
    /**
     * 生成字段表达     */
    private String generateFieldExpression(LogicStep step) {
        if (step.getField() == null || step.getField().isEmpty()) {
            return "";
        }
        return "input." + step.getField();
    }
    
    /**
     * 生成函数表达     */
    private String generateFunctionExpression(LogicStep step) {
        if (step.getFunctionName() == null || step.getFunctionName().isEmpty()) {
            return "";
        }
        
        String functionName = step.getFunctionName();
        List<String> params = step.getParams();
        
        if (params == null || params.isEmpty()) {
            return functionName + "()";
        }
        
        String paramsStr = params.stream()
                .map(this::formatParam)
                .collect(Collectors.joining(", "));
        
        return functionName + "(" + paramsStr + ")";
    }
    
    /**
     * 生成自定义表达式
     */
    private String generateCustomExpression(LogicStep step) {
        if (step.getExpression() == null || step.getExpression().isEmpty()) {
            return "";
        }
        
        // 替换字段引用
        String expression = step.getExpression();
        if (expression.contains(".")) {
            // 简单的字段替换逻辑，可以根据需要增            
            String[] parts = expression.split("\\.");
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i].trim();
                if (i > 0 && !part.contains(" ") && !isOperator(part)) {
                    parts[i] = "input." + part;
                }
            }
            expression = String.join(".", parts);
        }
        
        return expression;
    }
    
    /**
     * 生成条件表达     */
    private String generateConditionExpression(LogicStep step) {
        if (step.getConditions() == null || step.getConditions().isEmpty()) {
            return "";
        }
        
        List<String> conditions = new ArrayList<>();
        for (int i = 0; i < step.getConditions().size(); i++) {
            LogicStep.Condition cond = step.getConditions().get(i);
            String condition = buildCondition(cond);
            
            if (i > 0 && cond.getLogicOperator() != null) {
                String logicOp = "AND".equals(cond.getLogicOperator()) ? " && " : " || ";
                conditions.add(logicOp + condition);
            } else {
                conditions.add(condition);
            }
        }
        
        return String.join("", conditions);
    }
    
    /**
     * 构建单个条件
     */
    private String buildCondition(LogicStep.Condition cond) {
        if (cond.getField() == null || cond.getField().isEmpty()) {
            return "";
        }
        
        String field = "input." + cond.getField();
        String operator = cond.getOperator();
        String value;
        
        if ("value".equals(cond.getValueType())) {
            value = formatValue(cond.getValue(), operator);
        } else if ("field".equals(cond.getValueType()) && cond.getValueField() != null) {
            value = "input." + cond.getValueField();
        } else {
            value = "nil";
        }
        
        //特殊操作符处
        if ("contains".equals(operator)) {
            return "string.contains(" + field + ", " + value + ")";
        } else if ("startsWith".equals(operator)) {
            return "string.startsWith(" + field + ", " + value + ")";
        } else if ("endsWith".equals(operator)) {
            return "string.endsWith(" + field + ", " + value + ")";
        }
        
        return field + " " + operator + " " + value;
    }
    
    /**
     * 格式化参     */
    private String formatParam(String param) {
        if (param == null || param.isEmpty()) {
            return "nil";
        }
        
        //如果已经是字段引
        if (param.startsWith("input.")) {
            return param;
        }
        
        //尝试转换为数
        try {
            if (param.matches("-?\\d+")) {
                return param; // 整数
            }
            if (param.matches("-?\\d+\\.\\d+")) {
                return param; // 浮点
                }
        } catch (Exception e) {
            // 忽略
        }
        
        //作为字符串处
        return "'" + param + "'";
    }
    
    /**
     * 格式化     */
    private String formatValue(String value, String operator) {
        if (value == null) {
            return "nil";
        }
        
        //字符串操作符需要引
        if ("contains".equals(operator) || "startsWith".equals(operator) || "endsWith".equals(operator)) {
            return "'" + value + "'";
        }
        
        //尝试转换为数
        try {
            if (value.matches("-?\\d+")) {
                return value;
            }
            if (value.matches("-?\\d+\\.\\d+")) {
                return value;
            }
        } catch (Exception e) {
            // 忽略
        }
        
        //作为字符串处
        return "'" + value + "'";
    }
    
    /**
     * 判断是否为操作符
     */
    private boolean isOperator(String str) {
        return "+".equals(str) || "-".equals(str) || "*".equals(str) || "/".equals(str) ||
               "==".equals(str) || "!=".equals(str) || ">".equals(str) || "<".equals(str) ||
               ">=".equals(str) || "<=".equals(str) || "&&".equals(str) || "||".equals(str);
    }
}
