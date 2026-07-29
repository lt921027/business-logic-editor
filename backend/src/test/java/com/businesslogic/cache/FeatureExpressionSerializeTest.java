package com.businesslogic.cache;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Expression;
import com.googlecode.aviator.Options;
import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.type.AviatorObject;
import com.googlecode.aviator.runtime.type.AviatorRuntimeJavaType;
import com.googlecode.aviator.serialize.AviatorObjectInputStream;
import com.googlecode.aviator.serialize.AviatorObjectOutputStream;
import org.junit.jupiter.api.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Aviator 5.4.1 序列/ 反序列化专项测试
 *
 * <p>5.4.1 的序列化方式：使{@link AviatorObjectOutputStream} + {@link AviatorObjectInputStream}
 * （位com.googlecode.aviator.serialize 包），解5.3.3  * <code>ClassNotFoundException: AviatorScript_xxx</code> bug *
 * <p>覆盖场景 * <ul>
 *   <li>基本算术 / 逻辑 / 字符串表达式</li>
 *   <li>三元表达式、字面量、复杂嵌/li>
 *   <li>自定义函数（反序列化前先注册/li>
 *   <li>序列化的字节稳定性（幂等性）</li>
 *   <li>evaluator 实例反序列化</li>
 *   <li>大体积表达式</li>
 *   <li>空环/ null 值场/li>
 *   <li>toString 一致/li>
 * </ul>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Aviator 5.4.1 序列化专项测")
public class FeatureExpressionSerializeTest {

    private static AviatorEvaluatorInstance evaluator;

