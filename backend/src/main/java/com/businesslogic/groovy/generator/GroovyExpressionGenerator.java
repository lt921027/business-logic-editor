package com.businesslogic.groovy.generator;

import com.businesslogic.dto.*;
import com.businesslogic.groovy.engine.GroovyExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Groovy 表达式生成器
 *
 * <p>将业务逻辑 DTO 转换为 Groovy 脚本。
 *
 * <p>自定义表达式语法到 Groovy 语法的转换约定：
 * <pre>
 *   let x = expr;                  → def x = expr
 *   for item in list { }           → for (item in list) { }
 *   if (c) { } elsif { } else { }  → if (c) { } else if { } else { }
 *   nil                            → null
 *   seq.list()                     → []
 *   seq.add(list, item)            → list << item
 *   count(list)                    → list.size()
 *   count(list, lambda(x)->x!=nil) → list.count { it != null }
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

    /** 脚本头发布时间格式化 */
    private static final DateTimeFormatter PUBLISH_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 匹配方法体中的默认值赋值：def result = ...; */
    private static final Pattern DEFAULT_VALUE_PATTERN =
            Pattern.compile("(?s)\\bdef\\s+result\\s*=\\s*(.*?);");

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
        StringBuilder stepsCode = new StringBuilder();
        String lastVarName = null;

        List<LogicStepDTO> steps = dto.getLogicSteps();

        for (int i = 0; i < steps.size(); i++) {
            LogicStepDTO step = steps.get(i);
            String stepExpression = generateStepExpression(step, i + 1);

            // 空步骤或未知 category 不生成任何变量：跳过，避免末尾 result 引用未定义的 stepN
            if (stepExpression == null || stepExpression.trim().isEmpty()) {
                continue;
            }

            stepsCode.append("        ").append(stepExpression.replace("\n", "\n        ")).append("\n");

            // 记录最后一个步骤的输出变量名（用于最后赋值给 result）
            lastVarName = step.getOutputVar() != null ? step.getOutputVar() : "step" + (i + 1);
        }

        String methodName = sanitizeMethodName(dto.getName());

        StringBuilder expression = new StringBuilder();
        expression.append("def ").append(methodName).append("() {\n");
        expression.append("    def result = ")
                .append(resolveDefaultValueExpression(dto.getDefaultValue(), dto.getReturnType()))
                .append(";\n");
        expression.append("    try {\n");
        expression.append(stepsCode);
        if (lastVarName != null) {
            expression.append("        result = ").append(lastVarName).append(";\n");
        }
        expression.append("    } catch (Exception e) {\n");
        expression.append("        // 任一中间步骤异常：保持默认值，直接返回\n");
        expression.append("        return result;\n");
        expression.append("    }\n");
        expression.append("    return result;\n");
        expression.append("}\n");
        // 当前执行器通过 script.run() 读取脚本顶层的 return 值，因此这里显式调用一次方法并返回其结果
        expression.append("return ").append(methodName).append("()\n");

        logger.debug("生成的 Groovy 脚本:\n{}", expression);
        return expression.toString();
    }

    /**
     * 将前端传入的裸默认值转换为 Groovy 表达式，并校验是否与返回值类型匹配。
     *
     * <p>默认值为空、返回值类型为空，或默认值与返回值类型不匹配时，直接抛
     * IllegalArgumentException。</p>
     *
     * @param defaultValue 裸默认值（如 -99999、0、abc、true、[]、[:]）
     * @param returnType   返回值类型（如 BigDecimal、Integer、String、Boolean、List、Date）
     * @return 与返回值类型匹配的 Groovy 表达式
     */
    private String resolveDefaultValueExpression(String defaultValue, String returnType) {
        if (defaultValue == null || defaultValue.trim().isEmpty()) {
            throw new IllegalArgumentException("默认值不能为空");
        }
        if (returnType == null || returnType.trim().isEmpty()) {
            throw new IllegalArgumentException("返回值类型不能为空");
        }

        String value = defaultValue.trim();
        String type = returnType.trim().toLowerCase();

        if (isNumericType(type)) {
            return buildNumericDefaultValue(value, type);
        }

        switch (type) {
            case "string":
                return "'" + escapeStringLiteral(value) + "'";
            case "boolean":
                if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                    return value.toLowerCase();
                }
                throw new IllegalArgumentException("默认值[" + value + "]与返回值类型[" + returnType + "]不匹配");
            case "list":
                if (value.startsWith("[") && value.endsWith("]")) {
                    return value;
                }
                throw new IllegalArgumentException("默认值[" + value + "]与返回值类型[" + returnType + "]不匹配");
            case "map":
                if ("[:]".equals(value) || (value.startsWith("[") && value.endsWith("]") && value.contains(":"))) {
                    return value;
                }
                throw new IllegalArgumentException("默认值[" + value + "]与返回值类型[" + returnType + "]不匹配");
            case "date":
            case "localdate":
            case "localdatetime":
                return "'" + escapeStringLiteral(value) + "'";
            default:
                return value;
        }
    }

    /**
     * 构建数值类型默认值表达式，并校验裸值是否为合法数值。
     */
    private String buildNumericDefaultValue(String value, String type) {
        String wrapper;
        boolean decimal;

        switch (type) {
            case "bigdecimal": wrapper = "BigDecimal"; decimal = true; break;
            case "double":      wrapper = "Double";     decimal = true; break;
            case "float":       wrapper = "Float";      decimal = true; break;
            case "integer":     wrapper = "Integer";    decimal = false; break;
            case "long":        wrapper = "Long";       decimal = false; break;
            case "short":       wrapper = "Short";      decimal = false; break;
            case "byte":        wrapper = "Byte";       decimal = false; break;
            default:            wrapper = "BigDecimal"; decimal = true; break;
        }

        boolean valid = decimal
                ? value.matches("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)")
                : value.matches("[+-]?\\d+");
        if (!valid) {
            throw new IllegalArgumentException("默认值[" + value + "]与返回值类型[" + type + "]不匹配");
        }
        return wrapper + ".valueOf(" + value + ")";
    }

    /**
     * 是否数值类型。
     */
    private boolean isNumericType(String type) {
        return "bigdecimal".equals(type)
                || "integer".equals(type)
                || "long".equals(type)
                || "short".equals(type)
                || "byte".equals(type)
                || "double".equals(type)
                || "float".equals(type);
    }

    /**
     * 将特征名称转换为合法的 Groovy 方法名。
     *
     * <p>仅保留字母、数字和下划线；首字符若不是字母或下划线则前缀 "_"；
     * 名称为空或清洗后为空时回退为 "feature"。
     */
    private String sanitizeMethodName(String name) {
        if (name == null) {
            return "feature";
        }
        String cleaned = name.replaceAll("[^A-Za-z0-9_]", "");
        if (cleaned.isEmpty()) {
            return "feature";
        }
        char first = cleaned.charAt(0);
        if (!Character.isLetter(first) && first != '_') {
            cleaned = "_" + cleaned;
        }
        return cleaned;
    }


    /**
     * 将多个特征的表达式合并为一个完整的交易码级源报文表达式，并在脚本头写入发布元数据。
     *
     * <p>脚本头包含：交易码、发布版本号、发布时间、特征数量；
     * 每个特征前标注特征名称、特征编码、特征数据类型、版本号、特征描述、
     * 默认值、返回值类型和该特征表达式源码的 MD5 hash，便于线上定位是哪次发布、哪个特征。</p>
     *
     * <p>默认值与返回值类型优先取 {@link FeatureConfigDTO} 上显式传入的值；
     * 未传时默认值从方法体中的 `def result = ...` 解析，返回值类型按默认值表达式推断。</p>
     *
     * <p>长度校验：合并过程中实时对照 {@link com.businesslogic.groovy.engine.GroovyExpressionEngine#getMaxScriptLength()}
     * （与引擎编译时的限制保持一致），超过限制直接抛 IllegalArgumentException，避免发布编译不过的脚本。</p>
     *
     * @param features 特征配置列表（{@link FeatureConfigDTO}），列表顺序即返回 Map 的字段顺序
     * @param meta     发布元数据（交易码/版本号/发布时间），可为 null，缺失字段在脚本头显示为 "-"
     * @return 可被 {@link com.businesslogic.groovy.engine.GroovyExpressionEngine#compile} 编译的交易码级 Groovy 源码
     */
    public String mergeFeatureExpressions(List<FeatureConfigDTO> features, MergeMeta meta) {
        if (features == null || features.isEmpty()) {
            return "return [:]";
        }

        // 与引擎编译时使用的限制保持一致：引擎配置变更（setMaxScriptLength）后，合并校验自动跟随
        int maxScriptLength = GroovyExecutor.getEngine().getMaxScriptLength();

        StringBuilder script = new StringBuilder();
        script.append(buildHeader(meta, features.size()));

        LinkedHashMap<String, String> methodNameByFeature = new LinkedHashMap<>();
        Set<String> usedMethodNames = new HashSet<>();

        for (FeatureConfigDTO feature : features) {
            String featureCode = feature.getFeatureCode();
            String expression = feature.getRunExpress();

            if (featureCode == null || featureCode.trim().isEmpty()) {
                throw new IllegalArgumentException("特征编码不能为空");
            }
            if (methodNameByFeature.containsKey(featureCode)) {
                throw new IllegalArgumentException("重复的特征编码: " + featureCode);
            }

            MethodBlock methodBlock = extractMethodBlock(expression);
            String methodName = methodBlock.getMethodName();
            if (!usedMethodNames.add(methodName)) {
                throw new IllegalArgumentException(
                        "特征方法名重复（请保证特征名称唯一）: " + methodName + "，特征编码: " + featureCode);
            }

            String defaultValue = feature.getFeatureDefaultValue() != null
                    ? feature.getFeatureDefaultValue()
                    : extractDefaultValue(methodBlock.getBody());
            String returnType = feature.getFeatureDataType() != null
                    ? feature.getFeatureDataType()
                    : inferReturnType(defaultValue);

            script.append("// ===== 特征: ").append(commentSafe(featureCode)).append(" =====\n");
            script.append("// 特征名称: ").append(commentSafe(feature.getFeatureName())).append("\n");
            script.append("// 特征数据类型: ").append(commentSafe(feature.getFeatureDataType())).append("\n");
            script.append("// 版本号: ").append(commentSafe(feature.getVersion())).append("\n");
            script.append("// 特征描述: ").append(commentSafe(feature.getFeatureLogicDesc())).append("\n");
            script.append(methodBlock.getBody()).append("\n\n");
            ensureWithinScriptLengthLimit(script.length(), maxScriptLength, "超限特征: " + featureCode);
            methodNameByFeature.put(featureCode, methodName);
        }

        script.append("return [\n");
        for (Map.Entry<String, String> entry : methodNameByFeature.entrySet()) {
            script.append("    '").append(escapeStringLiteral(entry.getKey()))
                    .append("': ").append(entry.getValue()).append("(),\n");
        }
        script.append("]\n");
        ensureWithinScriptLengthLimit(script.length(), maxScriptLength, null);

        logger.debug("生成的交易码级 Groovy 脚本:\n{}", script);
        return script.toString();
    }

    /**
     * 校验合并脚本长度不超过引擎编译限制。
     *
     * @param currentLength   当前脚本长度
     * @param maxScriptLength 引擎 maxScriptLength 限制
     * @param detail          附加的定位信息（如超限特征），可为 null
     */
    private void ensureWithinScriptLengthLimit(int currentLength, int maxScriptLength, String detail) {
        if (currentLength > maxScriptLength) {
            throw new IllegalArgumentException("合并后的交易码脚本长度超限（" + currentLength
                    + " > " + maxScriptLength + "，引擎 maxScriptLength 限制）"
                    + (detail != null ? "，" + detail : ""));
        }
    }

    /**
     * 生成脚本头部元数据注释块。
     */
    private String buildHeader(MergeMeta meta, int featureCount) {
        String txnCode = meta != null && meta.getTransactionCode() != null
                ? commentSafe(meta.getTransactionCode()) : "-";
        String version = meta != null && meta.getVersion() != null
                ? String.valueOf(meta.getVersion()) : "-";
        String publishTime = meta != null && meta.getPublishTime() != null
                ? meta.getPublishTime().format(PUBLISH_TIME_FORMATTER) : "-";

        return "// ============================================================\n"
                + "// 交易码: " + txnCode + "\n"
                + "// 发布版本号: " + version + "\n"
                + "// 发布时间: " + publishTime + "\n"
                + "// 特征数量: " + featureCount + "\n"
                + "// ============================================================\n";
    }

    /**
     * 计算特征表达式源码的 MD5（32 位小写十六进制）。
     */
    private String md5(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("计算特征源码 hash 失败", e);
        }
    }

    /**
     * 将值中的换行/制表符替换为空格，避免破坏注释行。
     */
    private String commentSafe(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "-";
        }
        return value.replace("\r", " ").replace("\n", " ").replace("\t", " ");
    }

    /**
     * 从方法体中解析默认值赋值表达式（def result = ...;），解析不到返回 "-"。
     */
    private String extractDefaultValue(String methodBody) {
        Matcher matcher = DEFAULT_VALUE_PATTERN.matcher(methodBody);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "-";
    }

    /**
     * 按默认值表达式推断返回值类型；推断不出时返回 "动态(def)"。
     * 业务侧若已知确切类型，可通过 {@link FeatureConfigDTO#getFeatureDataType()} 显式传入。
     */
    private String inferReturnType(String defaultValue) {
        if (defaultValue == null || defaultValue.trim().isEmpty() || "-".equals(defaultValue.trim())) {
            return "动态(def)";
        }
        String value = defaultValue.trim();
        if (value.startsWith("BigDecimal")) {
            return "BigDecimal";
        }
        if (value.startsWith("'") || value.startsWith("\"")) {
            return "String";
        }
        if ("true".equals(value) || "false".equals(value)) {
            return "Boolean";
        }
        if (value.startsWith("[")) {
            return "List";
        }
        return "动态(def)";
    }

    /**
     * 从特征表达式中提取方法定义块：从 def 方法名( 到方法体闭合 } 为止，丢弃其前后所有内容。
     *
     * <p>与之前“匹配末尾 return xxx() 再剥离”的方式相比，本方式不依赖表达式是否以
     * return 调用结尾：带分号、尾部注释、尾部杂项代码都会被整体丢弃；方法体按花括号配对提取，
     * 且扫描时会跳过行注释、块注释和字符串字面量（含三引号字符串），避免注释/字符串里的
     * 花括号或 def 干扰判断。</p>
     */
    private MethodBlock extractMethodBlock(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("特征表达式不能为空");
        }

        String code = expression;
        int n = code.length();
        String methodName = null;
        int defStart = -1;
        int braceDepth = 0;
        boolean bodyStarted = false;
        int bodyEnd = -1;

        char quote = 0;
        int quoteLen = 0;
        boolean lineComment = false;
        boolean blockComment = false;

        int i = 0;
        while (i < n) {
            char c = code.charAt(i);
            char next = i + 1 < n ? code.charAt(i + 1) : 0;

            if (lineComment) {
                if (c == '\n') {
                    lineComment = false;
                }
                i++;
                continue;
            }
            if (blockComment) {
                if (c == '*' && next == '/') {
                    blockComment = false;
                    i += 2;
                    continue;
                }
                i++;
                continue;
            }
            if (quote != 0) {
                if (quoteLen == 3) {
                    if (c == quote && next == quote && i + 2 < n && code.charAt(i + 2) == quote) {
                        quote = 0;
                        quoteLen = 0;
                        i += 3;
                        continue;
                    }
                    i++;
                    continue;
                }
                if (c == '\\') {
                    i += 2;
                    continue;
                }
                if (c == quote) {
                    quote = 0;
                    quoteLen = 0;
                }
                i++;
                continue;
            }
            if (c == '/' && next == '/') {
                lineComment = true;
                i += 2;
                continue;
            }
            if (c == '/' && next == '*') {
                blockComment = true;
                i += 2;
                continue;
            }
            if (c == '\'' || c == '"') {
                if (next == c && i + 2 < n && code.charAt(i + 2) == c) {
                    quote = c;
                    quoteLen = 3;
                    i += 3;
                    continue;
                }
                quote = c;
                quoteLen = 1;
                i++;
                continue;
            }

            if (bodyStarted) {
                if (c == '{') {
                    braceDepth++;
                } else if (c == '}') {
                    braceDepth--;
                    if (braceDepth == 0) {
                        bodyEnd = i;
                        break;
                    }
                }
                i++;
                continue;
            }

            if (methodName == null && isDefAt(code, i)) {
                int j = i + 3;
                while (j < n && Character.isWhitespace(code.charAt(j))) {
                    j++;
                }
                int nameStart = j;
                while (j < n && (Character.isLetterOrDigit(code.charAt(j)) || code.charAt(j) == '_')) {
                    j++;
                }
                if (j > nameStart) {
                    String candidate = code.substring(nameStart, j);
                    int k = j;
                    while (k < n && Character.isWhitespace(code.charAt(k))) {
                        k++;
                    }
                    if (k < n && code.charAt(k) == '(') {
                        methodName = candidate;
                        defStart = i;
                        i = k;
                        continue;
                    }
                }
            }

            if (defStart >= 0 && c == '{') {
                bodyStarted = true;
                braceDepth = 1;
            }
            i++;
        }

        if (methodName == null) {
            throw new IllegalArgumentException(
                    "特征表达式不是合法的 Groovy 方法定义，无法提取方法名: " + abbreviate(expression));
        }
        if (bodyEnd < 0) {
            throw new IllegalArgumentException("特征表达式方法体不完整（缺少闭合的 }）: " + methodName);
        }

        return new MethodBlock(methodName, code.substring(defStart, bodyEnd + 1).trim());
    }

    /**
     * 判断下标 i 处是否为代码级的 "def" 关键字（前一个字符不是标识符字符，后一个字符是空白）。
     */
    private boolean isDefAt(String code, int i) {
        if (i + 2 >= code.length()) {
            return false;
        }
        if (code.charAt(i) != 'd' || code.charAt(i + 1) != 'e' || code.charAt(i + 2) != 'f') {
            return false;
        }
        if (i > 0) {
            char prev = code.charAt(i - 1);
            if (Character.isLetterOrDigit(prev) || prev == '_' || prev == '$') {
                return false;
            }
        }
        return i + 3 < code.length() && Character.isWhitespace(code.charAt(i + 3));
    }

    /**
     * 方法定义块：方法名 + 从 def 到方法体闭合 } 的完整代码。
     */
    private static class MethodBlock {

        private final String methodName;
        private final String body;

        MethodBlock(String methodName, String body) {
            this.methodName = methodName;
            this.body = body;
        }

        String getMethodName() {
            return methodName;
        }

        String getBody() {
            return body;
        }
    }

    /**
     * 截断表达式用于错误提示，避免异常信息过长。
     */
    private String abbreviate(String text) {
        String compact = text.trim().replaceAll("\\s+", " ");
        return compact.length() > 80 ? compact.substring(0, 80) + "..." : compact;
    }

    /**
     * 单个特征及其 Groovy 表达式（特征编码 + 单特征方法脚本）。
     */
    public static class FeatureExpression {

        private final String featureCode;
        private final String expression;
        private final String defaultValue;
        private final String returnType;

        public FeatureExpression(String featureCode, String expression) {
            this(featureCode, expression, null, null);
        }

        public FeatureExpression(String featureCode, String expression,
                                 String defaultValue, String returnType) {
            this.featureCode = featureCode;
            this.expression = expression;
            this.defaultValue = defaultValue;
            this.returnType = returnType;
        }

        public String getFeatureCode() {
            return featureCode;
        }

        public String getExpression() {
            return expression;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        public String getReturnType() {
            return returnType;
        }
    }

    /**
     * 发布元数据：交易码 + 发布版本号 + 发布时间，用于写入合并脚本头。
     */
    public static class MergeMeta {

        private final String transactionCode;
        private final Long version;
        private final LocalDateTime publishTime;

        public MergeMeta(String transactionCode, Long version, LocalDateTime publishTime) {
            this.transactionCode = transactionCode;
            this.version = version;
            this.publishTime = publishTime;
        }

        public String getTransactionCode() {
            return transactionCode;
        }

        public Long getVersion() {
            return version;
        }

        public LocalDateTime getPublishTime() {
            return publishTime;
        }
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
        String category = step.getFunctionCategory();

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
     * <p>生成形式：`def step1 = JsonPathUtil.read(inputData, '$.field')`。
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
     * <p>多个子计算时每个加括号保证优先级，单个时不加括号。
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
            // 无操作数时返回该分类的中性兜底值，避免生成 `def x = ` 这类非法脚本
            String category = calcStep.getFunctionCategory();
            if ("string".equals(category)) {
                return "''";
            } else if ("date".equals(category)) {
                return "null";
            }
            return "0";
        }

        String category = calcStep.getFunctionCategory();

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
     * <p>支持的 function 到 Groovy 代码的映射：
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
        List<String> operandExprs = operands.stream()
                .map(operand -> filterScope != null && !filterScope.isEmpty()
                        ? generateOperandExpressionInLoop(operand, "string", filterScope, loopVar)
                        : generateOperandExpression(operand, "string"))
                .collect(Collectors.toList());

        if ("includes".equals(function)) {
            // string.contains(a, b) → a.contains(b)；不足 2 个操作数时生成 false，避免非法 .contains()
            if (operandExprs.size() < 2) {
                return "false";
            }
            return operandExprs.get(0) + ".contains(" + operandExprs.get(1) + ")";
        } else if ("concat".equals(function)) {
            // seq.concat(a, b) → (a + b)；支持任意多个操作数
            if (operandExprs.isEmpty()) {
                return "''";
            }
            if (operandExprs.size() == 1) {
                return operandExprs.get(0);
            }
            return "(" + String.join(" + ", operandExprs) + ")";
        } else if ("equals".equals(function)) {
            // StringUtil.equals(a, b) → same (Java static call)
            if (operandExprs.size() < 2) {
                return "false";
            }
            return "StringUtil.equals(" + String.join(", ", operandExprs) + ")";
        } else if ("length".equals(function)) {
            // string.length(s) → s.length()；无操作数时生成 0，避免数组越界/非法调用
            if (operandExprs.isEmpty()) {
                return "0";
            }
            return operandExprs.get(0) + ".length()";
        } else {
            return "";
        }
    }

    // ==================== 数值函数 ====================

    /**
     * 生成数值函数调用的 Groovy 代码。
     *
     * <p>支持的 function 到 Groovy 代码的映射：
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

        List<String> operandExprs = operands.stream()
                .map(operand -> filterScope != null && !filterScope.isEmpty()
                        ? generateOperandExpressionInLoop(operand, "number", filterScope, loopVar)
                        : generateOperandExpression(operand, "number"))
                .collect(Collectors.toList());

        if ("max".equals(function)) {
            // max(a, b, c) → Math.max(Math.max(a, b), c)；支持任意多个操作数
            return buildNaryMath("Math.max", operandExprs);
        } else if ("min".equals(function)) {
            return buildNaryMath("Math.min", operandExprs);
        } else if ("sum".equals(function)) {
            // reduce(list(a, b, c), 0, lambda(x, y) -> x + y end) → [a, b, c].inject(0) { x, y -> x + y }
            return "[" + String.join(", ", operandExprs) + "].inject(0) { x, y -> x + y }";
        } else if ("avg".equals(function)) {
            // reduce(...) / count(...) → [...].inject(0) { x, y -> x + y } / [...].size()
            return "[" + String.join(", ", operandExprs) + "].inject(0) { x, y -> x + y } / ["
                    + String.join(", ", operandExprs) + "].size()";
        } else {
            return "";
        }
    }

    /**
     * 生成 n 元 Math.max/min 表达式：Math.max(Math.max(a, b), c)。
     *
     * <p>为何需要：Java Math.max/min 只接受两个参数，操作数超过 2 个时
     * 直接写 Math.max(a, b, c) 会在运行时抛 MissingMethodException。
     * 1 个操作数时直接返回该操作数；0 个时返回 0（调用方通常已前置拦截空操作数）。
     */
    private String buildNaryMath(String method, List<String> operandExprs) {
        if (operandExprs == null || operandExprs.isEmpty()) {
            return "0";
        }
        String expr = operandExprs.get(0);
        for (int i = 1; i < operandExprs.size(); i++) {
            expr = method + "(" + expr + ", " + operandExprs.get(i) + ")";
        }
        return expr;
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
     * 再按 filterLogics/reverseLogics 对筛选结果做聚合（count/sum/distinct）。
     *
     * <p>生成形式：`for (item in list) { if (condition) result << item }; step1 = result.count { ... }`。
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
     * 根据是否有 filterLogics / reverseLogics 决定循环体的生成方式。
     *
     * <p>三种分支：
     * <ul>
     *   <li>有 filterLogics：正向筛选 + 聚合（useTempList=true, reverseCondition=false）</li>
     *   <li>有 reverseLogics：反向筛选 + 聚合（useTempList=true, reverseCondition=true）</li>
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

        boolean hasFilterLogics = step.getFilterLogics() != null && !step.getFilterLogics().isEmpty();
        boolean hasReverseLogics = step.getReverseLogics() != null && !step.getReverseLogics().isEmpty();

        if (hasFilterLogics) {
            return generateFilterWithCondition(stepNum, varName, convertedCondition,
                    scopeExpression, loopVar, step.getFilterLogics(), true, false);
        } else if (hasReverseLogics) {
            return generateFilterWithCondition(stepNum, varName, convertedCondition,
                    scopeExpression, loopVar, step.getReverseLogics(), true, true);
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
            // 临时筛选结果列表：使用保留前缀内部变量名，避免与用户 outputVar 冲突
            listVarName = "__filter_step" + stepNum + (reverseCondition ? "_unmatched" : "_matched");
            // let var = seq.list(); → def var = []
            sb.append("def ").append(listVarName).append(" = []\n");
        } else {
            // 无聚合逻辑时，筛选结果直接赋给步骤输出变量（outputVar 或默认 stepN）
            listVarName = varName;
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
            // 聚合结果赋给步骤输出变量（outputVar 或默认 stepN）
            sb.append("def ").append(varName).append(" = ").append(initValue).append("\n");
            sb.append(generateFilterLogicWithListResults(stepNum, logicList, listVarName, varName));
        }

        return sb.toString();
    }

    /**
     * 生成多个筛选执行操作的表达式（支持链式多操作）
     * 执行流程：
     * - 操作1: 输入 = 原始列表 输出 = 结果1
     * - 操作2: 输入 = 结果1 输出 = 结果2
     * - 操作N: 输入 = 结果(N-1) 输出 = 赋值给步骤变量
     */
    private String generateFilterLogicWithListResults(int stepNum, List<FilterLogicDTO> logicList,
                                                       String listVar, String stepVarName) {
        StringBuilder sb = new StringBuilder();

        if (logicList == null || logicList.isEmpty()) {
            sb.append(stepVarName).append(" = 0\n");
            return sb.toString();
        }

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

            String resultExpr = generateFilterLogicExecutionWithList(logic, currentInput);

            if (i == logicList.size() - 1) {
                // 最后一个操作：结果直接赋值给步骤变量
                sb.append(stepVarName).append(" = ").append(resultExpr).append("\n");
            } else {
                // 中间操作：结果保存到临时变量
                String tempVar = "__filter_step" + stepNum + "_temp_" + i;
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
        String value = logic.getTypeValue();

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
     * 生成自定义表达式步骤：用户直接编写的自定义表达式，需转为 Groovy 语法。
     *
     * <p>处理步骤：
     * <ol>
     *   <li>去除 `input.` 前缀（Groovy 中字段通过 JsonPathUtil.read 访问，无需 input 前缀）</li>
     *   <li>调用 {@link #convertAviatorSyntaxToGroovy} 做 nil→null 等旧式语法转换</li>
     * </ol>
     *
     * <p>关联：被 {@link #generateStepExpression} 在 category="custom" 时调用。
     */
    private String generateCustomExpression(LogicStepDTO step, int stepNum) {
        String varName = step.getOutputVar() != null ? step.getOutputVar() : "step" + stepNum;
        String expr = step.getCustomExpression();

        // 去除 input. 前缀
        expr = expr.replaceAll("input\\.", "");

        // 旧式自定义表达式语法转 Groovy 语法
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
     * <p>为何去除 `input.` 前缀：前端传入的字段路径可能以 `input.` 开头（旧式写法），
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
            // 无操作数：返回 false 作为中性条件，避免外层拼出 (()) 这类非法表达式
            return "false";
        }

        String category = item.getFunctionCategory();

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
            // 无操作数：返回 false 作为中性条件，避免外层拼出 (()) 这类非法表达式
            return "false";
        }

        String category = item.getFunctionCategory();

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
     * AND/OR → &&/||
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
     * 将自定义表达式中的旧式语法转为 Groovy 语法
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

}
