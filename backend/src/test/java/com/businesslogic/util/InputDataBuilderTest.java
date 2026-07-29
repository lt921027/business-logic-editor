package com.businesslogic.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.businesslogic.executor.AviatorExecutor;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * InputDataBuilder 工具类测试
 *
 * <p>测试将前端传回的扁平 key-value 数据还原为 Aviator 表达式可用的 JSON 字符串的功能。
 * 同时验证生成的 JSON 能被 JsonPathUtil 正确读取，以及能被 Aviator 表达式正常使用。</p>
 *
 * @author businesslogic
 */
public class InputDataBuilderTest {

    /**
     * 测试简单顶层属性
     *
     * <p>输入：{"PH010R02": "abc", "PH010R03": "xyz"}
     * 期望输出：{"PH010R02":"abc","PH010R03":"xyz"}</p>
     */
    @Test
    public void testBuildJson_simpleKeys() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("PH010R02", "abc");
        flatData.put("PH010R03", "xyz");

        String json = InputDataBuilder.buildJson(flatData);
        System.out.println("简单顶层属性测试结果：" + json);

        JSONObject parsed = JSON.parseObject(json);
        assertEquals("abc", parsed.getString("PH010R02"));
        assertEquals("xyz", parsed.getString("PH010R03"));
    }

    /**
     * 测试嵌套对象属性（点号分隔）
     *
     * <p>输入：{"A.B.C": 1, "A.B.D": 2, "A.E": 3}
     * 期望输出：{"A":{"B":{"C":1,"D":2},"E":3}}</p>
     */
    @Test
    public void testBuildJson_nestedKeys() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("A.B.C", 1);
        flatData.put("A.B.D", 2);
        flatData.put("A.E", 3);

        String json = InputDataBuilder.buildJson(flatData);
        System.out.println("嵌套对象属性测试结果：" + json);

        JSONObject parsed = JSON.parseObject(json);
        assertEquals(1, parsed.getJSONObject("A").getJSONObject("B").getIntValue("C"));
        assertEquals(2, parsed.getJSONObject("A").getJSONObject("B").getIntValue("D"));
        assertEquals(3, parsed.getJSONObject("A").getIntValue("E"));
    }

    /**
     * 测试带 $. 前缀的路径
     *
     * <p>输入：{"$.PH010R01": "value1", "$.PH010R02.PH010R03": "value2"}
     * 期望输出：{"PH010R01":"value1","PH010R02":{"PH010R03":"value2"}}</p>
     */
    @Test
    public void testBuildJson_withDollarPrefix() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("$.PH010R01", "value1");
        flatData.put("$.PH010R02.PH010R03", "value2");

        String json = InputDataBuilder.buildJson(flatData);
        System.out.println("带 $. 前缀路径测试结果：" + json);

        JSONObject parsed = JSON.parseObject(json);
        assertEquals("value1", parsed.getString("PH010R01"));
        assertEquals("value2", parsed.getJSONObject("PH010R02").getString("PH010R03"));
    }

    /**
     * 测试数组索引路径
     *
     * <p>模拟前端传回数组中某个元素的字段值。
     * 输入：{"PH010R01[0].PH010RA1": 11, "PH010R01[0].PH010RB1": "abc",
     *        "PH010R01[1].PH010RA1": 112}
     * 期望输出：{"PH010R01":[{"PH010RA1":11,"PH010RB1":"abc"},{"PH010RA1":112}]}</p>
     */
    @Test
    public void testBuildJson_arrayIndex() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("PH010R01[0].PH010RA1", 11);
        flatData.put("PH010R01[0].PH010RB1", "abc");
        flatData.put("PH010R01[1].PH010RA1", 112);

        String json = InputDataBuilder.buildJson(flatData);
        System.out.println("数组索引路径测试结果：" + json);

        JSONObject parsed = JSON.parseObject(json);
        assertEquals(11, parsed.getJSONArray("PH010R01").getJSONObject(0).getIntValue("PH010RA1"));
        assertEquals("abc", parsed.getJSONArray("PH010R01").getJSONObject(0).getString("PH010RB1"));
        assertEquals(112, parsed.getJSONArray("PH010R01").getJSONObject(1).getIntValue("PH010RA1"));
    }

    /**
     * 测试混合路径（顶层 + 嵌套 + 数组）
     *
     * <p>模拟真实场景：前端传回的数据包含顶层字段、嵌套对象和数组元素。</p>
     */
    @Test
    public void testBuildJson_mixedKeys() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("PH010R02", "abc");
        flatData.put("PH010R03", "xyz");
        flatData.put("PH010R01[0].PH010RA1", 11);
        flatData.put("PH010R01[0].PH010RB1", "abc");
        flatData.put("PH010R01[1].PH010RA1", 112);
        flatData.put("PH010R01[1].PH010RB1", "def");
        flatData.put("PH010R01[2].PH010RA1", 113);
        flatData.put("PH010R01[2].PH010RB1", "abc");

        String json = InputDataBuilder.buildJson(flatData);
        System.out.println("混合路径测试结果：" + json);

        JSONObject parsed = JSON.parseObject(json);
        assertEquals("abc", parsed.getString("PH010R02"));
        assertEquals("xyz", parsed.getString("PH010R03"));
        assertEquals(3, parsed.getJSONArray("PH010R01").size());
        assertEquals(11, parsed.getJSONArray("PH010R01").getJSONObject(0).getIntValue("PH010RA1"));
        assertEquals(112, parsed.getJSONArray("PH010R01").getJSONObject(1).getIntValue("PH010RA1"));
        assertEquals(113, parsed.getJSONArray("PH010R01").getJSONObject(2).getIntValue("PH010RA1"));
    }

    /**
     * 测试空数据
     */
    @Test
    public void testBuildJson_emptyData() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        String json = InputDataBuilder.buildJson(flatData);
        assertEquals("{}", json);
    }

    /**
     * 测试 null 数据抛出异常
     */
    @Test
    public void testBuildJson_nullData() {
        assertThrows(IllegalArgumentException.class, () -> {
            InputDataBuilder.buildJson(null);
        });
    }

    /**
     * 测试生成的 JSON 能被 JsonPathUtil 正确读取
     *
     * <p>验证 InputDataBuilder 生成的 JSON 与 JsonPathUtil 的兼容性，
     * 确保表达式中通过 JSONPath 能正确读取到值。</p>
     */
    @Test
    public void testBuildJson_compatibleWithJsonPathUtil() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("PH010R01[0].PH010RA1", 11);
        flatData.put("PH010R01[0].PH010RB1", "abc");
        flatData.put("PH010R01[1].PH010RA1", 112);
        flatData.put("PH010R01[2].PH010RA1", 113);
        flatData.put("PH010R02", "abc");
        flatData.put("PH010R03", "abc");

        String json = InputDataBuilder.buildJson(flatData);
        System.out.println("JsonPathUtil 兼容性测试 JSON：" + json);

        // 测试顶层属性读取
        assertEquals("abc", JsonPathUtil.readString(json, "$.PH010R02"));
        assertEquals("abc", JsonPathUtil.readString(json, "$.PH010R03"));

        // 测试数组元素属性读取
        assertEquals(Integer.valueOf(11), JsonPathUtil.readInt(json, "$.PH010R01[0].PH010RA1"));
        assertEquals("abc", JsonPathUtil.readString(json, "$.PH010R01[0].PH010RB1"));
        assertEquals(Integer.valueOf(112), JsonPathUtil.readInt(json, "$.PH010R01[1].PH010RA1"));
        assertEquals(Integer.valueOf(113), JsonPathUtil.readInt(json, "$.PH010R01[2].PH010RA1"));

        // 测试数组整体读取
        assertNotNull(JsonPathUtil.read(json, "$.PH010R01"));
    }

    /**
     * 测试生成的 JSON 能被 Aviator 表达式正常使用
     *
     * <p>模拟真实的 Aviator 表达式执行场景，验证 InputDataBuilder 生成的 JSON
     * 可以作为 inputData 传入 AviatorExecutor 并正确执行。</p>
     *
     * @throws Exception 执行异常
     */
    @Test
    public void testBuildJson_compatibleWithAviatorExpression() throws Exception {
        // 模拟前端传回的数据
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("PH010R01[0].PH010RA1", 11);
        flatData.put("PH010R01[0].PH010RB1", "abc");
        flatData.put("PH010R01[1].PH010RA1", 112);
        flatData.put("PH010R01[1].PH010RB1", "abc");
        flatData.put("PH010R01[2].PH010RA1", 113);
        flatData.put("PH010R01[2].PH010RB1", "abc");
        flatData.put("PH010R02", "abc");
        flatData.put("PH010R03", "abc");

        // 构建 JSON
        String inputData = InputDataBuilder.buildJson(flatData);
        System.out.println("Aviator 表达式测试 JSON：" + inputData);

        // 编写一个简单的 Aviator 表达式：读取数组第一个元素的 PH010RA1 值
        String expression = "let result = JsonPathUtil.read(inputData, '$.PH010R01[0].PH010RA1');\n"
                + "return result;";

        Object result = AviatorExecutor.execute(expression, inputData);
        System.out.println("Aviator 表达式执行结果：" + result);
        assertNotNull(result);

        // 编写一个更复杂的表达式：遍历数组，累加 PH010RA1 的值
        String complexExpression =
                "let sum = 0;\n"
                + "for item in JsonPathUtil.read(inputData, '$.PH010R01') {\n"
                + "  sum = sum + item['PH010RA1'];\n"
                + "}\n"
                + "return sum;";

        Object complexResult = AviatorExecutor.execute(complexExpression, inputData);
        System.out.println("复杂 Aviator 表达式执行结果（累加和）：" + complexResult);
        // 11 + 112 + 113 = 236
        assertEquals(236L, ((Number) complexResult).longValue());
    }

    /**
     * 测试 buildJsonObject 方法
     */
    @Test
    public void testBuildJsonObject() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("A.B", "value1");
        flatData.put("C", "value2");

        JSONObject jsonObject = InputDataBuilder.buildJsonObject(flatData);
        System.out.println("buildJsonObject 测试结果：" + jsonObject.toJSONString());

        assertEquals("value1", jsonObject.getJSONObject("A").getString("B"));
        assertEquals("value2", jsonObject.getString("C"));
    }

    /**
     * 测试不同数据类型的值
     *
     * <p>验证 String、Integer、Long、Double、Boolean 等常见类型都能正确保留。</p>
     */
    @Test
    public void testBuildJson_variousValueTypes() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("strField", "hello");
        flatData.put("intField", 42);
        flatData.put("longField", 9999999999L);
        flatData.put("doubleField", 3.14);
        flatData.put("boolField", true);
        flatData.put("nullField", null);

        String json = InputDataBuilder.buildJson(flatData);
        System.out.println("多数据类型测试结果：" + json);

        JSONObject parsed = JSON.parseObject(json);
        assertEquals("hello", parsed.getString("strField"));
        assertEquals(Integer.valueOf(42), parsed.getInteger("intField"));
        assertEquals(Long.valueOf(9999999999L), parsed.getLong("longField"));
        assertEquals(Double.valueOf(3.14), parsed.getDouble("doubleField"), 0.001);
        assertEquals(Boolean.TRUE, parsed.getBoolean("boolField"));
        assertNull(parsed.get("nullField"));
    }

    /**
     * 测试深层嵌套（超过 3 层）
     *
     * <p>输入：{"A.B.C.D.E": "deep"}
     * 期望输出：{"A":{"B":{"C":{"D":{"E":"deep"}}}}}</p>
     */
    @Test
    public void testBuildJson_deepNesting() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("A.B.C.D.E", "deep");
        flatData.put("A.B.C.D.F", "also deep");

        String json = InputDataBuilder.buildJson(flatData);
        System.out.println("深层嵌套测试结果：" + json);

        JSONObject parsed = JSON.parseObject(json);
        assertEquals("deep",
                parsed.getJSONObject("A")
                        .getJSONObject("B")
                        .getJSONObject("C")
                        .getJSONObject("D")
                        .getString("E"));
        assertEquals("also deep",
                parsed.getJSONObject("A")
                        .getJSONObject("B")
                        .getJSONObject("C")
                        .getJSONObject("D")
                        .getString("F"));
    }

    /**
     * 测试数组索引不连续的情况
     *
     * <p>输入：{"arr[0]": "a", "arr[3]": "d"}
     * 期望：数组长度为 4，索引 1、2 处为 null</p>
     */
    @Test
    public void testBuildJson_sparseArray() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("arr[0]", "a");
        flatData.put("arr[3]", "d");

        String json = InputDataBuilder.buildJson(flatData);
        System.out.println("稀疏数组测试结果：" + json);

        JSONObject parsed = JSON.parseObject(json);
        assertEquals(4, parsed.getJSONArray("arr").size());
        assertEquals("a", parsed.getJSONArray("arr").getString(0));
        assertNull(parsed.getJSONArray("arr").get(1));
        assertNull(parsed.getJSONArray("arr").get(2));
        assertEquals("d", parsed.getJSONArray("arr").getString(3));
    }

    /**
     * 测试数组元素直接是基本类型（非对象）
     *
     * <p>输入：{"tags[0]": "java", "tags[1]": "python"}
     * 期望输出：{"tags":["java","python"]}</p>
     */
    @Test
    public void testBuildJson_arrayOfPrimitives() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("tags[0]", "java");
        flatData.put("tags[1]", "python");
        flatData.put("tags[2]", "go");

        String json = InputDataBuilder.buildJson(flatData);
        System.out.println("基本类型数组测试结果：" + json);

        JSONObject parsed = JSON.parseObject(json);
        assertEquals(3, parsed.getJSONArray("tags").size());
        assertEquals("java", parsed.getJSONArray("tags").getString(0));
        assertEquals("python", parsed.getJSONArray("tags").getString(1));
        assertEquals("go", parsed.getJSONArray("tags").getString(2));
    }

    /**
     * 测试多个独立数组
     *
     * <p>输入包含两个不同的数组路径，验证互不干扰。</p>
     */
    @Test
    public void testBuildJson_multipleArrays() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("list1[0].name", "Alice");
        flatData.put("list1[1].name", "Bob");
        flatData.put("list2[0].code", "X001");
        flatData.put("list2[1].code", "X002");

        String json = InputDataBuilder.buildJson(flatData);
        System.out.println("多数组测试结果：" + json);

        JSONObject parsed = JSON.parseObject(json);
        assertEquals("Alice", parsed.getJSONArray("list1").getJSONObject(0).getString("name"));
        assertEquals("Bob", parsed.getJSONArray("list1").getJSONObject(1).getString("name"));
        assertEquals("X001", parsed.getJSONArray("list2").getJSONObject(0).getString("code"));
        assertEquals("X002", parsed.getJSONArray("list2").getJSONObject(1).getString("code"));
    }

    /**
     * 测试嵌套数组（数组中包含数组）
     *
     * <p>输入：{"matrix[0][0]": 1, "matrix[0][1]": 2, "matrix[1][0]": 3}
     * 期望输出：{"matrix":[[1,2],[3]]}</p>
     */
    @Test
    public void testBuildJson_nestedArray() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("matrix[0][0]", 1);
        flatData.put("matrix[0][1]", 2);
        flatData.put("matrix[1][0]", 3);

        String json = InputDataBuilder.buildJson(flatData);
        System.out.println("嵌套数组测试结果：" + json);

        JSONObject parsed = JSON.parseObject(json);
        assertEquals(1, parsed.getJSONArray("matrix").getJSONArray(0).getIntValue(0));
        assertEquals(2, parsed.getJSONArray("matrix").getJSONArray(0).getIntValue(1));
        assertEquals(3, parsed.getJSONArray("matrix").getJSONArray(1).getIntValue(0));
    }

    /**
     * 测试路径中包含空格（前后空格应被 trim）
     *
     * <p>输入：{"  A.B  ": "value"}
     * 期望：能正常处理，去除前后空格</p>
     */
    @Test
    public void testBuildJson_pathWithSpaces() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("  A.B  ", "value");

        String json = InputDataBuilder.buildJson(flatData);
        System.out.println("路径含空格测试结果：" + json);

        JSONObject parsed = JSON.parseObject(json);
        assertEquals("value", parsed.getJSONObject("A").getString("B"));
    }

    /**
     * 测试单个条目
     */
    @Test
    public void testBuildJson_singleEntry() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("onlyKey", "onlyValue");

        String json = InputDataBuilder.buildJson(flatData);
        System.out.println("单条目测试结果：" + json);

        JSONObject parsed = JSON.parseObject(json);
        assertEquals("onlyValue", parsed.getString("onlyKey"));
    }

    /**
     * 测试对象嵌套在数组后再嵌套对象
     *
     * <p>输入：{"data[0].info.name": "Tom", "data[0].info.age": 25}
     * 期望输出：{"data":[{"info":{"name":"Tom","age":25}}]}</p>
     */
    @Test
    public void testBuildJson_arrayThenNestedObject() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("data[0].info.name", "Tom");
        flatData.put("data[0].info.age", 25);
        flatData.put("data[1].info.name", "Jerry");
        flatData.put("data[1].info.age", 30);

        String json = InputDataBuilder.buildJson(flatData);
        System.out.println("数组后嵌套对象测试结果：" + json);

        JSONObject parsed = JSON.parseObject(json);
        assertEquals("Tom",
                parsed.getJSONArray("data")
                        .getJSONObject(0)
                        .getJSONObject("info")
                        .getString("name"));
        assertEquals(Integer.valueOf(25),
                parsed.getJSONArray("data")
                        .getJSONObject(0)
                        .getJSONObject("info")
                        .getInteger("age"));
        assertEquals("Jerry",
                parsed.getJSONArray("data")
                        .getJSONObject(1)
                        .getJSONObject("info")
                        .getString("name"));
    }

    /**
     * 测试生成的 JSON 与 JsonPathUtil 的 exists 方法兼容
     */
    @Test
    public void testBuildJson_jsonPathExists() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("A.B.C", "exists");
        flatData.put("arr[0].val", 100);

        String json = InputDataBuilder.buildJson(flatData);

        assertTrue(JsonPathUtil.exists(json, "$.A.B.C"));
        assertTrue(JsonPathUtil.exists(json, "$.arr[0].val"));
        assertFalse(JsonPathUtil.exists(json, "$.nonExistent"));
        assertFalse(JsonPathUtil.exists(json, "$.arr[5].val"));
    }

    /**
     * 测试生成的 JSON 能被 Aviator 表达式用于条件判断
     *
     * <p>模拟真实业务场景：根据前端传回的数据，通过表达式做条件判断。</p>
     *
     * @throws Exception 执行异常
     */
    @Test
    public void testBuildJson_aviatorConditionalExpression() throws Exception {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("order.amount", 500);
        flatData.put("order.status", "PAID");
        flatData.put("order.items[0].price", 200);
        flatData.put("order.items[0].qty", 2);
        flatData.put("order.items[1].price", 100);
        flatData.put("order.items[1].qty", 1);

        String inputData = InputDataBuilder.buildJson(flatData);
        System.out.println("条件判断表达式测试 JSON：" + inputData);

        // 表达式：判断订单金额是否大于 300
        String expression =
                "let amount = JsonPathUtil.readInt(inputData, '$.order.amount');\n"
                + "if (amount > 300) {\n"
                + "  return 'HIGH';\n"
                + "} else {\n"
                + "  return 'LOW';\n"
                + "}";

        Object result = AviatorExecutor.execute(expression, inputData);
        System.out.println("条件判断结果：" + result);
        assertEquals("HIGH", result);
    }

    /**
     * 测试生成的 JSON 能被 Aviator 表达式用于字符串比较
     *
     * @throws Exception 执行异常
     */
    @Test
    public void testBuildJson_aviatorStringComparison() throws Exception {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("user.role", "ADMIN");
        flatData.put("user.name", "testUser");

        String inputData = InputDataBuilder.buildJson(flatData);
        System.out.println("字符串比较表达式测试 JSON：" + inputData);

        String expression =
                "let role = JsonPathUtil.readString(inputData, '$.user.role');\n"
                + "if (StringUtil.equals(role, 'ADMIN')) {\n"
                + "  return true;\n"
                + "}\n"
                + "return false;";

        Object result = AviatorExecutor.execute(expression, inputData);
        System.out.println("字符串比较结果：" + result);
        assertEquals(true, result);
    }

    /**
     * 测试生成的 JSON 能被 Aviator 表达式用于数组遍历和过滤
     *
     * <p>模拟 test.java 中的表达式风格：遍历数组，按条件过滤并计数。</p>
     *
     * @throws Exception 执行异常
     */
    @Test
    public void testBuildJson_aviatorArrayFilterAndCount() throws Exception {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("PH010R01[0].PH010RA1", 11);
        flatData.put("PH010R01[0].PH010RB1", "abc");
        flatData.put("PH010R01[1].PH010RA1", 112);
        flatData.put("PH010R01[1].PH010RB1", "xyz");
        flatData.put("PH010R01[2].PH010RA1", 113);
        flatData.put("PH010R01[2].PH010RB1", "abc");

        String inputData = InputDataBuilder.buildJson(flatData);
        System.out.println("数组过滤表达式测试 JSON：" + inputData);

        // 表达式：统计 PH010RB1 等于 "abc" 的元素个数
        String expression =
                "let matched = seq.list();\n"
                + "for item in JsonPathUtil.read(inputData, '$.PH010R01') {\n"
                + "  if (StringUtil.equals(item['PH010RB1'], 'abc')) {\n"
                + "    seq.add(matched, item);\n"
                + "  }\n"
                + "}\n"
                + "return count(matched);";

        Object result = AviatorExecutor.execute(expression, inputData);
        System.out.println("数组过滤计数结果：" + result);
        // PH010RB1 == "abc" 的有索引 0 和 2，共 2 个
        assertEquals(2L, ((Number) result).longValue());
    }

    /**
     * 测试生成的 JSON 能被 Aviator 表达式用于数组遍历 + 条件判断 + 累加
     *
     * <p>完全模拟 test.java 中的表达式：遍历数组，按条件过滤后累加某字段。</p>
     *
     * @throws Exception 执行异常
     */
    @Test
    public void testBuildJson_aviatorArrayFilterAndReduce() throws Exception {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("PH010R01[0].PH010RA1", 11);
        flatData.put("PH010R01[0].PH010RB1", "abc");
        flatData.put("PH010R01[1].PH010RA1", 112);
        flatData.put("PH010R01[1].PH010RB1", "abc");
        flatData.put("PH010R01[2].PH010RA1", 113);
        flatData.put("PH010R01[2].PH010RB1", "abc");

        String inputData = InputDataBuilder.buildJson(flatData);
        System.out.println("数组过滤累加表达式测试 JSON：" + inputData);

        // 表达式：遍历数组，过滤 PH010RB1 == 'abc' 的元素，累加 PH010RA1
        String expression =
                "let step1true = seq.list();\n"
                + "for item in JsonPathUtil.read(inputData, '$.PH010R01') {\n"
                + "  if (StringUtil.equals(item['PH010RB1'], 'abc')) {\n"
                + "    seq.add(step1true, item);\n"
                + "  }\n"
                + "}\n"
                + "let step1 = 0;\n"
                + "let temp = distinct(step1true);\n"
                + "step1 = reduce(temp, lambda(x, y) -> x + y['PH010RA1'] end, 0);\n"
                + "return step1;";

        Object result = AviatorExecutor.execute(expression, inputData);
        System.out.println("数组过滤累加结果：" + result);
        // 11 + 112 + 113 = 236
        assertEquals(236L, ((Number) result).longValue());
    }

    /**
     * 测试生成的 JSON 能被 Aviator 表达式读取 readList
     *
     * @throws Exception 执行异常
     */
    @Test
    public void testBuildJson_aviatorReadList() throws Exception {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("scores[0]", 80);
        flatData.put("scores[1]", 90);
        flatData.put("scores[2]", 75);

        String inputData = InputDataBuilder.buildJson(flatData);
        System.out.println("readList 表达式测试 JSON：" + inputData);

        // 表达式：读取数组并求和
        String expression =
                "let list = JsonPathUtil.read(inputData, '$.scores');\n"
                + "let total = 0;\n"
                + "for s in list {\n"
                + "  total = total + s;\n"
                + "}\n"
                + "return total;";

        Object result = AviatorExecutor.execute(expression, inputData);
        System.out.println("readList 求和结果：" + result);
        // 80 + 90 + 75 = 245
        assertEquals(245L, ((Number) result).longValue());
    }

    /**
     * 测试生成的 JSON 能被 Aviator 表达式用于多层嵌套读取
     *
     * @throws Exception 执行异常
     */
    @Test
    public void testBuildJson_aviatorDeepNestedRead() throws Exception {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("company.department.team.lead.name", "张三");
        flatData.put("company.department.team.lead.level", 5);
        flatData.put("company.department.team.size", 10);

        String inputData = InputDataBuilder.buildJson(flatData);
        System.out.println("多层嵌套读取表达式测试 JSON：" + inputData);

        String expression =
                "let name = JsonPathUtil.readString(inputData, '$.company.department.team.lead.name');\n"
                + "let level = JsonPathUtil.readInt(inputData, '$.company.department.team.lead.level');\n"
                + "let size = JsonPathUtil.readInt(inputData, '$.company.department.team.size');\n"
                + "return name + '-' + level + '-' + size;";

        Object result = AviatorExecutor.execute(expression, inputData);
        System.out.println("多层嵌套读取结果：" + result);
        assertEquals("张三-5-10", result);
    }

    /**
     * 测试重复路径（后值覆盖前值）
     *
     * <p>当同一个路径出现多次时，后面的值应该覆盖前面的值。</p>
     */
    @Test
    public void testBuildJson_duplicatePathOverride() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("A.B", "first");
        flatData.put("A.B", "second");
        flatData.put("A.B", "third");

        String json = InputDataBuilder.buildJson(flatData);
        System.out.println("重复路径覆盖测试结果：" + json);

        JSONObject parsed = JSON.parseObject(json);
        assertEquals("third", parsed.getJSONObject("A").getString("B"));
    }

    /**
     * 测试特殊字符作为 key
     *
     * <p>测试包含特殊字符（如连字符、下划线、数字开头等）的 key。</p>
     */
    @Test
    public void testBuildJson_specialCharacterKeys() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("field-with-dash", "value1");
        flatData.put("field_with_underscore", "value2");
        flatData.put("123numericStart", "value3");
        flatData.put("field.with.dots", "value4");

        String json = InputDataBuilder.buildJson(flatData);
        System.out.println("特殊字符 key 测试结果：" + json);

        JSONObject parsed = JSON.parseObject(json);
        assertEquals("value1", parsed.getString("field-with-dash"));
        assertEquals("value2", parsed.getString("field_with_underscore"));
        assertEquals("value3", parsed.getString("123numericStart"));
        // field.with.dots 会被解析为嵌套结构
        assertNotNull(parsed.getJSONObject("field"));
    }

    /**
     * 测试只有 $ 前缀没有路径
     *
     * <p>输入：{"$": "value"}
     * 期望：路径为空，应该被跳过</p>
     */
    @Test
    public void testBuildJson_onlyDollarPrefix() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("$", "value");
        flatData.put("validKey", "validValue");

        String json = InputDataBuilder.buildJson(flatData);
        System.out.println("只有 $ 前缀测试结果：" + json);

        JSONObject parsed = JSON.parseObject(json);
        // $ 路径应该被跳过，只有 validKey
        assertNull(parsed.get("$"));
        assertEquals("validValue", parsed.getString("validKey"));
    }

    /**
     * 测试与 JsonPathUtil 的 readBoolean 方法兼容
     */
    @Test
    public void testBuildJson_jsonPathReadBoolean() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("flag1", true);
        flatData.put("flag2", false);
        flatData.put("obj.enabled", true);

        String json = InputDataBuilder.buildJson(flatData);

        assertEquals(Boolean.TRUE, JsonPathUtil.readBoolean(json, "$.flag1"));
        assertEquals(Boolean.FALSE, JsonPathUtil.readBoolean(json, "$.flag2"));
        assertEquals(Boolean.TRUE, JsonPathUtil.readBoolean(json, "$.obj.enabled"));
    }

    /**
     * 测试与 JsonPathUtil 的 readDouble 方法兼容
     */
    @Test
    public void testBuildJson_jsonPathReadDouble() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("price", 99.99);
        flatData.put("discount", 0.85);
        flatData.put("item.rate", 1.5);

        String json = InputDataBuilder.buildJson(flatData);

        assertEquals(Double.valueOf(99.99), JsonPathUtil.readDouble(json, "$.price"), 0.001);
        assertEquals(Double.valueOf(0.85), JsonPathUtil.readDouble(json, "$.discount"), 0.001);
        assertEquals(Double.valueOf(1.5), JsonPathUtil.readDouble(json, "$.item.rate"), 0.001);
    }

    /**
     * 测试与 JsonPathUtil 的 readLong 方法兼容
     */
    @Test
    public void testBuildJson_jsonPathReadLong() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("bigNumber", 9999999999L);
        flatData.put("timestamp", 1234567890123L);
        flatData.put("data.id", 8888888888L);

        String json = InputDataBuilder.buildJson(flatData);

        assertEquals(Long.valueOf(9999999999L), JsonPathUtil.readLong(json, "$.bigNumber"));
        assertEquals(Long.valueOf(1234567890123L), JsonPathUtil.readLong(json, "$.timestamp"));
        assertEquals(Long.valueOf(8888888888L), JsonPathUtil.readLong(json, "$.data.id"));
    }

    /**
     * 测试与 JsonPathUtil 的 readList 方法兼容
     */
    @Test
    public void testBuildJson_jsonPathReadList() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("items[0].name", "Item1");
        flatData.put("items[0].price", 10.5);
        flatData.put("items[1].name", "Item2");
        flatData.put("items[1].price", 20.0);

        String json = InputDataBuilder.buildJson(flatData);

        java.util.List<Object> list = JsonPathUtil.readList(json, "$.items");
        assertNotNull(list);
        assertEquals(2, list.size());
    }

    /**
     * 测试复杂业务场景：订单校验表达式
     *
     * <p>模拟真实业务：前端传回订单数据，表达式校验订单是否满足多个条件。</p>
     *
     * @throws Exception 执行异常
     */
    @Test
    public void testBuildJson_complexOrderValidation() throws Exception {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("order.orderId", "ORD-2026-001");
        flatData.put("order.customer.level", "VIP");
        flatData.put("order.totalAmount", 1500);
        flatData.put("order.items[0].productId", "P001");
        flatData.put("order.items[0].quantity", 2);
        flatData.put("order.items[0].unitPrice", 500);
        flatData.put("order.items[1].productId", "P002");
        flatData.put("order.items[1].quantity", 1);
        flatData.put("order.items[1].unitPrice", 500);
        flatData.put("order.shipping.address", "北京市朝阳区");
        flatData.put("order.shipping.express", "SF");

        String inputData = InputDataBuilder.buildJson(flatData);
        System.out.println("复杂订单校验表达式测试 JSON：" + inputData);

        // 表达式：VIP 客户且订单金额大于 1000，返回 "APPROVED"，否则 "REJECTED"
        String expression =
                "let level = JsonPathUtil.readString(inputData, '$.order.customer.level');\n"
                + "let amount = JsonPathUtil.readInt(inputData, '$.order.totalAmount');\n"
                + "if (StringUtil.equals(level, 'VIP') && amount > 1000) {\n"
                + "  return 'APPROVED';\n"
                + "} else {\n"
                + "  return 'REJECTED';\n"
                + "}";

        Object result = AviatorExecutor.execute(expression, inputData);
        System.out.println("订单校验结果：" + result);
        assertEquals("APPROVED", result);
    }

    /**
     * 测试复杂业务场景：数组元素条件判断
     *
     * <p>模拟真实业务：遍历数组，判断每个元素是否满足条件。</p>
     *
     * @throws Exception 执行异常
     */
    @Test
    public void testBuildJson_arrayElementConditionCheck() throws Exception {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("students[0].name", "Alice");
        flatData.put("students[0].score", 85);
        flatData.put("students[0].passed", true);
        flatData.put("students[1].name", "Bob");
        flatData.put("students[1].score", 58);
        flatData.put("students[1].passed", false);
        flatData.put("students[2].name", "Charlie");
        flatData.put("students[2].score", 92);
        flatData.put("students[2].passed", true);

        String inputData = InputDataBuilder.buildJson(flatData);
        System.out.println("数组元素条件判断表达式测试 JSON：" + inputData);

        // 表达式：统计及格（score >= 60）的学生人数
        String expression =
                "let passedCount = 0;\n"
                + "for student in JsonPathUtil.read(inputData, '$.students') {\n"
                + "  if (student['score'] >= 60) {\n"
                + "    passedCount = passedCount + 1;\n"
                + "  }\n"
                + "}\n"
                + "return passedCount;";

        Object result = AviatorExecutor.execute(expression, inputData);
        System.out.println("及格学生人数：" + result);
        assertEquals(2L, ((Number) result).longValue());
    }

    /**
     * 测试复杂业务场景：多层嵌套数据读取和计算
     *
     * <p>模拟真实业务：从深层嵌套结构中读取数据并进行计算。</p>
     *
     * @throws Exception 执行异常
     */
    @Test
    public void testBuildJson_deepNestedCalculation() throws Exception {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("report.year", 2026);
        flatData.put("report.quarter", "Q1");
        flatData.put("report.department.sales.revenue", 500000);
        flatData.put("report.department.sales.cost", 300000);
        flatData.put("report.department.sales.profit", 200000);
        flatData.put("report.department.hr.headcount", 50);

        String inputData = InputDataBuilder.buildJson(flatData);
        System.out.println("深层嵌套计算表达式测试 JSON：" + inputData);

        // 表达式：计算利润率（profit / revenue * 100）
        String expression =
                "let revenue = JsonPathUtil.readDouble(inputData, '$.report.department.sales.revenue');\n"
                + "let profit = JsonPathUtil.readDouble(inputData, '$.report.department.sales.profit');\n"
                + "let profitMargin = profit / revenue * 100;\n"
                + "return profitMargin;";

        Object result = AviatorExecutor.execute(expression, inputData);
        System.out.println("利润率：" + result);
        // 200000 / 500000 * 100 = 40.0
        assertEquals(40.0, ((Number) result).doubleValue(), 0.001);
    }

    /**
     * 测试复杂业务场景：数组过滤 + 嵌套对象访问
     *
     * <p>模拟真实业务：过滤数组中满足条件的元素，并访问其嵌套属性。</p>
     *
     * @throws Exception 执行异常
     */
    @Test
    public void testBuildJson_arrayFilterWithNestedAccess() throws Exception {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("employees[0].name", "张三");
        flatData.put("employees[0].department", "IT");
        flatData.put("employees[0].salary", 15000);
        flatData.put("employees[0].skills[0]", "Java");
        flatData.put("employees[0].skills[1]", "Python");
        flatData.put("employees[1].name", "李四");
        flatData.put("employees[1].department", "HR");
        flatData.put("employees[1].salary", 12000);
        flatData.put("employees[1].skills[0]", "Recruitment");
        flatData.put("employees[2].name", "王五");
        flatData.put("employees[2].department", "IT");
        flatData.put("employees[2].salary", 18000);
        flatData.put("employees[2].skills[0]", "JavaScript");
        flatData.put("employees[2].skills[1]", "Go");

        String inputData = InputDataBuilder.buildJson(flatData);
        System.out.println("数组过滤嵌套访问表达式测试 JSON：" + inputData);

        // 表达式：统计 IT 部门且薪资大于 14000 的员工数量
        String expression =
                "let count = 0;\n"
                + "for emp in JsonPathUtil.read(inputData, '$.employees') {\n"
                + "  if (StringUtil.equals(emp['department'], 'IT') && emp['salary'] > 14000) {\n"
                + "    count = count + 1;\n"
                + "  }\n"
                + "}\n"
                + "return count;";

        Object result = AviatorExecutor.execute(expression, inputData);
        System.out.println("符合条件的员工数量：" + result);
        // 张三（IT, 15000）和王五（IT, 18000）符合条件
        assertEquals(2L, ((Number) result).longValue());
    }

    /**
     * 测试生成的 JSON 格式正确性（可被标准 JSON 解析器解析）
     */
    @Test
    public void testBuildJson_validJsonFormat() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("key1", "value1");
        flatData.put("key2", 123);
        flatData.put("key3", true);
        flatData.put("nested.key", "nestedValue");
        flatData.put("array[0]", "item1");
        flatData.put("array[1].name", "item2");

        String json = InputDataBuilder.buildJson(flatData);
        System.out.println("JSON 格式验证测试结果：" + json);

        // 验证可以被 fastjson 解析
        JSONObject parsed = JSON.parseObject(json);
        assertNotNull(parsed);

        // 验证可以被 jackson 解析
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode jacksonNode = mapper.readTree(json);
            assertNotNull(jacksonNode);
        } catch (Exception e) {
            fail("JSON 格式不正确，无法被 Jackson 解析：" + e.getMessage());
        }
    }

    /**
     * 测试大量数据的性能
     *
     * <p>测试构建包含大量条目的 JSON 的性能。</p>
     */
    @Test
    public void testBuildJson_largeDataSet() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        // 添加 1000 个条目
        for (int i = 0; i < 1000; i++) {
            flatData.put("item" + i + ".value", i);
        }

        long startTime = System.currentTimeMillis();
        String json = InputDataBuilder.buildJson(flatData);
        long endTime = System.currentTimeMillis();

        System.out.println("大数据集测试：构建 1000 个条目耗时 " + (endTime - startTime) + "ms");
        System.out.println("生成的 JSON 长度：" + json.length());

        // 验证 JSON 可以被解析
        JSONObject parsed = JSON.parseObject(json);
        assertNotNull(parsed);
        assertEquals(1000, parsed.size());
    }

    /**
     * 测试空字符串值
     */
    @Test
    public void testBuildJson_emptyStringValue() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("emptyStr", "");
        flatData.put("normalStr", "value");

        String json = InputDataBuilder.buildJson(flatData);
        System.out.println("空字符串值测试结果：" + json);

        JSONObject parsed = JSON.parseObject(json);
        assertEquals("", parsed.getString("emptyStr"));
        assertEquals("value", parsed.getString("normalStr"));
    }

    /**
     * 测试中文字符
     */
    @Test
    public void testBuildJson_chineseCharacters() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("中文key", "中文value");
        flatData.put("data.名称", "测试数据");
        flatData.put("list[0].内容", "第一项");

        String json = InputDataBuilder.buildJson(flatData);
        System.out.println("中文字符测试结果：" + json);

        JSONObject parsed = JSON.parseObject(json);
        assertEquals("中文value", parsed.getString("中文key"));
        assertEquals("测试数据", parsed.getJSONObject("data").getString("名称"));
        assertEquals("第一项", parsed.getJSONArray("list").getJSONObject(0).getString("内容"));
    }

    /**
     * 测试带类型信息的构建方法 - 基本类型
     *
     * <p>验证 string、number、boolean、null 类型的正确处理。</p>
     */
    @Test
    public void testBuildJsonWithType_basicTypes() {
        java.util.List<Map<String, Object>> dataList = new java.util.ArrayList<>();

        // 字符串类型
        Map<String, Object> item1 = new LinkedHashMap<>();
        item1.put("path", "strField");
        item1.put("value", "hello");
        item1.put("type", "string");
        dataList.add(item1);

        // 数字类型（整数）
        Map<String, Object> item2 = new LinkedHashMap<>();
        item2.put("path", "intField");
        item2.put("value", "42");
        item2.put("type", "number");
        dataList.add(item2);

        // 数字类型（浮点数）
        Map<String, Object> item3 = new LinkedHashMap<>();
        item3.put("path", "doubleField");
        item3.put("value", "3.14");
        item3.put("type", "double");
        dataList.add(item3);

        // 布尔类型（true）
        Map<String, Object> item4 = new LinkedHashMap<>();
        item4.put("path", "boolField");
        item4.put("value", "true");
        item4.put("type", "boolean");
        dataList.add(item4);

        // null 类型
        Map<String, Object> item5 = new LinkedHashMap<>();
        item5.put("path", "nullField");
        item5.put("value", "");
        item5.put("type", "null");
        dataList.add(item5);

        String json = InputDataBuilder.buildJsonWithType(dataList);
        System.out.println("带类型信息的基本类型测试结果：" + json);

        JSONObject parsed = JSON.parseObject(json);
        assertEquals("hello", parsed.getString("strField"));
        assertEquals(Integer.valueOf(42), parsed.getInteger("intField"));
        assertEquals(Double.valueOf(3.14), parsed.getDouble("doubleField"), 0.001);
        assertEquals(Boolean.TRUE, parsed.getBoolean("boolField"));
        assertNull(parsed.get("nullField"));
    }

    /**
     * 测试带类型信息的构建方法 - 嵌套路径
     *
     * <p>验证带类型信息的数据在嵌套路径下的正确构建。</p>
     */
    @Test
    public void testBuildJsonWithType_nestedPath() {
        java.util.List<Map<String, Object>> dataList = new java.util.ArrayList<>();

        Map<String, Object> item1 = new LinkedHashMap<>();
        item1.put("path", "order.amount");
        item1.put("value", "500");
        item1.put("type", "number");
        dataList.add(item1);

        Map<String, Object> item2 = new LinkedHashMap<>();
        item2.put("path", "order.status");
        item2.put("value", "PAID");
        item2.put("type", "string");
        dataList.add(item2);

        Map<String, Object> item3 = new LinkedHashMap<>();
        item3.put("path", "order.paid");
        item3.put("value", "true");
        item3.put("type", "boolean");
        dataList.add(item3);

        String json = InputDataBuilder.buildJsonWithType(dataList);
        System.out.println("带类型信息的嵌套路径测试结果：" + json);

        JSONObject parsed = JSON.parseObject(json);
        assertEquals(Integer.valueOf(500), parsed.getJSONObject("order").getInteger("amount"));
        assertEquals("PAID", parsed.getJSONObject("order").getString("status"));
        assertEquals(Boolean.TRUE, parsed.getJSONObject("order").getBoolean("paid"));
    }

    /**
     * 测试带类型信息的构建方法 - 数组路径
     *
     * <p>验证带类型信息的数据在数组路径下的正确构建。</p>
     */
    @Test
    public void testBuildJsonWithType_arrayPath() {
        java.util.List<Map<String, Object>> dataList = new java.util.ArrayList<>();

        Map<String, Object> item1 = new LinkedHashMap<>();
        item1.put("path", "items[0].price");
        item1.put("value", "100");
        item1.put("type", "number");
        dataList.add(item1);

        Map<String, Object> item2 = new LinkedHashMap<>();
        item2.put("path", "items[0].name");
        item2.put("value", "Product A");
        item2.put("type", "string");
        dataList.add(item2);

        Map<String, Object> item3 = new LinkedHashMap<>();
        item3.put("path", "items[1].price");
        item3.put("value", "200");
        item3.put("type", "int");
        dataList.add(item3);

        Map<String, Object> item4 = new LinkedHashMap<>();
        item4.put("path", "items[1].name");
        item4.put("value", "Product B");
        item4.put("type", "String");
        dataList.add(item4);

        String json = InputDataBuilder.buildJsonWithType(dataList);
        System.out.println("带类型信息的数组路径测试结果：" + json);

        JSONObject parsed = JSON.parseObject(json);
        assertEquals(Integer.valueOf(100), parsed.getJSONArray("items").getJSONObject(0).getInteger("price"));
        assertEquals("Product A", parsed.getJSONArray("items").getJSONObject(0).getString("name"));
        assertEquals(Integer.valueOf(200), parsed.getJSONArray("items").getJSONObject(1).getInteger("price"));
        assertEquals("Product B", parsed.getJSONArray("items").getJSONObject(1).getString("name"));
    }

    /**
     * 测试带类型信息的构建方法 - 各种数字类型别名
     *
     * <p>验证 number、int、integer、long、double、float 等类型别名都能正确处理。</p>
     */
    @Test
    public void testBuildJsonWithType_numberTypeAliases() {
        java.util.List<Map<String, Object>> dataList = new java.util.ArrayList<>();

        // number 类型
        Map<String, Object> item1 = new LinkedHashMap<>();
        item1.put("path", "field1");
        item1.put("value", "100");
        item1.put("type", "number");
        dataList.add(item1);

        // int 类型
        Map<String, Object> item2 = new LinkedHashMap<>();
        item2.put("path", "field2");
        item2.put("value", "200");
        item2.put("type", "int");
        dataList.add(item2);

        // integer 类型
        Map<String, Object> item3 = new LinkedHashMap<>();
        item3.put("path", "field3");
        item3.put("value", "300");
        item3.put("type", "integer");
        dataList.add(item3);

        // long 类型
        Map<String, Object> item4 = new LinkedHashMap<>();
        item4.put("path", "field4");
        item4.put("value", "9999999999");
        item4.put("type", "long");
        dataList.add(item4);

        // double 类型
        Map<String, Object> item5 = new LinkedHashMap<>();
        item5.put("path", "field5");
        item5.put("value", "3.14");
        item5.put("type", "double");
        dataList.add(item5);

        // float 类型
        Map<String, Object> item6 = new LinkedHashMap<>();
        item6.put("path", "field6");
        item6.put("value", "2.71");
        item6.put("type", "float");
        dataList.add(item6);

        String json = InputDataBuilder.buildJsonWithType(dataList);
        System.out.println("数字类型别名测试结果：" + json);

        JSONObject parsed = JSON.parseObject(json);
        assertEquals(Integer.valueOf(100), parsed.getInteger("field1"));
        assertEquals(Integer.valueOf(200), parsed.getInteger("field2"));
        assertEquals(Integer.valueOf(300), parsed.getInteger("field3"));
        assertEquals(Long.valueOf(9999999999L), parsed.getLong("field4"));
        assertEquals(Double.valueOf(3.14), parsed.getDouble("field5"), 0.001);
        assertEquals(Double.valueOf(2.71), parsed.getDouble("field6"), 0.001);
    }

    /**
     * 测试带类型信息的构建方法 - 布尔类型各种表示
     *
     * <p>验证 true/false、1/0、yes/no 等布尔值表示都能正确处理。</p>
     */
    @Test
    public void testBuildJsonWithType_booleanAliases() {
        java.util.List<Map<String, Object>> dataList = new java.util.ArrayList<>();

        // true
        Map<String, Object> item1 = new LinkedHashMap<>();
        item1.put("path", "bool1");
        item1.put("value", "true");
        item1.put("type", "boolean");
        dataList.add(item1);

        // TRUE（大写）
        Map<String, Object> item2 = new LinkedHashMap<>();
        item2.put("path", "bool2");
        item2.put("value", "TRUE");
        item2.put("type", "boolean");
        dataList.add(item2);

        // 1 表示 true
        Map<String, Object> item3 = new LinkedHashMap<>();
        item3.put("path", "bool3");
        item3.put("value", "1");
        item3.put("type", "boolean");
        dataList.add(item3);

        // yes 表示 true
        Map<String, Object> item4 = new LinkedHashMap<>();
        item4.put("path", "bool4");
        item4.put("value", "yes");
        item4.put("type", "boolean");
        dataList.add(item4);

        // false
        Map<String, Object> item5 = new LinkedHashMap<>();
        item5.put("path", "bool5");
        item5.put("value", "false");
        item5.put("type", "boolean");
        dataList.add(item5);

        // 0 表示 false
        Map<String, Object> item6 = new LinkedHashMap<>();
        item6.put("path", "bool6");
        item6.put("value", "0");
        item6.put("type", "boolean");
        dataList.add(item6);

        // no 表示 false
        Map<String, Object> item7 = new LinkedHashMap<>();
        item7.put("path", "bool7");
        item7.put("value", "no");
        item7.put("type", "boolean");
        dataList.add(item7);

        String json = InputDataBuilder.buildJsonWithType(dataList);
        System.out.println("布尔类型别名测试结果：" + json);

        JSONObject parsed = JSON.parseObject(json);
        assertEquals(Boolean.TRUE, parsed.getBoolean("bool1"));
        assertEquals(Boolean.TRUE, parsed.getBoolean("bool2"));
        assertEquals(Boolean.TRUE, parsed.getBoolean("bool3"));
        assertEquals(Boolean.TRUE, parsed.getBoolean("bool4"));
        assertEquals(Boolean.FALSE, parsed.getBoolean("bool5"));
        assertEquals(Boolean.FALSE, parsed.getBoolean("bool6"));
        assertEquals(Boolean.FALSE, parsed.getBoolean("bool7"));
    }

    /**
     * 测试带类型信息的构建方法 - 空数据和 null 数据
     */
    @Test
    public void testBuildJsonWithType_emptyAndNull() {
        // 空列表
        java.util.List<Map<String, Object>> emptyList = new java.util.ArrayList<>();
        String emptyJson = InputDataBuilder.buildJsonWithType(emptyList);
        assertEquals("{}", emptyJson);

        // null 列表
        assertThrows(IllegalArgumentException.class, () -> {
            InputDataBuilder.buildJsonWithType(null);
        });
    }

    /**
     * 测试带类型信息的构建方法 - 无类型字段默认作为字符串
     *
     * <p>验证当 type 字段为空或不存在时，值默认作为字符串处理。</p>
     */
    @Test
    public void testBuildJsonWithType_noType() {
        java.util.List<Map<String, Object>> dataList = new java.util.ArrayList<>();

        // 没有 type 字段
        Map<String, Object> item1 = new LinkedHashMap<>();
        item1.put("path", "field1");
        item1.put("value", "123");
        dataList.add(item1);

        // type 为空字符串
        Map<String, Object> item2 = new LinkedHashMap<>();
        item2.put("path", "field2");
        item2.put("value", "456");
        item2.put("type", "");
        dataList.add(item2);

        // type 为 null
        Map<String, Object> item3 = new LinkedHashMap<>();
        item3.put("path", "field3");
        item3.put("value", "789");
        item3.put("type", null);
        dataList.add(item3);

        String json = InputDataBuilder.buildJsonWithType(dataList);
        System.out.println("无类型字段测试结果：" + json);

        JSONObject parsed = JSON.parseObject(json);
        // 都应该作为字符串处理
        assertEquals("123", parsed.getString("field1"));
        assertEquals("456", parsed.getString("field2"));
        assertEquals("789", parsed.getString("field3"));
    }

    /**
     * 测试带类型信息的构建方法 - 与 Aviator 表达式集成
     *
     * <p>验证带类型信息构建的 JSON 能被 Aviator 表达式正确使用。</p>
     *
     * @throws Exception 执行异常
     */
    @Test
    public void testBuildJsonWithType_aviatorIntegration() throws Exception {
        java.util.List<Map<String, Object>> dataList = new java.util.ArrayList<>();

        Map<String, Object> item1 = new LinkedHashMap<>();
        item1.put("path", "amount");
        item1.put("value", "500");
        item1.put("type", "number");
        dataList.add(item1);

        Map<String, Object> item2 = new LinkedHashMap<>();
        item2.put("path", "status");
        item2.put("value", "PAID");
        item2.put("type", "string");
        dataList.add(item2);

        Map<String, Object> item3 = new LinkedHashMap<>();
        item3.put("path", "items[0].price");
        item3.put("value", "100");
        item3.put("type", "number");
        dataList.add(item3);

        Map<String, Object> item4 = new LinkedHashMap<>();
        item4.put("path", "items[1].price");
        item4.put("value", "200");
        item4.put("type", "number");
        dataList.add(item4);

        String inputData = InputDataBuilder.buildJsonWithType(dataList);
        System.out.println("带类型信息的 Aviator 集成测试 JSON：" + inputData);

        // 表达式：判断金额是否大于 300，并累加数组中的价格
        String expression =
                "let amount = JsonPathUtil.readInt(inputData, '$.amount');\n"
                + "let sum = 0;\n"
                + "for item in JsonPathUtil.read(inputData, '$.items') {\n"
                + "  sum = sum + item['price'];\n"
                + "}\n"
                + "if (amount > 300) {\n"
                + "  return sum + 1000;\n"
                + "} else {\n"
                + "  return sum;\n"
                + "}";

        Object result = AviatorExecutor.execute(expression, inputData);
        System.out.println("带类型信息的 Aviator 执行结果：" + result);
        // amount=500 > 300，所以返回 sum(100+200) + 1000 = 1300
        assertEquals(1300L, ((Number) result).longValue());
    }

    /**
     * 测试带类型信息的构建方法 - 与 JsonPathUtil 集成
     *
     * <p>验证带类型信息构建的 JSON 能被 JsonPathUtil 正确读取。</p>
     */
    @Test
    public void testBuildJsonWithType_jsonPathIntegration() {
        java.util.List<Map<String, Object>> dataList = new java.util.ArrayList<>();

        Map<String, Object> item1 = new LinkedHashMap<>();
        item1.put("path", "intField");
        item1.put("value", "42");
        item1.put("type", "number");
        dataList.add(item1);

        Map<String, Object> item2 = new LinkedHashMap<>();
        item2.put("path", "strField");
        item2.put("value", "hello");
        item2.put("type", "string");
        dataList.add(item2);

        Map<String, Object> item3 = new LinkedHashMap<>();
        item3.put("path", "boolField");
        item3.put("value", "true");
        item3.put("type", "boolean");
        dataList.add(item3);

        Map<String, Object> item4 = new LinkedHashMap<>();
        item4.put("path", "doubleField");
        item4.put("value", "3.14");
        item4.put("type", "double");
        dataList.add(item4);

        String json = InputDataBuilder.buildJsonWithType(dataList);

        // 验证 JsonPathUtil 能正确读取各种类型
        assertEquals(Integer.valueOf(42), JsonPathUtil.readInt(json, "$.intField"));
        assertEquals("hello", JsonPathUtil.readString(json, "$.strField"));
        assertEquals(Boolean.TRUE, JsonPathUtil.readBoolean(json, "$.boolField"));
        assertEquals(Double.valueOf(3.14), JsonPathUtil.readDouble(json, "$.doubleField"), 0.001);
    }

    /**
     * 测试带类型信息的构建方法 - 复杂业务场景
     *
     * <p>模拟真实业务场景：前端传回订单数据，包含各种类型。</p>
     *
     * @throws Exception 执行异常
     */
    @Test
    public void testBuildJsonWithType_complexBusinessScenario() throws Exception {
        java.util.List<Map<String, Object>> dataList = new java.util.ArrayList<>();

        // 订单基本信息
        Map<String, Object> item1 = new LinkedHashMap<>();
        item1.put("path", "order.orderId");
        item1.put("value", "ORD-2026-001");
        item1.put("type", "string");
        dataList.add(item1);

        Map<String, Object> item2 = new LinkedHashMap<>();
        item2.put("path", "order.totalAmount");
        item2.put("value", "1500");
        item2.put("type", "number");
        dataList.add(item2);

        Map<String, Object> item3 = new LinkedHashMap<>();
        item3.put("path", "order.paid");
        item3.put("value", "true");
        item3.put("type", "boolean");
        dataList.add(item3);

        // 订单商品
        Map<String, Object> item4 = new LinkedHashMap<>();
        item4.put("path", "order.items[0].productId");
        item4.put("value", "P001");
        item4.put("type", "string");
        dataList.add(item4);

        Map<String, Object> item5 = new LinkedHashMap<>();
        item5.put("path", "order.items[0].quantity");
        item5.put("value", "2");
        item5.put("type", "number");
        dataList.add(item5);

        Map<String, Object> item6 = new LinkedHashMap<>();
        item6.put("path", "order.items[0].unitPrice");
        item6.put("value", "500");
        item6.put("type", "number");
        dataList.add(item6);

        Map<String, Object> item7 = new LinkedHashMap<>();
        item7.put("path", "order.items[1].productId");
        item7.put("value", "P002");
        item7.put("type", "string");
        dataList.add(item7);

        Map<String, Object> item8 = new LinkedHashMap<>();
        item8.put("path", "order.items[1].quantity");
        item8.put("value", "1");
        item8.put("type", "number");
        dataList.add(item8);

        Map<String, Object> item9 = new LinkedHashMap<>();
        item9.put("path", "order.items[1].unitPrice");
        item9.put("value", "500");
        item9.put("type", "number");
        dataList.add(item9);

        String inputData = InputDataBuilder.buildJsonWithType(dataList);
        System.out.println("复杂业务场景测试 JSON：" + inputData);

        // 表达式：验证订单数据并计算总价
        String expression =
                "let orderId = JsonPathUtil.readString(inputData, '$.order.orderId');\n"
                + "let paid = JsonPathUtil.readBoolean(inputData, '$.order.paid');\n"
                + "let totalAmount = JsonPathUtil.readInt(inputData, '$.order.totalAmount');\n"
                + "let calculatedTotal = 0;\n"
                + "for item in JsonPathUtil.read(inputData, '$.order.items') {\n"
                + "  calculatedTotal = calculatedTotal + item['quantity'] * item['unitPrice'];\n"
                + "}\n"
                + "if (paid && totalAmount == calculatedTotal) {\n"
                + "  return 'VALID';\n"
                + "} else {\n"
                + "  return 'INVALID';\n"
                + "}";

        Object result = AviatorExecutor.execute(expression, inputData);
        System.out.println("复杂业务场景验证结果：" + result);
        assertEquals("VALID", result);
    }
}
