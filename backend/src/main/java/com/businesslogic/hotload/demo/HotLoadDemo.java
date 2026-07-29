package com.businesslogic.hotload.demo;

import com.businesslogic.hotload.AviatorFunctionRegistry;
import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Expression;

import java.util.HashMap;
import java.util.Map;

/**
 * 热加载功能完整演 *
 * 演示三种函数注册方式的使用和热更新效 */
public class HotLoadDemo {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("Aviator 函数热加载演");
        System.out.println("========================================\n");

        AviatorEvaluatorInstance evaluator = AviatorEvaluator.getInstance();
        AviatorFunctionRegistry registry = new AviatorFunctionRegistry(evaluator);
        registry.init();

        demo1_ExpressionFunction(registry, evaluator);
        demo2_ScriptFunction(registry, evaluator);
        demo3_StaticFunctions(registry, evaluator);
        demo4_HotUpdate(registry, evaluator);

        System.out.println("\n========================================");
        System.out.println("演示完成");
        System.out.println("========================================");
    }

    /**
     * 演示1：表达式函数
     *
     * 将简单表达式包装为函数，支持参数传     */
    private static void demo1_ExpressionFunction(AviatorFunctionRegistry registry, AviatorEvaluatorInstance evaluator) {
        System.out.println("【演】表达式函数");
        System.out.println("----------------------------------------");

        registry.registerExpressionFunction("calculateBonus",
                "salary * performance * 0.1",
                "salary", "performance");

        Map<String, Object> env = new HashMap<>();
        env.put("salary", 10000);
        env.put("performance", 1.5);

        Object result = evaluator.execute("calculateBonus(salary, performance)", env);
        System.out.println("calculateBonus(10000, 1.5) = " + result);
        System.out.println("预期结果: 1500.0");
        System.out.println();
    }

    /**
     * 演示2：脚本函     *
     * 多行脚本支持复杂业务逻辑
     */
    private static void demo2_ScriptFunction(AviatorFunctionRegistry registry, AviatorEvaluatorInstance evaluator) {
        System.out.println("【演】脚本函");
        System.out.println("----------------------------------------");

        String script = "let base = price * rate;\n" +
                "let discount = base > 1000 ? 0.9 : 1.0;\n" +
                "return base * discount;";

        registry.registerScriptFunction("calculateTax", script, "price", "rate");

        Map<String, Object> env = new HashMap<>();
        env.put("price", 2000);
        env.put("rate", 0.15);

        Object result = evaluator.execute("calculateTax(price, rate)", env);
        System.out.println("calculateTax(2000, 0.15) = " + result);
        System.out.println("预期结果: 270.0 (2000*0.15*0.9)");
        System.out.println();
    }

    /**
     * 演示3：静态方法批量注     *
     * 复用现有工具类的方法
     */
    private static void demo3_StaticFunctions(AviatorFunctionRegistry registry, AviatorEvaluatorInstance evaluator) {
        System.out.println("【演】静态方法批量注");
        System.out.println("----------------------------------------");

        registry.registerStaticFunctions("str", StringUtils.class);

        System.out.println("注册 StringUtils 的静态方法，前缀'str_'");
        System.out.println("可用函数: str_isBlank, str_upperCase, str_reverse");

        Map<String, Object> env = new HashMap<>();
        env.put("text", "hello world");

        Object result = evaluator.execute("str_upperCase(text)", env);
        System.out.println("str_upperCase('hello world') = " + result);
        System.out.println();
    }

    /**
     * 演示4：热更新
     *
     * 运行时修改函数逻辑，无需重启
     */
    private static void demo4_HotUpdate(AviatorFunctionRegistry registry, AviatorEvaluatorInstance evaluator) {
        System.out.println("【演】热更新");
        System.out.println("----------------------------------------");

        registry.registerExpressionFunction("discount",
                "price * 0.9",
                "price");

        Map<String, Object> env = new HashMap<>();
        env.put("price", 100);

        Object result1 = evaluator.execute("discount(price)", env);
        System.out.println("更新 discount(100) = " + result1 + " (9");

        registry.updateFunction("discount", "price * 0.8", "price");

        Object result2 = evaluator.execute("discount(price)", env);
        System.out.println("更新 discount(100) = " + result2 + " (8");
        System.out.println("函数已热更新，无需重启服务");
        System.out.println();
    }

    /**
     * 演示工具     */
    public static class StringUtils {
        public static boolean isBlank(String str) {
            return str == null || str.trim().isEmpty();
        }

        public static String upperCase(String str) {
            return str != null ? str.toUpperCase() : null;
        }

        public static String reverse(String str) {
            return str != null ? new StringBuilder(str).reverse().toString() : null;
        }
    }
}
