package com.businesslogic.groovy.generator;

import com.businesslogic.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Groovy 表达式生成器
 *
 * <p>对应 Aviator 的 AviatorExpressionGenerator。
 * 将业务逻辑 DTO 转换为 Groovy 脚本，业务逻辑与 Aviator 版本完全一致。
 *
 * <p>语法差异对照：
 * <pre>
 *   Aviator                          → Groovy
 *   let x = expr;                    → def x = expr
 *   for item in list { }             → for (item in list) { }
 *   if (c) { } elsif { } else { }    → if (c) { } else if { } else { }
 *   nil                              → null
 *   seq.list()                       → []
 *   seq.add(list, item)              → list << item
 *   count(list)                      → list.size()
 *   count(list, lambda(x)->x!=nil)   → list.count { it != null }
 *   reduce(list, 0, lambda(x,y)->..) → list.inject(0) { x, y -> .. }
 *   distinct(seq.map(list, lambda))  → list.collect { .. }.unique()
 *   string.contains(a, b)            → a.contains(b)
 *   string.length(s)                 → s.length()
 *   seq.concat(a, b)                 → a + b
 *   max(a, b)                        → Math.max(a, b)
 *   date.diff_months(a, b)           → GroovyDateFunctions.diffMonths(a, b)
 *   AND                              → &&
 *   OR                               → ||
 * </pre>
 *
 * <p>关联体系：
 * <ul>
 *   <li>被 {@link com.businesslogic.groovy.service.GroovyBusinessLogicService#save} /
 *       {@link com.businesslogic.groovy.service.GroovyBusinessLogicService#update} /
 *       {@link com.businesslogic.groovy.service.GroovyBusinessLogicService#generateExpression} 调用，
 *       在保存业务逻辑时生成 Groovy 脚本</li>
 *   <li>生成的脚本中引用 {@link com.businesslogic.groovy.util.GroovyDateFunctions}（日期函数）、
 *       {@link com.businesslogic.util.JsonPathUtil}（字段访问）、{@link com.businesslogic.util.StringUtil}（字符串工具）</li>
 *   <li>生成的脚本最终由 {@link com.businesslogic.groovy.engine.GroovyExecutor} 编译执行</li>
 * </ul>
 */
@Component
public class GroovyExpressionGenerator {

    private static final Logger logger = LoggerFactory.getLogger(GroovyExpressionGenerator.class);

    /**
     * 根据业务逻辑 DTO 生成 Groovy 脚本。
     *
     * <p>整体流程：遍历所有 LogicStep → 按 category 分发生成 → 末尾追加 return。
     *
     * <p>为何末尾必须 return：Groovy Script 默认返回最后一条表达式的值，但若最后一步是
     * `def x = ...` 则返回 null。显式 return 确保业务拿到最后一步的计算结果。
     *
     * <p>关联：被 {@link com.businesslogic.groovy.service.GroovyBusinessLogicService#save} 调用。
     *
     * @param dto 业务逻辑 DTO（含步骤列表）
     * @return 可被 {@link com.businesslogic.groovy.engine.GroovyExpressionEngine#compile} 编译的 Groovy 源码
     */
    public String generate(BusinessLogicSaveDTO dto) {
        StringBuilder expression = new StringBuilder();

        List<LogicStepDTO> steps = dto.getLogicSteps();
        String lastVarName = null;

        for (int i = 0; i < steps.size(); i++) {
            LogicStepDTO step = steps.get(i);
            String stepExpression = generateStepExpression(step, i + 1);
            expression.append(stepExpression).append("\n");

            // 记录最后一个步骤的输出变量名（用于末尾 return）
            lastVarName = step.getOutputVar() != null ? step.getOutputVar() : "step" + (i + 1);
        }

        // 添加 return 语句，确保返回最后一步的结果
        if (lastVarName != null) {
            expression.append("return ").append(lastVarName).append("\n");
        }

        logger.debug("生成的 Groovy 脚本:\n{}", expression);
        return expression.toString();
    }

    /**
     * 按 functionCategory 分发到具体的步骤生成方法。
     *
     * <p>关联：被 {@link #generate} 循环调用；根据 category 委托给
     * {@link #generateDirectMapping} / {@link #generateCalculation} /
     * {@link #generateFilter} / {@link #generateCustomExpression} 之一。
     *
     * @param step    单个逻辑步骤
     * @param stepNum 步骤序号（1-based，用于默认变量名 step1/step2...）
     * @return 该步骤的 Groovy 代码片段
     */
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

    // ==================== 直接映射 ====================

    /**
     * 生成直接映射步骤：将输入 JSON 的某字段直接赋值给变量。
     *
     * <p>对应 Aviator: `let step1 = input.field`。
     * Groovy 形式：`def step1 = JsonPathUtil.read(inputData, '$.field')`。
     *
     * <p>关联：字段访问由 {@link #generateFieldExpression} 生成（统一走 JsonPathUtil）。
     */
    private String generateDirectMapping(LogicStepDTO step, int stepNum) {
        String varName = step.getOutputVar() != null ? step.getOutputVar() : "step" + stepNum;
        String fieldPath = generateFieldExpression(step.getMappedField());
        return "def " + varName + " = " + fieldPath;
    }

    // ==================== 计算步骤 ====================

    /**
     * 生成计算步骤：支持多个子计算步骤通过 AND/OR 组合。
     *
     * <p>对应 Aviator: `let step1 = (子计算1) AND (子计算2)`。
     * 多个子计算时每个加括号保证优先级，单个时不加括号。
     *
     * <p>关联：委托 {@link #generateCalculationStepExpression} 生成单个子计算；
     * 委托 {@link #convertLogicOperator} 转换 AND/OR → &&/||。
     */
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
                sb.append(" ").append(convertLogicOperator(calcStep.getLogicOperator())).append(" ");
            }

            String stepExpr = generateCalculationStepExpression(calcStep);
            if (calcSteps.size() > 1) {
                sb.append("(").append(stepExpr).append(")");
            } else {
                sb.append(stepExpr);
            }
        }

        return "def " + varName + " = " + sb.toString();
    }

    /**
     * 生成单个子计算步骤的表达式：按 functionCategory 分发到 string/number/date 函数生成器。
     *
     * <p>关联：被 {@link #generateCalculation} 调用；
     * 委托给 {@link #generateStringFunction} / {@link #generateNumberFunction} / {@link #generateDateFunction}。
     */
    private String generateCalculationStepExpression(CalculationStepDTO calcStep) {
        String function = calcStep.getFilterFunction();
        List<OperandDTO> operands = calcStep.getOperands();

        if (operands == null || operands.isEmpty()) {
            return "";
        }

        String category = getFirstElement(calcStep.getFunctionCategory());

        if ("string".equals(category)) {
            return generateStringFunction(function, operands, null, null);
        } else if ("number".equals(category)) {
            return generateNumberFunction(function, operands, null, null);
        } else if ("date".equals(category)) {
            return generateDateFunction(function, operands, null, null);
        } else {
            return "";
        }
    }

    // ==================== 字符串函数 ====================

    /**
     * 生成字符串函数调用的 Groovy 代码。
     *
     * <p>支持的 function 与 Aviator→Groovy 映射：
     * <ul>
     *   <li>includes: string.contains(a, b) → a.contains(b)</li>
     *   <li>concat: seq.concat(a, b) → (a + b)</li>
     *   <li>equals: StringUtil.equals(a, b) → StringUtil.equals(a, b)（直接调用 Java 静态方法）</li>
     *   <li>length: string.length(s) → s.length()</li>
     * </ul>
     *
     * <p>关联：filterScope/loopVar 非空时表示在筛选循环内调用，
     * 委托 {@link #generateOperandExpressionInLoop} 生成循环内字段访问；
     * 否则委托 {@link #generateOperandExpression} 生成普通字段访问。
     * 被 {@link #generateCalculationStepExpression} / {@link #generateSingleCondition} 等调用。
     */
    private String generateStringFunction(String function, List<OperandDTO> operands,
                                           String filterScope, String loopVar) {
        String operandStr = operands.stream()
                .map(operand -> filterScope != null && !filterScope.isEmpty()
                        ? generateOperandExpressionInLoop(operand, "string", filterScope, loopVar)
                        : generateOperandExpression(operand, "string"))
                .collect(Collectors.joining(", "));

        if ("includes".equals(function)) {
            // string.contains(a, b) → a.contains(b)
            String[] parts = operandStr.split(", ", 2);
            if (parts.length == 2) {
                return parts[0] + ".contains(" + parts[1] + ")";
            }
            return parts[0] + ".contains()";
        } else if ("concat".equals(function)) {
            // seq.concat(a, b) → a + b
            String[] parts = operandStr.split(", ", 2);
            if (parts.length == 2) {
                return "(" + parts[0] + " + " + parts[1] + ")";
            }
            return operandStr;
        } else if ("equals".equals(function)) {
            // StringUtil.equals(a, b) → same (Java static call)
            return "StringUtil.equals(" + operandStr + ")";
        } else if ("length".equals(function)) {
            // string.length(s) → s.length()
            String[] parts = operandStr.split(", ", 2);
            return parts[0] + ".length()";
        } else {
            return "";
        }
    }

    // ==================== 数值函数 ====================

    /**
     * 生成数值函数调用的 Groovy 代码。
     *
     * <p>支持的 function 与 Aviator→Groovy 映射：
     * <ul>
     *   <li>arithmetic: 算术表达式（+,-,*,/,>,< 等），委托 {@link #generateArithmeticExpression}</li>
     *   <li>max: max(a, b) → Math.max(a, b)</li>
     *   <li>min: min(a, b) → Math.min(a, b)</li>
     *   <li>sum: reduce(list, 0, lambda(x,y)->x+y) → [a,b,c].inject(0) { x, y -> x + y }</li>
     *   <li>avg: sum / count → [a,b,c].inject(0){...} / [a,b,c].size()</li>
     * </ul>
     *
     * <p>关联：被 {@link #generateCalculationStepExpression} / {@link #generateSingleCondition} 调用。
     */
    private String generateNumberFunction(String function, List<OperandDTO> operands,
                                           String filterScope, String loopVar) {
        if ("arithmetic".equals(function)) {
            return generateArithmeticExpression(operands, filterScope, loopVar);
        }

        String operandStr = operands.stream()
                .map(operand -> filterScope != null && !filterScope.isEmpty()
                        ? generateOperandExpressionInLoop(operand, "number", filterScope, loopVar)
                        : generateOperandExpression(operand, "number"))
                .collect(Collectors.joining(", "));

        if ("max".equals(function)) {
            // max(a, b) → Math.max(a, b)
            return "Math.max(" + operandStr + ")";
        } else if ("min".equals(function)) {
            // min(a, b) → Math.min(a, b)
            return "Math.min(" + operandStr + ")";
        } else if ("sum".equals(function)) {
            // reduce(list(a, b, c), 0, lambda(x, y) -> x + y end) → [a, b, c].inject(0) { x, y -> x + y }
            return "[" + operandStr + "].inject(0) { x, y -> x + y }";
        } else if ("avg".equals(function)) {
            // reduce(...) / count(...) → [...].inject(0) { x, y -> x + y } / [...].size()
            return "[" + operandStr + "].inject(0) { x, y -> x + y } / [" + operandStr + "].size()";
        } else {
            return "";
        }
    }

    /**
     * 生成算术表达式：将操作数列表拼接为 `a + b * c` 形式。
     *
     * <p>为何需要单独处理：算术表达式中操作数与运算符交替出现（如 [field:a, operator:+, field:b]），
     * 不能简单 join。第一个操作数直接拼接，后续 operator 类型拼接运算符，其他类型拼接表达式。
     *
     * <p>关联：被 {@link #generateNumberFunction} 在 function="arithmetic" 时调用。
     */
    private String generateArithmeticExpression(List<OperandDTO> operands,
                                                  String filterScope, String loopVar) {
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
                String op = String.valueOf(operand.getTypeValue());
                sb.append(" ").append(convertLogicOperator(op)).append(" ");
            } else {
                sb.append(expr);
            }
        }

        return sb.toString();
    }

    // ==================== 日期函数 ====================

    /**
     * 生成日期函数调用的 Groovy 代码。
     *
     * <p>所有日期函数都映射到 {@link com.businesslogic.groovy.util.GroovyDateFunctions} 的静态方法，
     * 该类已在 {@link com.businesslogic.groovy.engine.GroovyExpressionEngine#execute} 中以 Class 形式注入到 Binding。
     *
     * <p>支持的 function：
     * <ul>
     *   <li>withinLast3/6/9/12Months → GroovyDateFunctions.withinLast3/6/9/12Months(arg)</li>
     *   <li>months_between → GroovyDateFunctions.diffMonths(a, b)</li>
     *   <li>days_between → GroovyDateFunctions.diffDays(a, b)</li>
     *   <li>years_between → GroovyDateFunctions.diffYears(a, b)</li>
     *   <li>isBefore/isAfter/isEqual → GroovyDateFunctions.before/after/equal</li>
     *   <li>format → GroovyDateFunctions.format(date)</li>
     * </ul>
     *
     * <p>关联：被 {@link #generateCalculationStepExpression} / {@link #generateSingleCondition} 调用。
     */
    private String generateDateFunction(String function, List<OperandDTO> operands,
                                         String filterScope, String loopVar) {
        String operandStr = operands.stream()
                .map(operand -> filterScope != null && !filterScope.isEmpty()
                        ? generateOperandExpressionInLoop(operand, "date", filterScope, loopVar)
                        : generateOperandExpression(operand, "date"))
                .collect(Collectors.joining(", "));

        if ("withinLast3Months".equals(function)) {
            return "GroovyDateFunctions.withinLast3Months(" + operandStr + ")";
        } else if ("withinLast6Months".equals(function)) {
            return "GroovyDateFunctions.withinLast6Months(" + operandStr + ")";
        } else if ("withinLast9Months".equals(function)) {
            return "GroovyDateFunctions.withinLast9Months(" + operandStr + ")";
        } else if ("withinLast12Months".equals(function)) {
            return "GroovyDateFunctions.withinLast12Months(" + operandStr + ")";
        } else if ("months_between".equals(function)) {
            // date.diff_months(a, b) → GroovyDateFunctions.diffMonths(a, b)
            return "GroovyDateFunctions.diffMonths(" + operandStr + ")";
        } else if ("days_between".equals(function)) {
            return "GroovyDateFunctions.diffDays(" + operandStr + ")";
        } else if ("years_between".equals(function)) {
            return "GroovyDateFunctions.diffYears(" + operandStr + ")";
        } else if ("isBefore".equals(function)) {
            return "GroovyDateFunctions.before(" + operandStr + ")";
        } else if ("isAfter".equals(function)) {
            return "GroovyDateFunctions.after(" + operandStr + ")";
        } else if ("isEqual".equals(function)) {
            return "GroovyDateFunctions.equal(" + operandStr + ")";
        } else if ("format".equals(function)) {
            return "GroovyDateFunctions.format(" + operandStr + ")";
        } else {
            return "";
        }
    }

    // ==================== 筛选步骤 ====================

    /**
     * 生成筛选步骤：遍历 filterScope 指定的列表，按 filterItems 条件筛选元素，
     * 再按 filterLogic/reverseLogic 对筛选结果做聚合（count/sum/distinct）。
     *
     * <p>对应 Aviator: `let step1 = count(filter(list, lambda(...)))`。
     * Groovy 形式：`for (item in list) { if (condition) result << item }; step1 = result.count { ... }`。
     *
     * <p>关联：委托 {@link #generateFilterCondition} 生成条件表达式；
     * 委托 {@link #generateFilterWithLoop} 生成循环体；
     * 最终由 {@link #generateFilterWithCondition} 拼装完整代码。
     */
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

    /**
     * 根据是否有 filterLogic / reverseLogic 决定循环体的生成方式。
     *
     * <p>三种分支：
     * <ul>
     *   <li>有 filterLogic：正向筛选 + 聚合（useTempList=true, reverseCondition=false）</li>
     *   <li>有 reverseLogic：反向筛选 + 聚合（useTempList=true, reverseCondition=true）</li>
     *   <li>两者都无：仅返回筛选后的列表，不聚合</li>
     * </ul>
     *
     * <p>关联：被 {@link #generateFilter} 调用；委托 {@link #generateFilterWithCondition}。
     */
    private String generateFilterWithLoop(LogicStepDTO step, int stepNum,
                                           String varName, String condition, String filterScope) {
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

    /**
     * 生成筛选循环体（Groovy 语法）
     */
    private String generateFilterWithCondition(int stepNum, String varName,
                                                String condition, String scopeExpression,
                                                String loopVar, List<FilterLogicDTO> logicList,
                                                boolean useTempList, boolean reverseCondition) {
        StringBuilder sb = new StringBuilder();

        String listVarName;
        if (useTempList) {
            listVarName = varName + (reverseCondition ? "false" : "true");
            // let var = seq.list(); → def var = []
            sb.append("def ").append(listVarName).append(" = []\n");
        } else {
            listVarName = "step" + stepNum;
            sb.append("def ").append(listVarName).append(" = []\n");
        }

        // for item in scope { → for (item in scope) {
        sb.append("for (").append(loopVar).append(" in ").append(scopeExpression).append(") {\n");
        if (reverseCondition) {
            sb.append("  if (!(").append(condition).append(")) {\n");
        } else {
            sb.append("  if (").append(condition).append(") {\n");
        }
        // seq.add(list, item); → list << item
        sb.append("    ").append(listVarName).append(" << ").append(loopVar).append("\n");
        sb.append("  }\n");
        sb.append("}\n");

        if (logicList != null && !logicList.isEmpty()) {
            String initValue = getStepInitValue(logicList);
            String stepVarName = "step" + stepNum;
            // let step = 0; → def step = 0
            // let step = seq.list(); → def step = []
            sb.append("def ").append(stepVarName).append(" = ").append(initValue).append("\n");
            sb.append(generateFilterLogicWithListResults(logicList, listVarName, stepVarName));
        }

        return sb.toString();
    }

    /**
     * 生成多个筛选执行操作的表达式（支持链式多操作）
     * 执行流程与 Aviator 版本一致：
     * - 操作1: 输入 = 原始列表 输出 = 结果1
     * - 操作2: 输入 = 结果1 输出 = 结果2
     * - 操作N: 输入 = 结果(N-1) 输出 = 赋值给步骤变量
     */
    private String generateFilterLogicWithListResults(List<FilterLogicDTO> logicList,
                                                       String listVar, String stepVarName) {
        StringBuilder sb = new StringBuilder();

        if (logicList == null || logicList.isEmpty()) {
            sb.append(stepVarName).append(" = 0\n");
            return sb.toString();
        }

        String currentInput = listVar;

        for (int i = 0; i < logicList.size(); i++) {
            FilterLogicDTO logic = logicList.get(i);

            String resultExpr = generateFilterLogicExecutionWithList(logic, currentInput);

            if (i == logicList.size() - 1) {
                // 最后一个操作：结果直接赋值给步骤变量
                sb.append(stepVarName).append(" = ").append(resultExpr).append("\n");
            } else {
                // 中间操作：结果保存到临时变量
                String tempVar = "temp_" + stepVarName + "_" + i;
                // let temp = result; → def temp = result
                sb.append("def ").append(tempVar).append(" = ").append(resultExpr).append("\n");
                currentInput = tempVar;
            }
        }

        return sb.toString();
    }

    /**
     * 根据执行操作类型获取步骤变量的初始值
     * count/sum 返回数值：0
     * distinct 返回数组：[]
     */
    private String getStepInitValue(List<FilterLogicDTO> logicList) {
        if (logicList == null || logicList.isEmpty()) {
            return "0";
        }

        FilterLogicDTO lastLogic = logicList.get(logicList.size() - 1);
        String type = lastLogic.getType();

        if ("distinct".equals(type)) {
            // seq.list() → []
            return "[]";
        } else {
            return "0";
        }
    }

    private String generateFilterLogicWithListResult(List<FilterLogicDTO> logicList, String listVar) {
        if (logicList == null || logicList.isEmpty()) {
            return "0";
        }

        FilterLogicDTO firstLogic = logicList.get(0);
        return generateFilterLogicExecutionWithList(firstLogic, listVar);
    }

    /**
     * 生成筛选执行操作的表达式结果（Groovy 语法）
     */
    private String generateFilterLogicExecutionWithList(FilterLogicDTO logic, String listVar) {
        String type = logic.getType();
        String value = logic.getValue();

        String fieldName = extractFieldName(value);

        if ("count".equals(type)) {
            if ("all".equals(value)) {
                // count(list) → list.size()
                return listVar + ".size()";
            } else {
                // count(list, lambda(x) -> x['field'] != nil end) → list.count { it['field'] != null }
                return listVar + ".count { it['" + fieldName + "'] != null }";
            }
        } else if ("sum".equals(type)) {
            // reduce(list, 0, lambda(x, y) -> x + y['field'] end) → list.inject(0) { x, y -> x + y['field'] }
            return listVar + ".inject(0) { x, y -> x + y['" + fieldName + "'] }";
        } else if ("distinct".equals(type)) {
            // distinct(seq.map(list, lambda(x) -> x['field'] end)) → list.collect { it['field'] }.unique()
            return listVar + ".collect { it['" + fieldName + "'] }.unique()";
        } else {
            return "";
        }
    }

    /**
     * 从完整字段路径中提取最终的属性名
     */
    private String extractFieldName(String fieldPath) {
        if (fieldPath == null || fieldPath.isEmpty()) {
            return "";
        }

        String cleaned = fieldPath.replaceAll("\\[\\d+\\]", "");

        int lastDot = cleaned.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < cleaned.length() - 1) {
            return cleaned.substring(lastDot + 1);
        }

        return cleaned;
    }

    // ==================== 自定义表达式 ====================

    /**
     * 生成自定义表达式步骤：用户直接编写的 Aviator 表达式，需转为 Groovy 语法。
     *
     * <p>处理步骤：
     * <ol>
     *   <li>去除 `input.` 前缀（Groovy 中字段通过 JsonPathUtil.read 访问，无需 input 前缀）</li>
     *   <li>调用 {@link #convertAviatorSyntaxToGroovy} 做 nil→null 等语法转换</li>
     * </ol>
     *
     * <p>关联：被 {@link #generateStepExpression} 在 category="custom" 时调用。
     */
    private String generateCustomExpression(LogicStepDTO step, int stepNum) {
        String varName = step.getOutputVar() != null ? step.getOutputVar() : "step" + stepNum;
        String expr = step.getCustomExpression();

        // 去除 input. 前缀
        expr = expr.replaceAll("input\\.", "");

        // Aviator 语法转 Groovy 语法
        expr = convertAviatorSyntaxToGroovy(expr);

        return "def " + varName + " = " + expr;
    }

    // ==================== 操作数表达式 ====================

    /**
     * 生成单个操作数的 Groovy 表达式（非循环上下文）。
     *
     * <p>按 operand.type 分发：
     * <ul>
     *   <li>field: 字段路径 → 委托 {@link #generateFieldExpression}（走 JsonPathUtil）</li>
     *   <li>value: 字面量 → 委托 {@link #formatValue}（按 functionCategory 决定是否加引号）</li>
     *   <li>step: 引用前序步骤结果 → step{N+1}（stepNum 是 0-based，需 +1）</li>
     *   <li>operator: 运算符 → 委托 {@link #convertLogicOperator}（AND→&&, OR→||）</li>
     * </ul>
     *
     * <p>关联：被 {@link #generateStringFunction} / {@link #generateNumberFunction} /
     * {@link #generateDateFunction} / {@link #generateArithmeticExpression} 等调用。
     */
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
            return convertLogicOperator(String.valueOf(typeValue));
        } else {
            return "";
        }
    }

    /**
     * 在筛选循环中生成操作数表达式
     */
    private String generateOperandExpressionInLoop(OperandDTO operand, String functionCategory,
                                                     String filterScope, String loopVar) {
        String type = operand.getType();
        Object typeValue = operand.getTypeValue();

        if ("field".equals(type)) {
            String field = (String) typeValue;
            if (field == null || field.isEmpty()) {
                return "null";
            }

            field = field.replaceAll("^input\\.", "");

            if (filterScope != null && !filterScope.isEmpty()) {
                String scopePrefix = filterScope + "[";
                if (field.startsWith(scopePrefix) || field.startsWith(filterScope + ".")) {
                    String relativePath;
                    if (field.startsWith(scopePrefix)) {
                        relativePath = field.substring(field.indexOf(']') + 1);
                        if (relativePath.startsWith(".")) {
                            relativePath = relativePath.substring(1);
                        }
                    } else {
                        relativePath = field.substring(filterScope.length());
                        if (relativePath.startsWith(".")) {
                            relativePath = relativePath.substring(1);
                        }
                    }
                    return loopVar + "['" + relativePath + "']";
                } else {
                    return "JsonPathUtil.read(inputData, '$." + field + "')";
                }
            } else {
                return "JsonPathUtil.read(inputData, '$." + field + "')";
            }
        } else if ("value".equals(type)) {
            return formatValue(String.valueOf(typeValue), functionCategory);
        } else if ("step".equals(type)) {
            return "step" + (((Number) typeValue).intValue() + 1);
        } else if ("operator".equals(type)) {
            return convertLogicOperator(String.valueOf(typeValue));
        } else {
            return "";
        }
    }

    // ==================== 字段表达式 ====================

    /**
     * 生成字段访问表达式：统一通过 JsonPathUtil.read 从 inputData 中读取。
     *
     * <p>为何走 JsonPathUtil 而非直接 map['field']：inputData 是 JSON 字符串而非 Map，
     * 需要先解析再取值；JsonPathUtil 封装了 JSON 解析 + JsonPath 查询，支持嵌套路径如 `user.address.city`。
     *
     * <p>为何去除 `input.` 前缀：前端传入的字段路径可能以 `input.` 开头（Aviator 风格），
     * Groovy 中统一用 `$.field` 的 JsonPath 语法，需先剥离 `input.`。
     *
     * <p>关联：被 {@link #generateDirectMapping} / {@link #generateOperandExpression} /
     * {@link #generateFilterWithLoop} 等所有需要字段访问的方法调用。
     */
    private String generateFieldExpression(String field) {
        if (field == null || field.isEmpty()) {
            return "null";
        }

        field = field.replaceAll("^input\\.", "");

        return "JsonPathUtil.read(inputData, '$." + field + "')";
    }

    // ==================== 值格式化 ====================

    /**
     * 格式化字面量值：根据 functionCategory 决定是否加引号。
     *
     * <p>为何区分 category：number 类型的字面量（如 100）不能加引号，否则 Groovy 会当作字符串处理，
     * 导致算术运算报错；string/date 类型的字面量必须加引号才能被识别为字符串。
     *
     * <p>关联：被 {@link #generateOperandExpression} / {@link #generateOperandExpressionInLoop} 调用。
     */
    private String formatValue(String value, String functionCategory) {
        if (value == null) {
            return "null";
        }

        // 数值处理分类：不加引号
        if ("number".equals(functionCategory)) {
            return value;
        }

        // 字符串、日期分类或默认情况：加单引号
        return "'" + value + "'";
    }

    // ==================== 筛选条件 ====================

    /**
     * 生成筛选条件表达式（非循环上下文）：递归处理 group 类型，拼接 AND/OR。
     *
     * <p>关联：被 {@link #generateFilter} 调用；
     * 委托 {@link #generateSingleCondition} 生成单个条件；
     * group 类型递归调用自身。
     */
    private String generateFilterCondition(List<FilterItemDTO> items) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < items.size(); i++) {
            FilterItemDTO item = items.get(i);

            if (i > 0 && item.getLogicOperator() != null) {
                // AND → &&, OR → ||
                sb.append(" ").append(convertLogicOperator(item.getLogicOperator().toUpperCase())).append(" ");
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
     * 生成筛选条件表达式（循环上下文）：字段访问改为 loopVar['field'] 形式。
     *
     * <p>为何需要循环版本：在 for 循环内，字段应从 loopVar（当前迭代元素）取值而非 inputData，
     * 否则筛选条件永远作用于整个列表而非单个元素。
     *
     * <p>关联：被 {@link #generateFilterWithLoop} 调用；
     * 委托 {@link #generateSingleConditionInLoop} 生成单个条件；
     * 与 {@link #generateFilterCondition} 结构对称，仅字段访问方式不同。
     */
    private String generateFilterConditionInLoop(List<FilterItemDTO> items,
                                                  String filterScope, String loopVar) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < items.size(); i++) {
            FilterItemDTO item = items.get(i);

            if (i > 0 && item.getLogicOperator() != null) {
                sb.append(" ").append(convertLogicOperator(item.getLogicOperator().toUpperCase())).append(" ");
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

    /**
     * 生成单个筛选条件（非循环上下文）：按 functionCategory 分发到 string/number/date 函数。
     *
     * <p>关联：被 {@link #generateFilterCondition} 调用；
     * 委托 {@link #generateStringFunction} / {@link #generateNumberFunction} / {@link #generateDateFunction}，
     * 传 filterScope=null, loopVar=null（非循环上下文）。
     */
    private String generateSingleCondition(FilterItemDTO item) {
        String function = item.getFilterFunction();
        List<OperandDTO> operands = item.getOperands();

        if (operands == null || operands.isEmpty()) {
            return "";
        }

        String category = getFirstElement(item.getFunctionCategory());

        if ("string".equals(category)) {
            return generateStringFunction(function, operands, null, null);
        } else if ("number".equals(category)) {
            return generateNumberFunction(function, operands, null, null);
        } else if ("date".equals(category)) {
            return generateDateFunction(function, operands, null, null);
        } else {
            return "";
        }
    }

    /**
     * 生成单个筛选条件（循环上下文）：与 {@link #generateSingleCondition} 对称，
     * 但传递 filterScope/loopVar 给函数生成器，使字段访问走 loopVar['field'] 路径。
     *
     * <p>关联：被 {@link #generateFilterConditionInLoop} 调用。
     */
    private String generateSingleConditionInLoop(FilterItemDTO item,
                                                  String filterScope, String loopVar) {
        String function = item.getFilterFunction();
        List<OperandDTO> operands = item.getOperands();

        if (operands == null || operands.isEmpty()) {
            return "";
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

    // ==================== 辅助方法 ====================

    /**
     * 转换逻辑运算符
     * Aviator: AND/OR → Groovy: &&/||
     * 其他运算符（+, -, *, /, >, <, ==, != 等）保持不变
     */
    private String convertLogicOperator(String op) {
        if (op == null) {
            return "";
        }
        String trimmed = op.trim().toUpperCase();
        if ("AND".equals(trimmed)) {
            return "&&";
        } else if ("OR".equals(trimmed)) {
            return "||";
        }
        return op;
    }

    /**
     * 将自定义表达式中的 Aviator 语法转为 Groovy 语法
     * 处理 nil → null, let → def 等
     */
    private String convertAviatorSyntaxToGroovy(String expr) {
        if (expr == null || expr.isEmpty()) {
            return expr;
        }
        // nil → null
        expr = expr.replaceAll("\\bnil\\b", "null");
        return expr;
    }

    /**
     * 安全获取数组首元素。
     *
     * <p>为何需要：DTO 中 functionCategory 是 String[]，但业务实际只用第一个元素作为分类标识。
     * 此方法封装 null 检查，避免 NPE。
     *
     * <p>关联：被 {@link #generateStepExpression} / {@link #generateCalculationStepExpression} /
     * {@link #generateSingleCondition} 等调用。
     */
    private String getFirstElement(String[] array) {
        if (array != null && array.length > 0) {
            return array[0];
        }
        return "";
    }
}
