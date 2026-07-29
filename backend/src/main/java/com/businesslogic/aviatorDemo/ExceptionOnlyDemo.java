package com.businesslogic.aviatorDemo;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.Expression;
import com.businesslogic.util.JsonPathUtil;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExceptionOnlyDemo {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println(" Aviator 异常定位 Exception 解析方案                      ");
        System.out.println(" 无需注入标记 | 仅解析异常消| Java StackTrace            ");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

       /* demo1_typeMismatch();
        demo2_divideByZero();
        demo3_nullPointer();
        demo4_undefinedVar();*/
        demo5_deepError();
    }

    static void demo1_typeMismatch() {
        printHeader("场景1", "类型不匹String × Double");

        String expr = joinLines(
            "let step1 = JsonPathUtil.read(inputData, '$.order.amount');",
            "let step2 = step1 * taxRate;",
            "let step3 = step2 + 10;"
        );

        Map<String, Object> env = new HashMap<>();
        env.put("inputData", "{\"order\":{\"amount\":\"不是数字\"}}");
        env.put("taxRate", 0.1);

        runAndReport(expr, env);
    }

    static void demo2_divideByZero() {
        printHeader("场景2", "除零错误 total / count (count=0)");

        String expr = joinLines(
            "let step1 = JsonPathUtil.read(inputData, '$.total');",
            "let step2 = JsonPathUtil.read(inputData, '$.count');",
            "let step3 = step1 / step2;",
            "let step4 = math.round(step3);"
        );

        Map<String, Object> env = new HashMap<>();
        env.put("inputData", "{\"total\":100,\"count\":0}");

        runAndReport(expr, env);
    }

    static void demo3_nullPointer() {
        printHeader("场景3", "空指string.contains(null, 'active')");

        String expr = joinLines(
            "let step1 = JsonPathUtil.read(inputData, '$.items');",
            "let step2true = seq.list();",
            "for item in step1 {",
            "  if (string.contains(item['status'], 'active')) {",
            "    seq.add(step2true, item);",
            "  }",
            "}",
            "let step2 = count(step2true);"
        );

        Map<String, Object> env = new HashMap<>();
        env.put("inputData", "{\"items\":[{\"name\":\"A\",\"status\":null},{\"name\":\"B\",\"status\":\"active\"}]}");

        runAndReport(expr, env);
    }

    static void demo4_undefinedVar() {
        printHeader("场景4", "未定义变value + undefinedVar");

        String expr = joinLines(
            "let step1 = JsonPathUtil.read(inputData, '$.value');",
            "let step2 = step1 + undefinedVar;",
            "let step3 = step2 * 2;"
        );

        Map<String, Object> env = new HashMap<>();
        env.put("inputData", "{\"value\":42}");

        runAndReport(expr, env);
    }

    static void demo5_deepError() {
        printHeader("场景5", "深部步骤出错 Double×String 炸裂");

        String expr = joinLines(
            "let step1 = JsonPathUtil.read(inputData, '$.user.level');",
            "let step2 = JsonPathUtil.read(inputData, '$.score');",
            "let step3 = step2 * rate;",
            "let step4t=seq.list(); for o in JsonPathUtil.read(inputData,'$.orders') { seq.add(step4t,o); } let step4=count(step4t);",
            "let step5 = step3 / step4;",
            "let step6 = string.contains(step1,'GOLD') ? 50 : 0;",
            "let step7 = step5 + step6;",
            "let step8 = JsonPathUtil.read(inputData, '$.badField');",
            "let step9 = step7 * step8;"
        );

        Map<String, Object> env = new HashMap<>();
        env.put("inputData", "{\"user\":{\"level\":\"GOLD\"},\"score\":800,\"orders\":[{\"amt\":200},{\"amt\":300}],\"badField\":\"NOT_A_NUMBER\"}");
        env.put("rate", 1.5);

        runAndReport(expr, env);
    }


    // ══════════════════════════════════════════════════════    //  核心：执+ 异常解析引擎
    // ══════════════════════════════════════════════════════
    static void runAndReport(String expression, Map<String, Object> env) {
        List<String> lines = Arrays.asList(expression.split("\n"));

        env.put("JsonPathUtil", JsonPathUtil.class);

        try {
            Expression compiled = AviatorEvaluator.compile(expression);
            compiled.execute(env);
            System.out.println("  执行成功（未触发异常）");
        } catch (Exception e) {
            printExceptionReport(e, lines, env);
        }
        System.out.println();
    }

    static void printExceptionReport(Exception e, List<String> lines, Map<String, Object> env) {
        ParsedException parsed = parseException(e);

        int errorLine = locateErrorLine(parsed, lines);

        System.out.println("┌───────────────────────────────────────────────────────────────");
        System.out.printf(" %-56sn", parsed.exceptionClass + ": " + truncate(parsed.rawMessage, 50));
        System.out.println("├───────────────────────────────────────────────────────────────");

        if (errorLine > 0 && errorLine <= lines.size()) {
            System.out.printf(" at BusinessLogic#expr:%-40d n", errorLine);
            System.out.println("├───────────────────────────────────────────────────────────────");
            System.out.println(" 源代                                                  ");

            int start = Math.max(1, errorLine - 2);
            int end = Math.min(lines.size(), errorLine + 2);

            for (int i = start; i <= end; i++) {
                String line = lines.get(i - 1);
                boolean isTarget = (i == errorLine);
                String marker = isTarget ? ">>" : "  ";
                System.out.printf("%s %2d | %-46s n", marker, i, truncate(line, 44));

                if (isTarget && parsed.operatorHint != null) {
                    System.out.printf("    %s%sn", spaces(parsed.hintPos), "^-- " + parsed.operatorHint);
                }
            }
        } else {
            System.out.println(" at (无法精确定位行号)                         ");
        }

        System.out.println("├───────────────────────────────────────────────────────────────");
        System.out.println(" 变量快照:                                                 ");  

        if (!parsed.involvedVariables.isEmpty()) {
            for (String varName : parsed.involvedVariables) {
                Object val = env.get(varName);
                String valStr = formatValue(val);
                String typeStr = val != null ? shortenType(val.getClass().getName()) : "null";
                boolean isCulprit = parsed.culpritVariable != null && parsed.culpritVariable.equals(varName);
                System.out.printf("   %-10s = %-18s (%-10s) %sn",
                    varName, truncate(valStr, 16), typeStr,
                    isCulprit ? "问题根源" : "");
            }
        } else {
            System.out.println("   (从异常消息中无法提取变量信息)                           ");
        }

        if (parsed.rootCause != null) {
            System.out.println("├───────────────────────────────────────────────────────────────");
            System.out.println(" 根因分析:                                                 ");
            for (String rc : parsed.rootCause.split("\n")) {
                System.out.printf("   %-54sn", rc);
            }
        }

        System.out.println("└───────────────────────────────────────────────────────────────");
    }


    // ══════════════════════════════════════════════════════    //  核心：Exception 解析    // ══════════════════════════════════════════════════════
    static class ParsedException {
        String exceptionClass;
        String rawMessage;
        String operator;
        String leftType;
        String leftValue;
        String rightType;
        String rightValue;
        List<String> involvedVariables = new ArrayList<>();
        String culpritVariable;
        String operatorHint;
        int hintPos = 20;
        String rootCause;
    }

    static ParsedException parseException(Exception e) {
        ParsedException p = new ParsedException();
        p.exceptionClass = e.getClass().getSimpleName();
        p.rawMessage = e.getMessage();

        String msg = p.rawMessage;

        if (msg == null || msg.contains("NullPointerException") || msg.toLowerCase().contains("null")) {
            p.operatorHint = "空指 某个变量值为 null";
            p.rootCause = "可能原因:\n  1. JsonPath 读取了不存在的字段，返回 null\n  2. 变量未被正确赋值\n  3. 方法调用传入null 参数";
            return p;
        }

        Pattern arithP = Pattern.compile(
            "Could not (mult|add|sub|div|mod)\\s*<([^,>]+),\\s*([^>]*)>\\s+with\\s*<([^,>]+),\\s*([^>]+)>"
        );
        Matcher am = arithP.matcher(msg);
        if (am.find()) {
            p.operator = am.group(1);
            p.leftType = cleanType(am.group(2));
            p.leftValue = am.group(3).trim();
            p.rightType = cleanType(am.group(4));
            p.rightValue = am.group(5).trim();

            p.involvedVariables.addAll(extractVars(p.leftValue));
            p.involvedVariables.addAll(extractVars(p.rightValue));

            p.culpritVariable = findCulprit(p.leftType, p.rightType, p.leftValue, p.rightValue, p.involvedVariables);

            String opSymbol = opToSymbol(p.operator);
            p.operatorHint = String.format("类型不匹 %s(%s) %s %s(%s)",
                p.leftType, truncate(p.leftValue, 15), opSymbol, p.rightType, truncate(p.rightValue, 15));
            p.rootCause = buildArithmeticRootCause(p);

            return p;
        }

        Pattern undefP = Pattern.compile("variable[\\s\"]+(\\w+)\\s+(not defined|undefined|未定", Pattern.CASE_INSENSITIVE);
        Matcher um = undefP.matcher(msg);
        if (um.find()) {
            String varName = um.group(1);
            p.involvedVariables.add(varName);
            p.culpritVariable = varName;
            p.operatorHint = "未定义变 " + varName;
            p.rootCause = "可能原因:\n  1. 变量名拼写错误\n  2. 变量未在使用前定(let 声明)\n  3. 该变量属于其他作用域";
            return p;
        }

        Pattern idxP = Pattern.compile("(index|下标|IndexOutOfBounds|ArrayIndex)");
        Matcher im = idxP.matcher(msg);
        if (im.find()) {
            p.operatorHint = "数组/集合索引越界";
            p.rootCause = "可能原因:\n  1. 对非数组类型使用了下标访(Map[0])\n  2. 索引值超出数组长度范围\n  3. 集合为空时尝试访问元";
            return p;
        }

        Pattern methodP = Pattern.compile("Cannot find function method[\\s'\"](\\w+)");
        Matcher mm = methodP.matcher(msg);
        if (mm.find()) {
            p.operatorHint = "方法不存在或参数类型错误: " + mm.group(1);
            p.rootCause = "可能原因:\n  1. 方法名拼写错误\n  2. 调用对象类型不支持该方法 (如对数字调用字符串方\n  3. 参数数量或类型不匹配";
            return p;
        }

        p.operatorHint = "执行异常 (详见上方原始消息)";
        p.rootCause = null;
        return p;
    }

    static int locateErrorLine(ParsedException parsed, List<String> lines) {
        if (parsed.involvedVariables.isEmpty()) {
            return guessLineFromMessage(parsed.rawMessage, lines);
        }

        String primaryVar = parsed.culpritVariable != null ? parsed.culpritVariable : parsed.involvedVariables.get(0);

        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i).trim();
            if (line.contains(primaryVar) && (line.contains("=") || line.contains("+") || line.contains("-") || line.contains("*") || line.contains("/"))) {
                return i + 1;
            }
        }

        for (String var : parsed.involvedVariables) {
            for (int i = lines.size() - 1; i >= 0; i--) {
                if (lines.get(i).contains(var)) {
                    return i + 1;
                }
            }
        }

        return guessLineFromMessage(parsed.rawMessage, lines);
    }

    static int guessLineFromMessage(String msg, List<String> lines) {
        if (msg == null) return lines.size();

        List<String> keywords = Arrays.asList("contains(", ".read(", "JsonPathUtil", "string.", "seq.");

        for (String kw : keywords) {
            if (msg.contains(kw.replace("(", "")) || msg.contains(kw)) {
                for (int i = 0; i < lines.size(); i++) {
                    if (lines.get(i).contains(kw.replace("(", ""))) {
                        return i + 1;
                    }
                }
            }
        }

        return lines.size();
    }


    // ══════════════════════════════════════════════════════    //  工具方法
    // ══════════════════════════════════════════════════════
    static String cleanType(String type) {
        if (type == null) return "Unknown";
        type = type.trim();
        if (type.startsWith("java.lang.")) type = type.substring(10);
        if (type.equals("Long") || type.equals("Integer") || type.equals("Double") || type.equals("BigDecimal")) return type;
        if (type.equals("String")) return type;
        if (type.length() > 20) return type.substring(0, 20);
        return type;
    }

    static List<String> extractVars(String value) {
        List<String> vars = new ArrayList<>();
        if (value == null) return vars;

        Pattern varP = Pattern.compile("\\b([a-zA-Z_]\\w*)\\b");
        Matcher m = varP.matcher(value);
        while (m.find()) {
            String v = m.group(1);
            if (!isKeyword(v) && !v.matches("\\d+")) {
                vars.add(v);
            }
        }
        return vars;
    }

    static boolean isKeyword(String v) {
        Set<String> kw = new HashSet<>(Arrays.asList(
            "null", "true", "false", "let", "if", "else", "for", "in", "return",
            "def", "fn", "while", "do", "break", "continue", "new", "try", "catch"
        ));
        return kw.contains(v);
    }

    static String findCulprit(String leftT, String rightT, String leftV, String rightV, List<String> vars) {
        boolean leftSuspicious = leftT.equals("JavaType") || leftT.equals("String") || leftT.contains("unknown");
        boolean rightSuspicious = rightT.equals("JavaType") || rightT.equals("String") || rightT.contains("unknown");

        if (rightSuspicious && !vars.isEmpty()) {
            for (int i = vars.size() - 1; i >= 0; i--) {
                if (rightV.contains(vars.get(i))) return vars.get(i);
            }
        }
        if (leftSuspicious && !vars.isEmpty()) {
            for (int i = vars.size() - 1; i >= 0; i--) {
                if (leftV.contains(vars.get(i))) return vars.get(i);
            }
        }
        return vars.isEmpty() ? null : vars.get(vars.size() - 1);
    }

    static String opToSymbol(String op) {
        switch (op) {
            case "mult": return "*";
            case "add": return "+";
            case "sub": return "-";
            case "div": return "/";
            case "mod": return "%";
            default: return op;
        }
    }

    static String buildArithmeticRootCause(ParsedException p) {
        StringBuilder sb = new StringBuilder();
        sb.append("算术运算失败:\n");
        sb.append(String.format("  操作 %s%n", opToSymbol(p.operator)));
        sb.append(String.format("  左操作数: type=%s, value=%s%n", p.leftType, truncate(p.leftValue, 30)));
        sb.append(String.format("  右操作数: type=%s, value=%s%n", p.rightType, truncate(p.rightValue, 30)));
        sb.append("\n常见原因:\n");

        if ("JavaType".equals(p.leftType) || "JavaType".equals(p.rightType) ||
            "String".equals(p.leftType) || "String".equals(p.rightType)) {
            sb.append("  1. 类型不匹 期望数字但得到了字符串或其他类型\n");
            sb.append("     检JsonPath 返回值是否为预期类型\n");
            sb.append("     可能需要使number() string() 进行类型转换\n");
        }

        if (p.rightValue != null && (p.rightValue.contains("null") || p.rightValue.equals("null"))) {
            sb.append("  2. 空值参与运 某个操作数为 null\n");
            sb.append("     检查字段是否存在，JsonPath 不存在的字段返回 null\n");
        }

        return sb.toString();
    }

    static String formatValue(Object val) {
        if (val == null) return "null";
        if (val instanceof String) return "\"" + val + "\"";
        if (val instanceof Collection) return "[" + ((Collection<?>) val).size() + " elements]";
        if (val instanceof Map) return "{Map...}";
        if (val.toString().length() > 50) return val.toString().substring(0, 47) + "...";
        return val.toString();
    }

    static String shortenType(String fullType) {
        if (fullType == null) return "null";
        if (fullType.startsWith("java.lang.")) return fullType.substring(10);
        if (fullType.startsWith("com.")) {
            int lastDot = fullType.lastIndexOf('.');
            return lastDot > 0 ? fullType.substring(lastDot + 1) : fullType;
        }
        if (fullType.length() > 18) return fullType.substring(0, 15) + "...";
        return fullType;
    }

    static String truncate(String s, int maxLen) {
        if (s == null) return "(null)";
        return s.length() > maxLen ? s.substring(0, maxLen - 3) + "..." : s;
    }

    static String spaces(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(' ');
        return sb.toString();
    }

    static String joinLines(String... lines) {
        return String.join("\n", lines);
    }

    static void printHeader(String scene, String desc) {
        System.out.println("┌─────────────────────────────────────────────────────────────");
        System.out.printf(" %-56sn", scene + "  " + desc);
        System.out.println("└───────────────────────────────────────────────────────────────");
    }
}