    /**
     * 工具方法：将 Expression 序列化为字节数组
     */
    private static byte[] serializeExpr(Expression exp) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (AviatorObjectOutputStream oos = new AviatorObjectOutputStream(baos)) {
            oos.writeObject(exp);
        }
        return baos.toByteArray();
    }

    /**
     * 工具方法：从字节数组反序列化Expression
     */
    private static Expression deserializeExpr(byte[] bytes,
                                               AviatorEvaluatorInstance instance) throws Exception {
        try (AviatorObjectInputStream ois = new AviatorObjectInputStream(
                new ByteArrayInputStream(bytes), instance)) {
            return (Expression) ois.readObject();
        }
    }

    @BeforeAll
    static void init() {
        evaluator = AviatorEvaluator.getInstance();
        // 关键：序列化前必须启SERIALIZABLE 选项
        // 否则 compile() 不会缓存 class bytes，AviatorObjectOutputStream 序列化时会抛
        //   IllegalArgumentException: Class bytes not found: AviatorScript_xxx
        evaluator.setOption(Options.SERIALIZABLE, true);
        //注册自定义函数：双倍数
        evaluator.addFunction(new DoubleNumFunction());
        System.out.println("══════════════════════════════════════════");
        System.out.println("Aviator version: " + AviatorEvaluator.VERSION);
        System.out.println("已注册自定义函数: doubleNum(x)");
        System.out.println("SERIALIZABLE = " + evaluator.getOption(Options.SERIALIZABLE));
        System.out.println("══════════════════════════════════════════");
    }

    // ─────────────────────────────────────────────────────────────
    // 1. 基本算术表达    // ─────────────────────────────────────────────────────────────
    @Test
    @Order(1)
    @DisplayName("1. 基本算术表达a + b * 2")
    void testSerializeBasicArithmetic() throws Exception {
        System.out.println("\n[1] 基本算术表达");

        Expression original = evaluator.compile("a + b * 2");
        Map<String, Object> env = new HashMap<>();
        env.put("a", 10);
        env.put("b", 3);

        byte[] bytes = serializeExpr(original);
        Expression restored = deserializeExpr(bytes, evaluator);
        Object result = restored.execute(env);

        System.out.println("  原始结果     = " + original.execute(env));
        System.out.println("  反序列化结果 = " + result);
        System.out.println("  字节长度     = " + bytes.length);

        assertEquals(16L, ((Number) result).longValue()); // 10 + 3*2
    }

    // ─────────────────────────────────────────────────────────────
    // 2. 字符串字面量 + 比较
    // ─────────────────────────────────────────────────────────────
    @Test
    @Order(2)
    @DisplayName("2. 字符串字面量表达status == 'ACTIVE' && amount > 100")
    void testSerializeStringLiteral() throws Exception {
        System.out.println("\n[2] 字符串字面量表达");

        String expr = "status == 'ACTIVE' && amount > 100";
        Expression original = evaluator.compile(expr);
        Expression restored = deserializeExpr(serializeExpr(original), evaluator);

        Map<String, Object> case1 = new HashMap<>();
        case1.put("status", "ACTIVE");
        case1.put("amount", 250);
        Map<String, Object> case2 = new HashMap<>();
        case2.put("status", "INACTIVE");
        case2.put("amount", 250);

        Object r1 = restored.execute(case1);
        Object r2 = restored.execute(case2);
        System.out.println("  case1 (ACTIVE, 250)  = " + r1);
        System.out.println("  case2 (INACTIVE,250) = " + r2);

        assertEquals(Boolean.TRUE, r1);
        assertEquals(Boolean.FALSE, r2);
    }

    // ─────────────────────────────────────────────────────────────
    // 3. 三元表达    // ─────────────────────────────────────────────────────────────
    @Test
    @Order(3)
    @DisplayName("3. 三元表达score >= 60 ? 'PASS' : 'FAIL'")
    void testSerializeTernary() throws Exception {
        System.out.println("\n[3] 三元表达");

        String expr = "score >= 60 ? 'PASS' : 'FAIL'";
        Expression original = evaluator.compile(expr);
        Expression restored = deserializeExpr(serializeExpr(original), evaluator);

        Map<String, Object> pass = new HashMap<>();
        pass.put("score", 85);
        Map<String, Object> fail = new HashMap<>();
        fail.put("score", 45);

        assertEquals("PASS", restored.execute(pass));
        assertEquals("FAIL", restored.execute(fail));
        System.out.println("  score=85 " + restored.execute(pass));
        System.out.println("  score=45 " + restored.execute(fail));
    }

    // ─────────────────────────────────────────────────────────────
    // 4. 复杂嵌套 + 字面    // ─────────────────────────────────────────────────────────────
    @Test
    @Order(4)
    @DisplayName("4. 复杂嵌套表达含数组字面量、字符串、算")
    void testSerializeComplexNested() throws Exception {
        System.out.println("\n[4] 复杂嵌套表达");

        String expr = "(price - cost) * quantity * (1 + taxRate) >= 1000 && (category == 'A' || category == 'B' || category == 'C')";
        Expression original = evaluator.compile(expr);
        Expression restored = deserializeExpr(serializeExpr(original), evaluator);

        Map<String, Object> env = new HashMap<>();
        env.put("price", 100);
        env.put("cost", 30);
        env.put("quantity", 5);
        env.put("taxRate", 0.13);
        env.put("category", "B");

        // (100-30) * 5 * 1.13 = 395.5 < 1000 false
        assertEquals(Boolean.FALSE, restored.execute(env));
        System.out.println("  qty=5    " + restored.execute(env));

        env.put("quantity", 20);
        // (100-30) * 20 * 1.13 = 1582 >= 1000 true
        assertEquals(Boolean.TRUE, restored.execute(env));
        System.out.println("  qty=20   " + restored.execute(env));
    }

    // ─────────────────────────────────────────────────────────────
    // 5. 自定义函5.3.3 反序列化失败的高发场    // ─────────────────────────────────────────────────────────────
    @Test
    @Order(5)
    @DisplayName("5. 含自定义函数 doubleNum 验证 5.3.3 ClassNotFoundException 已修")
    void testSerializeWithCustomFunction() throws Exception {
        System.out.println("\n[5] 自定义函数表达式");

        String expr = "doubleNum(amount) > 200";
        Expression original = evaluator.compile(expr);
        Expression restored = deserializeExpr(serializeExpr(original), evaluator);

        Map<String, Object> env = new HashMap<>();
        env.put("amount", 150);
        // doubleNum(150) = 300
        Object r1 = restored.execute(env);
        assertEquals(Boolean.TRUE, r1);
        System.out.println("  amount=150 " + r1);

        env.put("amount", 50);
        // doubleNum(50) = 100
        Object r2 = restored.execute(env);
        assertEquals(Boolean.FALSE, r2);
        System.out.println("  amount=50  " + r2);
    }

    // ─────────────────────────────────────────────────────────────
    // 6. 序列化字节的幂等    // ─────────────────────────────────────────────────────────────
    @Test
    @Order(6)
    @DisplayName("6. 多次 serialize 同一 Expression 字节数组应一")
    void testSerializeIdempotent() throws Exception {
        System.out.println("\n[6] 序列化幂等");

        Expression exp = evaluator.compile("x * x + 2 * x + 1");
        byte[] b1 = serializeExpr(exp);
        byte[] b2 = serializeExpr(exp);
        byte[] b3 = serializeExpr(exp);

        System.out.println("  b1.length = " + b1.length);
        System.out.println("  b2.length = " + b2.length);
        System.out.println("  b3.length = " + b3.length);

        assertArrayEquals(b1, b2, "同一 Expression 序列化结果应一");
        assertArrayEquals(b2, b3, "同一 Expression 序列化结果应一");
    }

    // ─────────────────────────────────────────────────────────────
    // 7. evaluator 实例反序列化
    // ─────────────────────────────────────────────────────────────
    @Test
    @Order(7)
    @DisplayName("7. evaluator 实例 序列化后在新实例上反序列化执")
    void testDeserializeAcrossEvaluatorInstances() throws Exception {
        System.out.println("\n[7] evaluator 实例");

        AviatorEvaluatorInstance src = AviatorEvaluator.getInstance();
        AviatorEvaluatorInstance dst = AviatorEvaluator.newInstance();

        Expression exp = src.compile("a * a + b * b");
        byte[] bytes = serializeExpr(exp);

        Expression restored = deserializeExpr(bytes, dst);
        Map<String, Object> env = new HashMap<>();
        env.put("a", 3);
        env.put("b", 4);

        Object result = restored.execute(env);
        System.out.println("  3^2 + 4^2 = " + result);
        assertEquals(25L, ((Number) result).longValue());
    }

    // ─────────────────────────────────────────────────────────────
    // 8. 大体积表达式
    // ─────────────────────────────────────────────────────────────
    @Test
    @Order(8)
    @DisplayName("8. 大体积表达式 1000 个变量相")
    void testSerializeLargeExpression() throws Exception {
        System.out.println("\n[8] 大体积表达式");

        StringBuilder sb = new StringBuilder("a0");
        for (int i = 1; i < 1000; i++) {
            sb.append(" + a").append(i);
        }
        String expr = sb.toString();
        System.out.println("  表达式长= " + expr.length() + " 字符");

        Expression original = evaluator.compile(expr);
        Expression restored = deserializeExpr(serializeExpr(original), evaluator);

        Map<String, Object> env = new HashMap<>();
        long sum = 0;
        for (int i = 0; i < 1000; i++) {
            env.put("a" + i, 1L);
            sum += 1;
        }

        Object result = restored.execute(env);
        System.out.println("  result = " + result);
        assertEquals(sum, ((Number) result).longValue());
    }

    // ─────────────────────────────────────────────────────────────
    // 9. 空环境执    // ─────────────────────────────────────────────────────────────
    @Test
    @Order(9)
    @DisplayName("9. 空环境执表达式无外部变量引用")
    void testSerializeNoEnvExpression() throws Exception {
        System.out.println("\n[9] 空环境执");

        Expression original = evaluator.compile("1 + 2 + 3 + 4 + 5");
        Expression restored = deserializeExpr(serializeExpr(original), evaluator);

        Object result = restored.execute();
        System.out.println("  1+2+3+4+5 = " + result);
        assertEquals(15L, ((Number) result).longValue());
    }

    // ─────────────────────────────────────────────────────────────
    // 10. null 值场    // ─────────────────────────────────────────────────────────────
    @Test
    @Order(10)
    @DisplayName("10. null 值场value == nil 的安全处")
    void testSerializeWithNullEnvValue() throws Exception {
        System.out.println("\n[10] null 值场");

        Expression original = evaluator.compile("value == nil");
        Expression restored = deserializeExpr(serializeExpr(original), evaluator);

        Map<String, Object> env = new HashMap<>();
        env.put("value", null);

        Object result = restored.execute(env);
        System.out.println("  null == nil = " + result);
        assertEquals(Boolean.TRUE, result);
    }

    // ─────────────────────────────────────────────────────────────
    // 11. 反序列化Expression 哈希一致    // ─────────────────────────────────────────────────────────────
    @Test
    @Order(11)
    @DisplayName("11. 反序列化后执行结果与原表达式一致")
    void testSerializePreserveToString() throws Exception {
        System.out.println("\n[11] 执行结果一致");

        String src = "user.age >= 18 && user.country == 'CN'";
        Expression original = evaluator.compile(src);
        Expression restored = deserializeExpr(serializeExpr(original), evaluator);

        Map<String, Object> user1 = new HashMap<>();
        user1.put("age", 25);
        user1.put("country", "CN");
        Map<String, Object> env1 = new HashMap<>();
        env1.put("user", user1);

        Map<String, Object> user2 = new HashMap<>();
        user2.put("age", 15);
        user2.put("country", "US");
        Map<String, Object> env2 = new HashMap<>();
        env2.put("user", user2);

        assertEquals(original.execute(env1), restored.execute(env1));
        assertEquals(original.execute(env2), restored.execute(env2));
        System.out.println("  case1: " + restored.execute(env1));
        System.out.println("  case2: " + restored.execute(env2));
    }

    @AfterAll
    static void summary() {
        System.out.println("\n══════════════════════════════════════════");
        System.out.println("Aviator 序列化专项测试全部完(version: "
                + AviatorEvaluator.VERSION + ")");
        System.out.println("══════════════════════════════════════════");
    }

    // ─────────────────────────────────────────────────────────────
    // 自定义函数：doubleNum(x) x * 2
    // ─────────────────────────────────────────────────────────────
    static class DoubleNumFunction extends AbstractFunction {
        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject arg1) {
            Number n = (Number) arg1.getValue(env);
            return AviatorRuntimeJavaType.valueOf(n.longValue() * 2);
        }

        @Override
        public String getName() {
            return "doubleNum";
        }
    }
}
