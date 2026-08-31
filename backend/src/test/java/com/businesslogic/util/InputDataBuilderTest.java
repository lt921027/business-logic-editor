package com.businesslogic.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.businesslogic.dto.JsonPathParamDTO;
import com.businesslogic.groovy.engine.GroovyExecutor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * InputDataBuilder 工具类测试
 *
 * <p>覆盖 buildJson（扁平 key-value 还原）与 buildInputDataJson
 * （JsonPathParamDTO 参数列表 → inputData JSON）两类构建逻辑，
 * 表达式集成统一使用 Groovy 引擎。</p>
 */
public class InputDataBuilderTest {

    // ==================== buildJson：扁平路径还原 ====================

    @Test
    public void testBuildJson_simpleKeys() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("PH010R02", "abc");
        flatData.put("PH010R03", "xyz");

        String json = InputDataBuilder.buildJson(flatData);
        JSONObject parsed = JSON.parseObject(json);
        assertEquals("abc", parsed.getString("PH010R02"));
        assertEquals("xyz", parsed.getString("PH010R03"));
    }

    @Test
    public void testBuildJson_nestedKeys() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("A.B.C", 1);
        flatData.put("A.B.D", 2);
        flatData.put("A.E", 3);

        String json = InputDataBuilder.buildJson(flatData);
        JSONObject parsed = JSON.parseObject(json);
        assertEquals(1, parsed.getJSONObject("A").getJSONObject("B").getIntValue("C"));
        assertEquals(2, parsed.getJSONObject("A").getJSONObject("B").getIntValue("D"));
        assertEquals(3, parsed.getJSONObject("A").getIntValue("E"));
    }

    @Test
    public void testBuildJson_withDollarPrefix() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("$.PH010R01", "value1");
        flatData.put("$.PH010R02.PH010R03", "value2");

        String json = InputDataBuilder.buildJson(flatData);
        JSONObject parsed = JSON.parseObject(json);
        assertEquals("value1", parsed.getString("PH010R01"));
        assertEquals("value2", parsed.getJSONObject("PH010R02").getString("PH010R03"));
    }

    @Test
    public void testBuildJson_arrayIndex() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("PH010R01[0].PH010RA1", 11);
        flatData.put("PH010R01[0].PH010RB1", "abc");
        flatData.put("PH010R01[1].PH010RA1", 112);

        String json = InputDataBuilder.buildJson(flatData);
        JSONObject parsed = JSON.parseObject(json);
        assertEquals(11, parsed.getJSONArray("PH010R01").getJSONObject(0).getIntValue("PH010RA1"));
        assertEquals("abc", parsed.getJSONArray("PH010R01").getJSONObject(0).getString("PH010RB1"));
        assertEquals(112, parsed.getJSONArray("PH010R01").getJSONObject(1).getIntValue("PH010RA1"));
    }

    @Test
    public void testBuildJson_emptyData() {
        assertEquals("{}", InputDataBuilder.buildJson(new LinkedHashMap<>()));
    }

    @Test
    public void testBuildJson_nullData() {
        assertThrows(IllegalArgumentException.class, () -> InputDataBuilder.buildJson(null));
    }

    @Test
    public void testBuildJson_compatibleWithJsonPathUtil() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("PH010R01[0].PH010RA1", 11);
        flatData.put("PH010R01[1].PH010RA1", 112);
        flatData.put("PH010R02", "abc");

        String json = InputDataBuilder.buildJson(flatData);
        assertEquals("abc", JsonPathUtil.readString(json, "$.PH010R02"));
        assertEquals(Integer.valueOf(11), JsonPathUtil.readInt(json, "$.PH010R01[0].PH010RA1"));
        assertEquals(Integer.valueOf(112), JsonPathUtil.readInt(json, "$.PH010R01[1].PH010RA1"));
        assertNotNull(JsonPathUtil.read(json, "$.PH010R01"));
    }

    @Test
    public void testBuildJsonObject() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("A.B", "value1");
        flatData.put("C", "value2");

        JSONObject jsonObject = InputDataBuilder.buildJsonObject(flatData);
        assertEquals("value1", jsonObject.getJSONObject("A").getString("B"));
        assertEquals("value2", jsonObject.getString("C"));
    }

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
        JSONObject parsed = JSON.parseObject(json);
        assertEquals("hello", parsed.getString("strField"));
        assertEquals(Integer.valueOf(42), parsed.getInteger("intField"));
        assertEquals(Long.valueOf(9999999999L), parsed.getLong("longField"));
        assertEquals(Double.valueOf(3.14), parsed.getDouble("doubleField"), 0.001);
        assertEquals(Boolean.TRUE, parsed.getBoolean("boolField"));
        assertNull(parsed.get("nullField"));
    }

    @Test
    public void testBuildJson_deepNesting() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("A.B.C.D.E", "deep");
        flatData.put("A.B.C.D.F", "also deep");

        String json = InputDataBuilder.buildJson(flatData);
        JSONObject parsed = JSON.parseObject(json);
        assertEquals("deep",
                parsed.getJSONObject("A").getJSONObject("B").getJSONObject("C")
                        .getJSONObject("D").getString("E"));
        assertEquals("also deep",
                parsed.getJSONObject("A").getJSONObject("B").getJSONObject("C")
                        .getJSONObject("D").getString("F"));
    }

    @Test
    public void testBuildJson_sparseArray() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("arr[0]", "a");
        flatData.put("arr[3]", "d");

        String json = InputDataBuilder.buildJson(flatData);
        JSONObject parsed = JSON.parseObject(json);
        assertEquals(4, parsed.getJSONArray("arr").size());
        assertEquals("a", parsed.getJSONArray("arr").getString(0));
        assertNull(parsed.getJSONArray("arr").get(1));
        assertNull(parsed.getJSONArray("arr").get(2));
        assertEquals("d", parsed.getJSONArray("arr").getString(3));
    }

    @Test
    public void testBuildJson_arrayOfPrimitives() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("tags[0]", "java");
        flatData.put("tags[1]", "python");

        String json = InputDataBuilder.buildJson(flatData);
        JSONObject parsed = JSON.parseObject(json);
        assertEquals(2, parsed.getJSONArray("tags").size());
        assertEquals("java", parsed.getJSONArray("tags").getString(0));
        assertEquals("python", parsed.getJSONArray("tags").getString(1));
    }

    @Test
    public void testBuildJson_nestedArray() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("matrix[0][0]", 1);
        flatData.put("matrix[0][1]", 2);
        flatData.put("matrix[1][0]", 3);

        String json = InputDataBuilder.buildJson(flatData);
        JSONObject parsed = JSON.parseObject(json);
        assertEquals(1, parsed.getJSONArray("matrix").getJSONArray(0).getIntValue(0));
        assertEquals(2, parsed.getJSONArray("matrix").getJSONArray(0).getIntValue(1));
        assertEquals(3, parsed.getJSONArray("matrix").getJSONArray(1).getIntValue(0));
    }

    @Test
    public void testBuildJson_duplicatePathOverride() {
        Map<String, Object> flatData = new LinkedHashMap<>();
        flatData.put("A.B", "first");
        flatData.put("A.B", "second");

        String json = InputDataBuilder.buildJson(flatData);
        JSONObject parsed = JSON.parseObject(json);
        assertEquals("second", parsed.getJSONObject("A").getString("B"));
    }

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
        JSONObject parsed = JSON.parseObject(json);
        assertEquals("value1", parsed.getString("key1"));
        assertEquals(Integer.valueOf(123), parsed.getInteger("key2"));
        assertEquals(Boolean.TRUE, parsed.getBoolean("key3"));
        assertEquals("nestedValue", parsed.getJSONObject("nested").getString("key"));
        assertEquals("item1", parsed.getJSONArray("array").getString(0));
        assertEquals("item2", parsed.getJSONArray("array").getJSONObject(1).getString("name"));
    }

    // ==================== buildInputDataJson：JsonPathParamDTO 参数列表 ====================

    @Test
    public void testBuildInputDataJson_objectNode() {
        List<JsonPathParamDTO> paramList = new ArrayList<>();
        paramList.add(new JsonPathParamDTO("$.amount", "500", "number"));
        paramList.add(new JsonPathParamDTO("$.user", "{\"name\":\"Tom\",\"age\":30}", "object"));

        String json = InputDataBuilder.buildInputDataJson(paramList);
        System.out.println("对象节点测试结果：" + json);

        JSONObject parsed = JSON.parseObject(json);
        assertEquals(Integer.valueOf(500), parsed.getInteger("amount"));
        assertEquals("Tom", parsed.getJSONObject("user").getString("name"));
        assertEquals(Integer.valueOf(30), parsed.getJSONObject("user").getInteger("age"));
    }

    @Test
    public void testBuildInputDataJson_arrayNode() {
        List<JsonPathParamDTO> paramList = new ArrayList<>();
        paramList.add(new JsonPathParamDTO("$.tags", "[\"java\",\"python\"]", "array"));
        paramList.add(new JsonPathParamDTO("$.items",
                "[{\"name\":\"A\",\"price\":10},{\"name\":\"B\",\"price\":20}]", "array"));

        String json = InputDataBuilder.buildInputDataJson(paramList);
        System.out.println("数组节点测试结果：" + json);

        JSONObject parsed = JSON.parseObject(json);
        assertEquals(2, parsed.getJSONArray("tags").size());
        assertEquals("java", parsed.getJSONArray("tags").getString(0));
        assertEquals(2, parsed.getJSONArray("items").size());
        assertEquals("A", parsed.getJSONArray("items").getJSONObject(0).getString("name"));
        assertEquals(Integer.valueOf(20), parsed.getJSONArray("items").getJSONObject(1).getInteger("price"));
    }

    @Test
    public void testBuildInputDataJson_nestedWithObjectMerge() {
        List<JsonPathParamDTO> paramList = new ArrayList<>();
        paramList.add(new JsonPathParamDTO("$.order", "{\"orderId\":\"ORD-001\"}", "object"));
        paramList.add(new JsonPathParamDTO("$.order.totalAmount", "1500", "number"));
        paramList.add(new JsonPathParamDTO("$.order.paid", "true", "boolean"));

        String json = InputDataBuilder.buildInputDataJson(paramList);
        System.out.println("对象合并测试结果：" + json);

        JSONObject order = JSON.parseObject(json).getJSONObject("order");
        assertEquals("ORD-001", order.getString("orderId"));
        assertEquals(Integer.valueOf(1500), order.getInteger("totalAmount"));
        assertEquals(Boolean.TRUE, order.getBoolean("paid"));
    }

    @Test
    public void testBuildInputDataJson_nonStringValues() {
        List<JsonPathParamDTO> paramList = new ArrayList<>();
        paramList.add(new JsonPathParamDTO("$.count", 42, "number"));
        paramList.add(new JsonPathParamDTO("$.enabled", true, "boolean"));

        Map<String, Object> userMap = new LinkedHashMap<>();
        userMap.put("name", "Tom");
        paramList.add(new JsonPathParamDTO("$.user", userMap, "object"));

        List<String> tagList = new ArrayList<>();
        tagList.add("a");
        tagList.add("b");
        paramList.add(new JsonPathParamDTO("$.tags", tagList, "array"));

        String json = InputDataBuilder.buildInputDataJson(paramList);
        System.out.println("非字符串值测试结果：" + json);

        JSONObject parsed = JSON.parseObject(json);
        assertEquals(Integer.valueOf(42), parsed.getInteger("count"));
        assertEquals(Boolean.TRUE, parsed.getBoolean("enabled"));
        assertEquals("Tom", parsed.getJSONObject("user").getString("name"));
        assertEquals(2, parsed.getJSONArray("tags").size());
    }

    @Test
    public void testBuildInputDataJson_typeAliases() {
        List<JsonPathParamDTO> paramList = new ArrayList<>();
        paramList.add(new JsonPathParamDTO("$.f1", "100", "int"));
        paramList.add(new JsonPathParamDTO("$.f2", "3.14", "Double"));
        paramList.add(new JsonPathParamDTO("$.f3", "true", "Boolean"));
        paramList.add(new JsonPathParamDTO("$.f4", "", "null"));
        paramList.add(new JsonPathParamDTO("$.f5", "{\"a\":1}", "Object"));
        paramList.add(new JsonPathParamDTO("$.f6", "[1,2]", "List"));

        String json = InputDataBuilder.buildInputDataJson(paramList);
        System.out.println("类型别名测试结果：" + json);

        JSONObject parsed = JSON.parseObject(json);
        assertEquals(Integer.valueOf(100), parsed.getInteger("f1"));
        assertEquals(Double.valueOf(3.14), parsed.getDouble("f2"), 0.001);
        assertEquals(Boolean.TRUE, parsed.getBoolean("f3"));
        assertNull(parsed.get("f4"));
        assertEquals(Integer.valueOf(1), parsed.getJSONObject("f5").getInteger("a"));
        assertEquals(2, parsed.getJSONArray("f6").size());
    }

    @Test
    public void testBuildInputDataJson_emptyAndNull() {
        assertEquals("{}", InputDataBuilder.buildInputDataJson(new ArrayList<>()));
        assertThrows(IllegalArgumentException.class, () -> InputDataBuilder.buildInputDataJson(null));
    }

    @Test
    public void testBuildInputDataJson_groovyIntegration() throws Exception {
        List<JsonPathParamDTO> paramList = new ArrayList<>();
        paramList.add(new JsonPathParamDTO("$.amount", "500", "number"));
        paramList.add(new JsonPathParamDTO("$.user", "{\"name\":\"Tom\",\"level\":\"GOLD\"}", "object"));
        paramList.add(new JsonPathParamDTO("$.items", "[{\"price\":100},{\"price\":200}]", "array"));

        String inputData = InputDataBuilder.buildInputDataJson(paramList);
        System.out.println("Groovy 集成测试 inputData：" + inputData);

        String expression =
                "def level = JsonPathUtil.readString(inputData, '$.user.level');\n"
                + "def sum = 0;\n"
                + "for (item in JsonPathUtil.read(inputData, '$.items')) {\n"
                + "  sum = sum + item['price'];\n"
                + "}\n"
                + "if (level == 'GOLD' && sum > 250) {\n"
                + "  return 'PASS';\n"
                + "} else {\n"
                + "  return 'FAIL';\n"
                + "}";

        Object result = GroovyExecutor.execute(expression, inputData);
        System.out.println("Groovy 集成测试执行结果：" + result);
        assertEquals("PASS", result);
    }

    /**
     * 测试数组通配符 A[*].B：数组为空时创建一个元素承载值
     */
    @Test
    public void testBuildInputDataJson_wildcardCreatesElement() {
        List<JsonPathParamDTO> paramList = new ArrayList<>();
        paramList.add(new JsonPathParamDTO("$.list[*].name", "Tom", "string"));

        String json = InputDataBuilder.buildInputDataJson(paramList);
        System.out.println("通配符创建元素测试结果：" + json);

        JSONObject parsed = JSON.parseObject(json);
        assertEquals(1, parsed.getJSONArray("list").size());
        assertEquals("Tom", parsed.getJSONArray("list").getJSONObject(0).getString("name"));
    }

    /**
     * 测试数组通配符 A[*].B：已有多个元素时批量设置到所有元素
     */
    @Test
    public void testBuildInputDataJson_wildcardAppliesToExistingElements() {
        List<JsonPathParamDTO> paramList = new ArrayList<>();
        paramList.add(new JsonPathParamDTO("$.list[0].id", "1", "number"));
        paramList.add(new JsonPathParamDTO("$.list[1].id", "2", "number"));
        paramList.add(new JsonPathParamDTO("$.list[*].name", "Tom", "string"));

        String json = InputDataBuilder.buildInputDataJson(paramList);
        System.out.println("通配符批量设置测试结果：" + json);

        JSONObject parsed = JSON.parseObject(json);
        assertEquals(2, parsed.getJSONArray("list").size());
        assertEquals("Tom", parsed.getJSONArray("list").getJSONObject(0).getString("name"));
        assertEquals("Tom", parsed.getJSONArray("list").getJSONObject(1).getString("name"));
        assertEquals(Integer.valueOf(1), parsed.getJSONArray("list").getJSONObject(0).getInteger("id"));
        assertEquals(Integer.valueOf(2), parsed.getJSONArray("list").getJSONObject(1).getInteger("id"));
    }

    /**
     * 测试数组通配符 A[*] 作为最后一段：直接设置数组所有元素
     */
    @Test
    public void testBuildInputDataJson_wildcardAsLastSegment() {
        List<JsonPathParamDTO> paramList = new ArrayList<>();
        paramList.add(new JsonPathParamDTO("$.tags[0]", "java", "string"));
        paramList.add(new JsonPathParamDTO("$.tags[1]", "python", "string"));
        paramList.add(new JsonPathParamDTO("$.tags[*]", "go", "string"));

        String json = InputDataBuilder.buildInputDataJson(paramList);
        System.out.println("通配符最后一段测试结果：" + json);

        JSONObject parsed = JSON.parseObject(json);
        assertEquals(2, parsed.getJSONArray("tags").size());
        assertEquals("go", parsed.getJSONArray("tags").getString(0));
        assertEquals("go", parsed.getJSONArray("tags").getString(1));
    }

    /**
     * 测试深层对象内的通配符：Data.PCA.PD02[*].PD02A.PD02AD01
     */
    @Test
    public void testBuildInputDataJson_deepWildcardInObject() {
        List<JsonPathParamDTO> paramList = new ArrayList<>();
        paramList.add(new JsonPathParamDTO(
                "$.Data.PCA.PD02[*].PD02A.PD02AD01", "hello", "string"));

        String json = InputDataBuilder.buildInputDataJson(paramList);
        System.out.println("深层对象通配符测试结果：" + json);

        JSONObject parsed = JSON.parseObject(json);
        JSONObject pd02a = parsed.getJSONObject("Data")
                .getJSONObject("PCA")
                .getJSONArray("PD02")
                .getJSONObject(0)
                .getJSONObject("PD02A");
        assertEquals("hello", pd02a.getString("PD02AD01"));
    }
}
