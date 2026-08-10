package com.businesslogic.generator;

import com.businesslogic.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class AviatorExpressionGenerator {

    private static final Logger logger = LoggerFactory.getLogger(AviatorExpressionGenerator.class);

    public String generate(BusinessLogicSaveDTO dto) {
        StringBuilder expression = new StringBuilder();

        List<LogicStepDTO> steps = dto.getLogicSteps();
        for (int i = 0; i < steps.size(); i++) {
            LogicStepDTO step = steps.get(i);
            String stepExpression = generateStepExpression(step, i + 1);
            expression.append(stepExpression).append("\n");
        }

        return expression.toString();
    }

    private String generateStepExpression(LogicStepDTO step, int stepNum) {
        String category = getFirstElement(step.getFunctionCategory());

        if ("direct".equals(category)) {
            return generateDirectMapping(step, stepNum);
        } else if ("calculation".equals(category)) {
            return generateCalculation(step, stepNum);
        } else if ("filter".equals(category)) {
            return generateFilter(step, stepNum);
        } else if ("custom".equals(category)) {
            return generateCustomExpression(step, stepNum);
        } else {
            return "";
        }
    }

    private String generateDirectMapping(LogicStepDTO step, int stepNum) {
        String varName = step.getOutputVar() != null ? step.getOutputVar() : "step" + stepNum;
        String fieldPath = generateFieldExpression(step.getMappedField());
        return "let " + varName + " = " + fieldPath + ";";
    }

    private String generateCalculation(LogicStepDTO step, int stepNum) {
        String varName = step.getOutputVar() != null ? step.getOutputVar() : "step" + stepNum;

        if (step.getCalculationSteps() == null || step.getCalculationSteps().isEmpty()) {
            return "";
        }

        List<CalculationStepDTO> calcSteps = step.getCalculationSteps();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < calcSteps.size(); i++) {
            CalculationStepDTO calcStep = calcSteps.get(i);

            if (i > 0 && calcStep.getLogicOperator() != null) {
                sb.append(" ").append(calcStep.getLogicOperator()).append(" ");
            }

            String stepExpr = generateCalculationStepExpression(calcStep);
            // 只有在有多个计算步骤时才添加括号
            if (calcSteps.size() > 1) {
                sb.append("(").append(stepExpr).append(")");
            } else {
                sb.append(stepExpr);
            }
        }

        return "let " + varName + " = " + sb.toString() + ";";
    }

    private String generateCalculationStepExpression(CalculationStepDTO calcStep) {
        String function = calcStep.getFilterFunction();
        List<OperandDTO> operands = calcStep.getOperands();

        if (operands == null || operands.isEmpty()) {
            // 无操作数时返回该分类的中性兜底值，避免生成 `let x = ` 这类非法脚本
            String category = getFirstElement(calcStep.getFunctionCategory());
            if ("string".equals(category)) {
                return "''";
            } else if ("date".equals(category)) {
                return "nil";
            }
            return "0";
        }

        String category = getFirstElement(calcStep.getFunctionCategory());

        if ("string".equals(category)) {
            return generateStringFunction(function, operands,null,null);
        } else if ("number".equals(category)) {
            return generateNumberFunction(function, operands,null,null);
        } else if ("date".equals(category)) {
            return generateDateFunction(function, operands,null,null);
        } else {
            return "";
        }
    }


    private String generateStringFunction(String function, List<OperandDTO> operands, String filterScope, String loopVar) {
        List<String> operandExprs = operands.stream()
                .map(operand -> filterScope != null && !filterScope.isEmpty() 
                    ? generateOperandExpressionInLoop(operand, "string", filterScope, loopVar)
                    : generateOperandExpression(operand, "string"))
                .collect(Collectors.toList());

        if ("includes".equals(function)) {
            // string.contains(a, b)；不足 2 个操作数时生成 false，避免运行时参数个数错误
            if (operandExprs.size() < 2) {
                return "false";
            }
            return "string.contains(" + String.join(", ", operandExprs) + ")";
        } else if ("concat".equals(function)) {
            // Aviator 5.x 没有 seq.concat/string.concat（裸 concat 是序列构造器），
            // 用 string.join(seq.list(...), '') 拼接任意多个字符串
            return "string.join(seq.list(" + String.join(", ", operandExprs) + "), '')";
        } else if ("equals".equals(function)) {
            if (operandExprs.size() < 2) {
                return "false";
            }
            return "StringUtil.equals(" + String.join(", ", operandExprs) + ")";
        } else if ("length".equals(function)) {
            // string.length(s)；无操作数时生成 0
            if (operandExprs.isEmpty()) {
                return "0";
            }
            return "string.length(" + String.join(", ", operandExprs) + ")";
        } else {
            return "";
        }
    }


    private String generateNumberFunction(String function, List<OperandDTO> operands, String filterScope, String loopVar) {
        if ("arithmetic".equals(function)) {
            return generateArithmeticExpression(operands, filterScope, loopVar);
        }

        String operandStr = operands.stream()
                .map(operand -> filterScope != null && !filterScope.isEmpty() 
                    ? generateOperandExpressionInLoop(operand, "number", filterScope, loopVar)
                    : generateOperandExpression(operand, "number"))
                .collect(Collectors.joining(", "));

        if ("max".equals(function)) {
            return "max(" + operandStr + ")";
        } else if ("min".equals(function)) {
            return "min(" + operandStr + ")";
        } else if ("sum".equals(function)) {
            return "reduce(list(" + operandStr + "), 0, lambda(x, y) -> x + y end)";
        } else if ("avg".equals(function)) {
            return "reduce(list(" + operandStr + "), 0, lambda(x, y) -> x + y end) / count(list(" + operandStr + "))";
        } else {
            return "";
        }
    }


    private String generateArithmeticExpression(List<OperandDTO> operands, String filterScope, String loopVar) {
        if (operands == null || operands.isEmpty()) {
            return "0";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < operands.size(); i++) {
            OperandDTO operand = operands.get(i);
            String expr = filterScope != null && !filterScope.isEmpty()
                    ? generateOperandExpressionInLoop(operand, "number", filterScope, loopVar)
                    : generateOperandExpression(operand, "number");

            if (i == 0) {
                sb.append(expr);
            } else if ("operator".equals(operand.getType())) {
                sb.append(" ").append(operand.getTypeValue()).append(" ");
            } else {
                sb.append(expr);
            }
        }

        return sb.toString();
    }


    private String generateDateFunction(String function, List<OperandDTO> operands, String filterScope, String loopVar) {
        String operandStr = operands.stream()
                .map(operand -> filterScope != null && !filterScope.isEmpty() 
                    ? generateOperandExpressionInLoop(operand, "date", filterScope, loopVar)
                    : generateOperandExpression(operand, "date"))
                .collect(Collectors.joining(", "));

        if ("withinLast3Months".equals(function)) {
            return "DateUtil.withinLast3Months(" + operandStr + ")";
        } else if ("withinLast6Months".equals(function)) {
            return "DateUtil.withinLast6Months(" + operandStr + ")";
        } else if ("withinLast9Months".equals(function)) {
            return "DateUtil.withinLast9Months(" + operandStr + ")";
        } else if ("withinLast12Months".equals(function)) {
            return "DateUtil.withinLast12Months(" + operandStr + ")";
        } else if ("months_between".equals(function)) {
            return "date.diff_months(" + operandStr + ")";
        } else if ("days_between".equals(function)) {
            return "date.diff_days(" + operandStr + ")";
        } else if ("years_between".equals(function)) {
            return "date.diff_years(" + operandStr + ")";
        } else if ("isBefore".equals(function)) {
            return "date.before(" + operandStr + ")";
        } else if ("isAfter".equals(function)) {
            return "date.after(" + operandStr + ")";
        } else if ("isEqual".equals(function)) {
            return "date.equal(" + operandStr + ")";
        } else if ("format".equals(function)) {
            return "DateFormatUtil.format(" + operandStr + ")";
        } else {
            return "";
        }
    }

    private String generateFilter(LogicStepDTO step, int stepNum) {
        String varName = step.getOutputVar() != null ? step.getOutputVar() : "step" + stepNum;

        if (step.getFilterItems() == null || step.getFilterItems().isEmpty()) {
            return "";
        }

        String condition = generateFilterCondition(step.getFilterItems());
        String filterScope = step.getFilterScope();

        if (filterScope == null || filterScope.isEmpty()) {
            return "";
        }

        return generateFilterWithLoop(step, stepNum, varName, condition, filterScope);
    }

    private String generateFilterWithLoop(LogicStepDTO step, int stepNum, String varName, String condition, String filterScope) {
        String scopeExpression = generateFieldExpression(filterScope);
        String loopVar = "item";
        String convertedCondition = generateFilterConditionInLoop(step.getFilterItems(), filterScope, loopVar);

        boolean hasFilterLogic = step.getFilterLogic() != null && !step.getFilterLogic().isEmpty();
        boolean hasReverseLogic = step.getReverseLogic() != null && !step.getReverseLogic().isEmpty();

        if (hasFilterLogic) {
            return generateFilterWithCondition(stepNum, varName, convertedCondition,
                    scopeExpression, loopVar, step.getFilterLogic(), true, false);
        } else if (hasReverseLogic) {
            return generateFilterWithCondition(stepNum, varName, convertedCondition,
                    scopeExpression, loopVar, step.getReverseLogic(), true, true);
        } else {
            return generateFilterWithCondition(stepNum, varName, convertedCondition,
                    scopeExpression, loopVar, null, false, false);
        }
    }

    private String generateFilterWithCondition(int stepNum, String varName,
                                               String condition, String scopeExpression,
                                               String loopVar, List<FilterLogicDTO> logicList,
                                               boolean useTempList, boolean reverseCondition) {
        StringBuilder sb = new StringBuilder();

        String listVarName;
        if (useTempList) {
            // 临时筛选结果列表：使用保留前缀内部变量名，避免与用户 outputVar 冲突
            listVarName = "__filter_step" + stepNum + (reverseCondition ? "_unmatched" : "_matched");
            sb.append("let ").append(listVarName).append(" = seq.list();\n");
        } else {
            // 无聚合逻辑时，筛选结果直接赋给步骤输出变量（outputVar 或默认 stepN）
            listVarName = varName;
            sb.append("let ").append(listVarName).append(" = seq.list();\n");
        }

        sb.append("for ").append(loopVar).append(" in ").append(scopeExpression).append(" {\n");
        if (reverseCondition) {
            sb.append("  if (!(").append(condition).append(")) {\n");
        } else {
            sb.append("  if (").append(condition).append(") {\n");
        }
        sb.append("    seq.add(").append(listVarName).append(", ").append(loopVar).append(");\n");
        sb.append("  }\n");
        sb.append("}\n");

        if (logicList != null && !logicList.isEmpty()) {
            String initValue = getStepInitValue(logicList);
            // 聚合结果赋给步骤输出变量（outputVar 或默认 stepN）
            sb.append("let ").append(varName).append(" = ").append(initValue).append(";\n");
            sb.append(generateFilterLogicWithListResults(stepNum, logicList, listVarName, varName));
        }

        return sb.toString();
    }

    /**
     * 生成多个筛选执行操作的表达式（支持链式多操作）
     * 执行流程：每个操作的输出作为下一个操作的输入
     * - 操作1: 输入 = 原始列表 输出 = 结果1
     * - 操作2: 输入 = 结果1 输出 = 结果2  
     * - 操作N: 输入 = 结果(N-1) 输出 = 赋值给步骤变量
     */
    private String generateFilterLogicWithListResults(int stepNum, List<FilterLogicDTO> logicList,
                                                       String listVar, String stepVarName) {
        StringBuilder sb = new StringBuilder();
        
        if (logicList == null || logicList.isEmpty()) {
            sb.append(stepVarName).append(" = 0;\n");
            return sb.toString();
        }

        //当前输入变量，初始为筛选后的列
        String currentInput = listVar;

        for (int i = 0; i < logicList.size(); i++) {
            FilterLogicDTO logic = logicList.get(i);

            // 链式聚合中，count/sum 返回数值，只能作为最后一个操作；
            // 若放在中间，下一步会把这个数值当作列表处理，生成运行时报错的表达式
            if (i < logicList.size() - 1
                    && ("count".equals(logic.getType()) || "sum".equals(logic.getType()))) {
                throw new IllegalArgumentException(
                        "筛选聚合链中 count/sum 必须是最后一个操作（第 " + (i + 1)
                                + " 个操作类型: " + logic.getType() + "）");
            }
            
            // 生成当前操作的表达式（使用当前输入变量）
            String resultExpr = generateFilterLogicExecutionWithList(logic, currentInput);
            
            if (i == logicList.size() - 1) {
                // 最后一个操作：结果直接赋值给步骤变量
                sb.append(stepVarName).append(" = ").append(resultExpr).append(";\n");
            } else {
                // 中间操作：结果保存到临时变量，作为下一步的输入
                String tempVar = "__filter_step" + stepNum + "_temp_" + i;
                sb.append("let ").append(tempVar).append(" = ").append(resultExpr).append(";\n");
                // 更新当前输入为临时变量
                currentInput = tempVar;
            }
        }

        return sb.toString();
    }

    /**
     * 根据执行操作类型获取步骤变量的初始     * 以最后一个操作的返回结果类型为准
     * count/sum 返回数值：0
     * distinct 返回数组：seq.list()
     */
    private String getStepInitValue(List<FilterLogicDTO> logicList) {
        if (logicList == null || logicList.isEmpty()) {
            return "0";
        }

        //使用最后一个操作的类型来决定初始
        FilterLogicDTO lastLogic = logicList.get(logicList.size() - 1);
        String type = lastLogic.getType();

        if ("distinct".equals(type)) {
            return "seq.list()";
        } else {
            //count、sum 都是数
            return "0";
        }
    }

    /**
     * 生成筛选执行操作的表达式结果（用于赋值给步骤变量     */
    private String generateFilterLogicWithListResult(List<FilterLogicDTO> logicList, String listVar) {
        if (logicList == null || logicList.isEmpty()) {
            return "0";
        }

        //取第一个执行操作的结果作为返回
        FilterLogicDTO firstLogic = logicList.get(0);
        return generateFilterLogicExecutionWithList(firstLogic, listVar);
    }

    private String convertConditionForLoop(String condition, String loopVar, String filterScope) {
        if (condition == null || condition.isEmpty()) {
            return condition;
        }

        // 使用更精确的正则，处理所JsonPathUtil.read 调用
        // 匹配模式: JsonPathUtil.read(inputData, '$.xxx')
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "JsonPathUtil\\.read\\(inputData, '\\$\\.([^']+)'\\)"
        );
        
        java.util.regex.Matcher matcher = pattern.matcher(condition);
        StringBuffer sb = new StringBuffer();
        
        while (matcher.find()) {
            String originalPath = matcher.group(1);  // 例如: PH010R01[0].PH010RA1
            String convertedPath;
            
            if (filterScope != null && !filterScope.isEmpty()) {
                // 去除 filterScope 前缀
                String prefixToRemove = filterScope + "\\[\\d+\\]\\.";
                if (originalPath.matches(prefixToRemove + ".*")) {
                    String relativePath = originalPath.replaceFirst("^" + prefixToRemove, "");
                    relativePath = relativePath.replaceAll("\\[(\\d+)\\]\\.", ".");
                    convertedPath = loopVar + "['" + relativePath + "']";
                } else {
                    // 不在筛选范围内，保持原样但转换item 引用
                    String cleanPath = originalPath.replaceAll("\\[(\\d+)\\]\\.", ".");
                    convertedPath = loopVar + "['" + cleanPath + "']";
                }
            } else {
                //没有 filterScope，直接转
                String cleanPath = originalPath.replaceAll("\\[(\\d+)\\]\\.", ".");
                convertedPath = loopVar + "['" + cleanPath + "']";
            }
            
            matcher.appendReplacement(sb, convertedPath);
        }
        matcher.appendTail(sb);
        
        return sb.toString();
    }

    private String generateFilterCondition(List<FilterItemDTO> items) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < items.size(); i++) {
            FilterItemDTO item = items.get(i);

            if (i > 0 && item.getLogicOperator() != null) {
                sb.append(" ").append(item.getLogicOperator().toUpperCase()).append(" ");
            }

            if ("group".equals(item.getType())) {
                sb.append("(").append(generateFilterCondition(item.getItems())).append(")");
            } else {
                String condition = generateSingleCondition(item);
                sb.append("(").append(condition).append(")");
            }
        }

        return sb.toString();
    }

    /**
     * 在筛选循环中生成筛选条     * 如果字段在筛选范围内，使item['字段]，否则使JsonPathUtil.read
     */
    private String generateFilterConditionInLoop(List<FilterItemDTO> items, String filterScope, String loopVar) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < items.size(); i++) {
            FilterItemDTO item = items.get(i);

            if (i > 0 && item.getLogicOperator() != null) {
                sb.append(" ").append(item.getLogicOperator().toUpperCase()).append(" ");
            }

            if ("group".equals(item.getType())) {
                sb.append("(").append(generateFilterConditionInLoop(item.getItems(), filterScope, loopVar)).append(")");
            } else {
                String condition = generateSingleConditionInLoop(item, filterScope, loopVar);
                sb.append("(").append(condition).append(")");
            }
        }

        return sb.toString();
    }

    private String generateSingleCondition(FilterItemDTO item) {
        String function = item.getFilterFunction();
        List<OperandDTO> operands = item.getOperands();

        if (operands == null || operands.isEmpty()) {
            // 无操作数：返回 false 作为中性条件，避免外层拼出 (()) 这类非法表达式
            return "false";
        }

        String category = getFirstElement(item.getFunctionCategory());

        if ("string".equals(category)) {
            return generateStringFunction(function, operands,null,null);
        } else if ("number".equals(category)) {
            return generateNumberFunction(function, operands,null,null);
        } else if ("date".equals(category)) {
            return generateDateFunction(function, operands,null,null);
        } else {
            return "";
        }
    }

    /**
     * 在筛选循环中生成单个条件
     * 如果字段在筛选范围内，使item['字段]，否则使JsonPathUtil.read
     */
    private String generateSingleConditionInLoop(FilterItemDTO item, String filterScope, String loopVar) {
        String function = item.getFilterFunction();
        List<OperandDTO> operands = item.getOperands();

        if (operands == null || operands.isEmpty()) {
            // 无操作数：返回 false 作为中性条件，避免外层拼出 (()) 这类非法表达式
            return "false";
        }

        String category = getFirstElement(item.getFunctionCategory());

        if ("string".equals(category)) {
            return generateStringFunction(function, operands, filterScope, loopVar);
        } else if ("number".equals(category)) {
            return generateNumberFunction(function, operands, filterScope, loopVar);
        } else if ("date".equals(category)) {
            return generateDateFunction(function, operands, filterScope, loopVar);
        } else {
            return "";
        }
    }

    private String generateFilterLogic(List<FilterLogicDTO> logicList, String logicType) {
        StringBuilder sb = new StringBuilder();

        for (FilterLogicDTO logic : logicList) {
            String execution = generateFilterLogicExecution(logic);
            sb.append(execution).append("\n");
        }

        return sb.toString();
    }

    private String generateFilterLogicWithList(List<FilterLogicDTO> logicList, String listVar) {
        StringBuilder sb = new StringBuilder();

        for (FilterLogicDTO logic : logicList) {
            String execution = generateFilterLogicExecutionWithList(logic, listVar);
            sb.append(execution).append("\n");
        }

        return sb.toString();
    }

    private String generateFilterLogicExecution(FilterLogicDTO logic) {
        String type = logic.getType();
        String value = logic.getValue();

        if ("count".equals(type)) {
            if ("all".equals(value)) {
                return "count(list)";
            } else {
                String fieldPath = generateFieldExpression(value);
                return "count(list, lambda(x) -> " + fieldPath + " != nil end)";
            }
        } else if ("sum".equals(type)) {
            String sumField = generateFieldExpression(value);
            return "reduce(list, 0, lambda(x, y) -> x + " + sumField + " end)";
        } else if ("distinct".equals(type)) {
            String distinctField = generateFieldExpression(value);
            return "distinct(seq.map(list, lambda(x) -> " + distinctField + " end))";
        } else {
            return "";
        }
    }

    private String generateFilterLogicExecutionWithList(FilterLogicDTO logic, String listVar) {
        String type = logic.getType();
        String value = logic.getValue();

        // 从完整路径中提取最终的字段        // 例如: "PH010R01[0].PH010RA1" "PH010RA1"
        // 例如: "amount" "amount"
        String fieldName = extractFieldName(value);

        if ("count".equals(type)) {
            if ("all".equals(value)) {
                return "count(" + listVar + ")";
            } else {
                // Aviator 5.x 的 count 只支持单参数，带条件计数需先 filter 再 count
                return "count(filter(" + listVar + ", lambda(x) -> x['" + fieldName + "'] != nil end))";
            }
        } else if ("sum".equals(type)) {
            // Aviator 5.x 的 reduce 签名是 reduce(seq, lambda, init)；
            // 且 reduce 的 lambda 内对元素做 x['field'] 取属性不可靠，先 map 成值再求和
            return "reduce(filter(map(" + listVar + ", lambda(x) -> x['" + fieldName + "'] end),"
                    + " lambda(x) -> x != nil end), lambda(x, y) -> x + y end, 0)";
        } else if ("distinct".equals(type)) {
            // Aviator 5.x 中 seq.map 返回的是 Map（seq -> lambda），映射列表应使用 map
            return "distinct(map(" + listVar + ", lambda(x) -> x['" + fieldName + "'] end))";
        } else {
            return "";
        }
    }

    /**
     * 从完整字段路径中提取最终的属性名
     * 处理各种格式的路径：
     * - "field" "field"
     * - "a.b.c" "c"
     * - "arr[0].field" "field"
     * - "arr[0].nested.field" "field"
     */
    private String extractFieldName(String fieldPath) {
        if (fieldPath == null || fieldPath.isEmpty()) {
            return "";
        }

        // 去除数组索引 [i]
        String cleaned = fieldPath.replaceAll("\\[\\d+\\]", "");

        //取最后一. 之后的部分（如果没有 . 则取全部
        int lastDot = cleaned.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < cleaned.length() - 1) {
            return cleaned.substring(lastDot + 1);
        }

        return cleaned;
    }

    private String generateCustomExpression(LogicStepDTO step, int stepNum) {
        String varName = step.getOutputVar() != null ? step.getOutputVar() : "step" + stepNum;
        String expr = step.getCustomExpression();

        expr = expr.replaceAll("input\\.", "");

        return "let " + varName + " = " + expr + ";";
    }

    private String generateOperandExpression(OperandDTO operand, String functionCategory) {
        String type = operand.getType();
        Object typeValue = operand.getTypeValue();

        if ("field".equals(type)) {
            return generateFieldExpression((String) typeValue);
        } else if ("value".equals(type)) {
            return formatValue(String.valueOf(typeValue), functionCategory);
        } else if ("step".equals(type)) {
            return "step" + (((Number) typeValue).intValue() + 1);
        } else if ("operator".equals(type)) {
            return String.valueOf(typeValue);
        } else {
            return "";
        }
    }

    /**
     * 在筛选循环中生成操作数表达式
     * 如果字段在筛选范围内，使item['字段]，否则使JsonPathUtil.read
     */
    private String generateOperandExpressionInLoop(OperandDTO operand, String functionCategory, String filterScope, String loopVar) {
        String type = operand.getType();
        Object typeValue = operand.getTypeValue();

        if ("field".equals(type)) {
            String field = (String) typeValue;
            if (field == null || field.isEmpty()) {
                return "nil";
            }

            // 去除 input. 前缀
            field = field.replaceAll("^input\\.", "");

            // 判断字段是否在筛选范围内
            if (filterScope != null && !filterScope.isEmpty()) {
                // 构建筛选范围前缀，例"PH010R01[0]."
                String scopePrefix = filterScope + "[";
                //检查字段是否以筛选范围开
                if (field.startsWith(scopePrefix) || field.startsWith(filterScope + ".")) {
                    //字段在筛选范围内，提取相对路
                    String relativePath;
                    if (field.startsWith(scopePrefix)) {
                        // 格式：PH010R01[0].PH010RA1 PH010R01[0].nested.field
                        relativePath = field.substring(field.indexOf(']') + 1);
                        if (relativePath.startsWith(".")) {
                            relativePath = relativePath.substring(1);
                        }
                    } else {
                        // 格式：PH010R01.field
                        relativePath = field.substring(filterScope.length());
                        if (relativePath.startsWith(".")) {
                            relativePath = relativePath.substring(1);
                        }
                    }
                    // 使用 item['字段]
                    return loopVar + "['" + relativePath + "']";
                } else {
                    // 字段不在筛选范围内，使JsonPathUtil.read
                    return "JsonPathUtil.read(inputData, '$." + field + "')";
                }
            } else {
                // 没有筛选范围，使用 JsonPathUtil.read
                return "JsonPathUtil.read(inputData, '$." + field + "')";
            }
        } else if ("value".equals(type)) {
            return formatValue(String.valueOf(typeValue), functionCategory);
        } else if ("step".equals(type)) {
            return "step" + (((Number) typeValue).intValue() + 1);
        } else if ("operator".equals(type)) {
            return String.valueOf(typeValue);
        } else {
            return "";
        }
    }

    private String generateFieldExpression(String field) {
        if (field == null || field.isEmpty()) {
            return "nil";
        }

        field = field.replaceAll("^input\\.", "");

        return "JsonPathUtil.read(inputData, '$." + field + "')";
    }

    private String formatValue(String value, String functionCategory) {
        if (value == null) {
            return "nil";
        }

        // 如果是数值处理分类，不加引号（直接返回原始值）
        if ("number".equals(functionCategory)) {
            return value;
        }

        // 字符串、日期分类或默认情况：加单引号，并转义单引号/反斜杠/控制字符，
        // 防止值里带引号或反斜杠时生成非法脚本（甚至注入代码）
        return "'" + escapeStringLiteral(value) + "'";
    }

    /**
     * 转义单引号字符串字面量：反斜杠、单引号、回车、换行、制表符。
     *
     * <p>关联：被 {@link #formatValue} 调用，用于 string/date 字面量。
     */
    private String escapeStringLiteral(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    private String getFirstElement(String[] array) {
        if (array != null && array.length > 0) {
            return array[0];
        }
        return "";
    }
}
