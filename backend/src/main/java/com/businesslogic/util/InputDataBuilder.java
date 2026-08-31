package com.businesslogic.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.businesslogic.dto.JsonPathParamDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 输入数据构建工具类
 *
 * <p>用于将前端传回的扁平 key-value 数据还原为 Aviator 表达式可用的 JSON 字符串。
 * 前端只回传 JSON 中某个节点的路径和对应的值，本工具将这些路径-值对
 * 按照 JSONPath 的层级关系还原为完整的嵌套 JSON 结构。</p>
 *
 * <h3>使用场景</h3>
 * <p>Aviator 表达式中通过 {@code JsonPathUtil.read(inputData, '$.xxx')} 读取数据，
 * inputData 需要是一个完整的 JSON 字符串。但前端可能只传回部分节点的数据，
 * 例如：{@code {"PH010R01.PH010RA1": 11, "PH010R02": "abc"}}，
 * 需要将其还原为：
 * <pre>{@code
 * {
 *   "PH010R01": {
 *     "PH010RA1": 11
 *   },
 *   "PH010R02": "abc"
 * }
 * }</pre>
 * 从而让表达式可以通过 {@code JsonPathUtil.read(inputData, '$.PH010R01.PH010RA1')} 正确读取值。</p>
 *
 * <h3>支持的路径格式</h3>
 * <ul>
 *   <li>简单路径：{@code "PH010R01"} → 顶层属性</li>
 *   <li>嵌套路径（点号分隔）：{@code "PH010R01.PH010RA1"} → 嵌套对象</li>
 *   <li>带 $. 前缀的路径：{@code "$.PH010R01.PH010RA1"} → 自动去除前缀</li>
 *   <li>数组索引路径：{@code "PH010R01[0].PH010RA1"} → 数组中的元素</li>
 * </ul>
 *
 * <h3>示例</h3>
 * <pre>{@code
 * Map<String, Object> flatData = new LinkedHashMap<>();
 * flatData.put("PH010R01[0].PH010RA1", 11);
 * flatData.put("PH010R01[0].PH010RB1", "abc");
 * flatData.put("PH010R01[1].PH010RA1", 112);
 * flatData.put("PH010R02", "abc");
 *
 * String json = InputDataBuilder.buildJson(flatData);
 * // 生成的 JSON:
 * // {
 * //   "PH010R01": [
 * //     {"PH010RA1": 11, "PH010RB1": "abc"},
 * //     {"PH010RA1": 112}
 * //   ],
 * //   "PH010R02": "abc"
 * // }
 * }</pre>
 *
 * @author businesslogic
 */
public class InputDataBuilder {

    private static final Logger logger = LoggerFactory.getLogger(InputDataBuilder.class);

    /**
     * 私有构造，防止实例化
     */
    private InputDataBuilder() {
    }

    /**
     * 将前端传回的扁平 key-value 数据构建为 Aviator 表达式可用的 JSON 字符串
     *
     * <p>key 支持以下格式：</p>
     * <ul>
     *   <li>简单 key：{@code "PH010R01"} → 生成顶层属性</li>
     *   <li>点号分隔的嵌套 key：{@code "A.B.C"} → 生成嵌套对象 {@code {"A":{"B":{"C":value}}}}</li>
     *   <li>带 {@code $.} 前缀的 key：{@code "$.A.B"} → 自动去除前缀，等同于 {@code "A.B"}</li>
     *   <li>含数组索引的 key：{@code "A[0].B"} → 生成数组 {@code {"A":[{"B":value}]}}</li>
     * </ul>
     *
     * @param flatData 前端传回的扁平数据，key 为路径，value 为对应的值
     * @return 构建好的 JSON 字符串，可直接作为 Aviator 表达式的 inputData 使用
     * @throws IllegalArgumentException 当 flatData 为 null 时抛出
     */
    public static String buildJson(Map<String, Object> flatData) {
        if (flatData == null) {
            throw new IllegalArgumentException("flatData 不能为 null");
        }

        if (flatData.isEmpty()) {
            return "{}";
        }

        // 使用 JSONObject 保持插入顺序（LinkedHashMap 底层）
        JSONObject root = new JSONObject(new LinkedHashMap<>());

        for (Map.Entry<String, Object> entry : flatData.entrySet()) {
            String rawPath = entry.getKey();
            Object value = entry.getValue();

            // 去除 $. 前缀
            String path = normalizePath(rawPath);

            if (path.isEmpty()) {
                logger.warn("路径为空，跳过该条目，原始路径：{}", rawPath);
                continue;
            }

            // 解析路径并设置值
            setNestedValue(root, path, value, rawPath);
        }

        String result = root.toJSONString();
        logger.info("构建 JSON 完成，输入条目数：{}，JSON 长度：{}", flatData.size(), result.length());
        return result;
    }

    /**
     * 将前端传回的扁平 key-value 数据构建为 JSONObject 对象
     *
     * @param flatData 前端传回的扁平数据，key 为路径，value 为对应的值
     * @return 构建好的 JSONObject
     * @throws IllegalArgumentException 当 flatData 为 null 时抛出
     */
    public static JSONObject buildJsonObject(Map<String, Object> flatData) {
        String json = buildJson(flatData);
        return JSON.parseObject(json);
    }

    /**
     * 将前端传回的表达式的参数列表构建为表达式可用的 inputData JSON 字符串
     *
     * <p>前端每个参数节点对应一个 {@link JsonPathParamDTO}，包含 jsonPath、value、jsonType 三个字段。</p>
     *
     * <p>支持的 jsonType：</p>
     * <ul>
     *   <li>{@code string} / {@code String}: 字符串类型，JSON 中值带引号</li>
     *   <li>{@code number} / {@code int} / {@code Integer} / {@code long} / {@code Long}: 整数类型</li>
     *   <li>{@code double} / {@code Double} / {@code float} / {@code Float}: 浮点数类型</li>
     *   <li>{@code boolean} / {@code Boolean}: 布尔类型，JSON 中值为 true/false</li>
     *   <li>{@code null} / {@code Null}: null 类型，JSON 中值为 null</li>
     *   <li>{@code object} / {@code Object} / {@code json} / {@code map}: 对象类型，
     *       value 为用户直接输入的 JSON 字符串（如 {@code "{\"name\":\"Tom\"}"}）</li>
     *   <li>{@code array} / {@code Array} / {@code list} / {@code List}: 数组类型，
     *       value 为用户直接输入的 JSON 字符串（如 {@code "[1,2,3]"}）</li>
     * </ul>
     *
     * <p>示例输入：</p>
     * <pre>{@code
     * [
     *   {"jsonPath": "$.amount", "value": "500", "jsonType": "number"},
     *   {"jsonPath": "$.user", "value": "{\"name\":\"Tom\",\"age\":30}", "jsonType": "object"},
     *   {"jsonPath": "$.tags", "value": "[\"a\",\"b\"]", "jsonType": "array"}
     * ]
     * }</pre>
     *
     * <p>生成的 inputData：</p>
     * <pre>{@code
     * {"amount":500,"user":{"name":"Tom","age":30},"tags":["a","b"]}
     * }</pre>
     *
     * <p>路径支持数组通配符 {@code [*]}：表示把后续路径应用到数组的每个元素。
     * 例如 {@code "A[*].B"} 会给 A 数组的所有元素设置 B 字段；如果数组为空，则先创建一个元素。</p>
     *
     * @param paramList 前端传回的表达式的参数列表（JsonPathParamDTO 对象）
     * @return 构建好的 JSON 字符串，可直接作为 inputData 传入表达式引擎执行
     * @throws IllegalArgumentException 当 paramList 为 null 时抛出
     */
    public static String buildInputDataJson(List<JsonPathParamDTO> paramList) {
        if (paramList == null) {
            throw new IllegalArgumentException("paramList 不能为 null");
        }

        if (paramList.isEmpty()) {
            return "{}";
        }

        // 使用 JSONObject 保持插入顺序（LinkedHashMap 底层）
        JSONObject root = new JSONObject(new LinkedHashMap<>());

        for (JsonPathParamDTO param : paramList) {
            String rawPath = param.getJsonPath();
            Object value = param.getValue();
            String type = param.getJsonType();

            if (rawPath == null || rawPath.trim().isEmpty()) {
                logger.warn("路径为空，跳过该条目");
                continue;
            }

            // 去除 $. 前缀
            String path = normalizePath(rawPath);

            if (path.isEmpty()) {
                logger.warn("路径为空，跳过该条目，原始路径：{}", rawPath);
                continue;
            }

            // 根据类型转换值
            Object convertedValue = convertValueByType(value, type);

            // 解析路径并设置值
            setNestedValue(root, path, convertedValue, rawPath);
        }

        String result = root.toJSONString();
        logger.info("构建 JSON 完成，输入条目数：{}，JSON 长度：{}", paramList.size(), result.length());
        return result;
    }

    /**
     * 根据类型字符串将字符串值转换为对应的 Java 对象
     *
     * <p>支持的类型映射：
     * <ul>
     *   <li>string / String → 保持原字符串</li>
     *   <li>number / Number / int / Integer / long / Long → 转换为整数（Integer 或 Long）</li>
     *   <li>double / Double / float / Float → 转换为浮点数（Double）</li>
     *   <li>boolean / Boolean → 转换为布尔值（Boolean）</li>
     *   <li>null / Null → 返回 null</li>
     *   <li>object / Object / json / map → 将 JSON 字符串解析为 JSONObject</li>
     *   <li>array / Array / list / List → 将 JSON 字符串解析为 JSONArray</li>
     * </ul>
     *
     * @param value 前端传回的值（可能是字符串，也可能是 JSON 反序列化后的对象）
     * @param type  类型字符串
     * @return 转换后的 Java 对象
     */
    private static Object convertValueByType(String valueStr, String type) {
        return convertValueByType((Object) valueStr, type);
    }

    /**
     * 根据类型字符串将值转换为对应的 Java 对象
     *
     * <p>支持的类型映射：
     * <ul>
     *   <li>string / String → 保持原字符串</li>
     *   <li>number / Number / int / Integer / long / Long → 转换为整数（Integer 或 Long）</li>
     *   <li>double / Double / float / Float → 转换为浮点数（Double）</li>
     *   <li>boolean / Boolean → 转换为布尔值（Boolean）</li>
     *   <li>null / Null → 返回 null</li>
     *   <li>object / Object / json / map → 将 JSON 字符串解析为 JSONObject</li>
     *   <li>array / Array / list / List → 将 JSON 字符串解析为 JSONArray</li>
     * </ul>
     *
     * @param value 前端传回的值（可能是字符串，也可能是 JSON 反序列化后的对象）
     * @param type  类型字符串
     * @return 转换后的 Java 对象
     */
    private static Object convertValueByType(Object value, String type) {
        if (type == null || type.trim().isEmpty()) {
            // 如果没有指定类型，保持原始值
            return value;
        }

        String typeLower = type.toLowerCase().trim();

        switch (typeLower) {
            case "string":
                // 字符串类型，直接返回
                return value;

            case "object":
            case "json":
            case "map":
                // 对象类型：前端直接输入的 JSON 字符串，解析为 JSONObject
                if (value == null) {
                    return null;
                }
                if (value instanceof Map) {
                    return value;
                }
                String objJson = value.toString().trim();
                if (objJson.isEmpty()) {
                    return null;
                }
                try {
                    return JSON.parseObject(objJson);
                } catch (Exception e) {
                    logger.warn("无法将值 '{}' 解析为对象类型，返回 null", objJson);
                    return null;
                }

            case "array":
            case "list":
                // 数组类型：前端直接输入的 JSON 字符串，解析为 JSONArray
                if (value == null) {
                    return null;
                }
                if (value instanceof List) {
                    return value;
                }
                String arrJson = value.toString().trim();
                if (arrJson.isEmpty()) {
                    return null;
                }
                try {
                    return JSON.parseArray(arrJson);
                } catch (Exception e) {
                    logger.warn("无法将值 '{}' 解析为数组类型，返回 null", arrJson);
                    return null;
                }

            case "number":
            case "int":
            case "integer":
                // 整数类型
                if (value == null) {
                    return null;
                }
                if (value instanceof Number) {
                    return ((Number) value).longValue();
                }
                String numStr = value.toString().trim();
                if (numStr.isEmpty()) {
                    return null;
                }
                try {
                    // 先尝试解析为 int
                    return Integer.parseInt(numStr);
                } catch (NumberFormatException e) {
                    try {
                        // 超出 int 范围，解析为 long
                        return Long.parseLong(numStr);
                    } catch (NumberFormatException e2) {
                        logger.warn("无法将值 '{}' 转换为整数类型，返回 null", numStr);
                        return null;
                    }
                }

            case "long":
                // 长整数类型
                if (value == null) {
                    return null;
                }
                if (value instanceof Number) {
                    return ((Number) value).longValue();
                }
                try {
                    return Long.parseLong(value.toString().trim());
                } catch (NumberFormatException e) {
                    logger.warn("无法将值 '{}' 转换为 long 类型，返回 null", value);
                    return null;
                }

            case "double":
            case "float":
                // 浮点数类型
                if (value == null) {
                    return null;
                }
                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                }
                try {
                    return Double.parseDouble(value.toString().trim());
                } catch (NumberFormatException e) {
                    logger.warn("无法将值 '{}' 转换为浮点数类型，返回 null", value);
                    return null;
                }

            case "boolean":
                // 布尔类型
                if (value == null) {
                    return null;
                }
                if (value instanceof Boolean) {
                    return value;
                }
                String boolStr = value.toString().trim().toLowerCase();
                if (boolStr.isEmpty()) {
                    return null;
                }
                if ("true".equals(boolStr) || "1".equals(boolStr) || "yes".equals(boolStr)) {
                    return true;
                } else if ("false".equals(boolStr) || "0".equals(boolStr) || "no".equals(boolStr)) {
                    return false;
                } else {
                    logger.warn("无法将值 '{}' 转换为布尔类型，返回 null", value);
                    return null;
                }

            case "null":
                // null 类型
                return null;

            default:
                // 未知类型，默认作为字符串处理
                logger.warn("未知的类型 '{}'，将值 '{}' 作为字符串处理", type, value);
                return value;
        }
    }

    /**
     * 去除路径中的 {@code $.} 前缀
     *
     * @param path 原始路径
     * @return 去除前缀后的路径
     */
    private static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        String normalized = path.trim();
        if (normalized.startsWith("$.")) {
            normalized = normalized.substring(2);
        } else if (normalized.startsWith("$")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    /**
     * 根据路径在 root 中设置嵌套值
     *
     * <p>路径支持点号分隔的嵌套结构和数组索引。
     * 例如 {@code "A[0].B"} 会被解析为：A 是一个数组，取第 0 个元素，设置其 B 属性。</p>
     *
     * @param root      根 JSONObject
     * @param path      归一化后的路径（已去除 $. 前缀）
     * @param value     要设置的值
     * @param rawPath   原始路径（用于日志）
     */
    private static void setNestedValue(JSONObject root, String path, Object value, String rawPath) {
        // 将路径按 '.' 分割，但需要保留数组索引信息
        // 例如 "A[0].B" → ["A[0]", "B"]
        String[] segments = path.split("\\.");

        setNestedValue(root, segments, 0, value, rawPath);
    }

    /**
     * 递归设置嵌套值，支持数组通配符 {@code [*]}。
     *
     * <p>通配符语义：把后续路径应用到目标数组的每一个元素；如果数组为空，则先创建一个元素承载值。</p>
     */
    private static void setNestedValue(Object current, String[] segments, int index,
                                       Object value, String rawPath) {
        if (index >= segments.length) {
            return;
        }

        String segment = segments[index];
        boolean isLast = (index == segments.length - 1);

        // 处理数组通配符段，例如 "A[*]"
        if (segment.contains("[*]")) {
            handleWildcardSegment(current, segment, segments, index, isLast, value, rawPath);
            return;
        }

        // 处理数字索引的数组段，例如 "A[0]" 或 "A[0][1]"
        if (segment.contains("[")) {
            Object next = handleArraySegment(current, segment, isLast, value, rawPath);
            if (!isLast) {
                setNestedValue(next, segments, index + 1, value, rawPath);
            }
            return;
        }

        // 普通对象属性
        JSONObject jsonObj;
        if (current instanceof JSONObject) {
            jsonObj = (JSONObject) current;
        } else if (current instanceof JSONArray) {
            // 当前对象是数组，说明上层通配符遍历到的元素本身就是数组：
            // 把当前段当作数组元素下标（0）继续向下设置，保证路径仍能落地。
            JSONArray currentArray = (JSONArray) current;
            int targetIndex = 0;
            if (!segment.isEmpty() && segment.chars().allMatch(Character::isDigit)) {
                targetIndex = Integer.parseInt(segment);
            }
            while (currentArray.size() <= targetIndex) {
                currentArray.add(null);
            }
            Object element = currentArray.get(targetIndex);
            if (isLast) {
                currentArray.set(targetIndex, value);
                return;
            }
            if (element == null) {
                element = new JSONObject(new LinkedHashMap<>());
                currentArray.set(targetIndex, element);
            }
            setNestedValue(element, segments, index + 1, value, rawPath);
            return;
        } else {
            logger.warn("无法在非对象/数组的节点上设置子路径：{}", rawPath);
            return;
        }

        if (isLast) {
            jsonObj.put(segment, value);
            return;
        }

        Object child = jsonObj.get(segment);
        if (child == null) {
            // 当前段始终先按对象处理；下一段若是数组索引/通配符，
            // 会由 handleArraySegment / handleWildcardSegment 在对象内创建数组字段。
            child = new JSONObject(new LinkedHashMap<>());
            jsonObj.put(segment, child);
        }
        setNestedValue(child, segments, index + 1, value, rawPath);
    }

    /**
     * 处理带 {@code [*]} 通配符的数组段。
     */
    private static void handleWildcardSegment(Object current, String segment, String[] segments,
                                              int index, boolean isLast, Object value, String rawPath) {
        int bracketPos = segment.indexOf('[');
        String key = bracketPos > 0 ? segment.substring(0, bracketPos) : null;
        String indexPart = segment.substring(bracketPos);

        JSONArray array;
        if (key == null || key.isEmpty()) {
            // 当前对象本身就是数组：A[*].B
            if (current instanceof JSONArray) {
                array = (JSONArray) current;
            } else {
                logger.warn("通配符路径的当前节点不是数组，跳过：{}", rawPath);
                return;
            }
        } else {
            if (!(current instanceof JSONObject)) {
                logger.warn("通配符路径的父节点不是对象，跳过：{}", rawPath);
                return;
            }
            JSONObject parentObj = (JSONObject) current;
            Object arrayObj = parentObj.get(key);
            if (arrayObj == null) {
                array = new JSONArray();
                parentObj.put(key, array);
            } else if (arrayObj instanceof JSONArray) {
                array = (JSONArray) arrayObj;
            } else {
                logger.warn("通配符路径的目标不是数组，跳过：{}", rawPath);
                return;
            }
        }

        boolean hasRemaining = index + 1 < segments.length;

        // 通配符是最后一段：直接把值设置到数组的所有元素
        if (isLast || !hasRemaining) {
            if (array.isEmpty()) {
                array.add(value);
            } else {
                for (int i = 0; i < array.size(); i++) {
                    array.set(i, value);
                }
            }
            return;
        }

        // 还有更深层路径：对数组每个元素递归设置剩余路径
        if (array.isEmpty()) {
            array.add(new JSONObject(new LinkedHashMap<>()));
        }
        for (int i = 0; i < array.size(); i++) {
            Object element = array.get(i);
            if (element == null) {
                element = new JSONObject(new LinkedHashMap<>());
                array.set(i, element);
            }
            setNestedValue(element, segments, index + 1, value, rawPath);
        }
    }

    /**
     * 处理包含数组索引的路径段
     *
     * <p>支持格式如 {@code "A[0]"}、{@code "A[0][1]"} 等。
     * 会自动创建 JSONArray 和其中的 JSONObject（如果不存在）。</p>
     *
     * @param current  当前层级的对象（JSONObject 或 JSONArray）
     * @param segment  当前路径段，例如 "A[0]"
     * @param isLast   是否是路径的最后一段
     * @param value    最终要设置的值
     * @param rawPath  原始路径（用于日志）
     * @return 处理后的当前对象（用于下一层级的遍历）
     */
    private static Object handleArraySegment(Object current, String segment, boolean isLast, Object value, String rawPath) {
        // 解析 "A[0]" → key="A", indices=[0]
        // 解析 "A[0][1]" → key="A", indices=[0, 1]
        String key = segment.substring(0, segment.indexOf('['));
        String indexPart = segment.substring(segment.indexOf('['));

        // 提取所有数组索引
        List<Integer> indices = parseIndices(indexPart);

        // 获取或创建 JSONArray
        JSONObject parentObj = (JSONObject) current;
        Object arrayObj = parentObj.get(key);
        JSONArray array;
        if (arrayObj == null) {
            array = new JSONArray();
            parentObj.put(key, array);
        } else {
            array = (JSONArray) arrayObj;
        }

        // 按照索引逐级深入
        Object arrayCurrent = array;
        for (int j = 0; j < indices.size(); j++) {
            int idx = indices.get(j);
            boolean isLastIndex = (j == indices.size() - 1);

            JSONArray currentArray = (JSONArray) arrayCurrent;

            // 确保数组有足够的长度
            while (currentArray.size() <= idx) {
                currentArray.add(null);
            }

            if (isLast && isLastIndex) {
                // 最后一个索引，设置值
                currentArray.set(idx, value);
            } else if (isLastIndex) {
                // 不是最后一个索引，但已经是最后的数组下标了，下一层是对象属性
                Object next = currentArray.get(idx);
                if (next == null) {
                    JSONObject newObj = new JSONObject(new LinkedHashMap<>());
                    currentArray.set(idx, newObj);
                    arrayCurrent = newObj;
                } else {
                    arrayCurrent = next;
                }
            } else {
                // 还有更深层的数组索引，需要创建 JSONArray
                Object next = currentArray.get(idx);
                if (next == null) {
                    // 判断下一级是否还是数组索引
                    // 如果还有更多索引，创建 JSONArray；否则创建 JSONObject
                    JSONArray newArray = new JSONArray();
                    currentArray.set(idx, newArray);
                    arrayCurrent = newArray;
                } else {
                    arrayCurrent = next;
                }
            }
        }

        return arrayCurrent;
    }

    /**
     * 从索引字符串中解析出所有数组索引
     *
     * <p>例如 {@code "[0][1][2]"} → [0, 1, 2]</p>
     *
     * @param indexPart 索引部分字符串，如 "[0]" 或 "[0][1]"
     * @return 索引列表
     */
    private static List<Integer> parseIndices(String indexPart) {
        List<Integer> indices = new java.util.ArrayList<>();
        int pos = 0;
        while (pos < indexPart.length()) {
            int start = indexPart.indexOf('[', pos);
            if (start == -1) {
                break;
            }
            int end = indexPart.indexOf(']', start);
            if (end == -1) {
                logger.warn("数组索引格式错误，缺少 ']'：{}", indexPart);
                break;
            }
            String numStr = indexPart.substring(start + 1, end);
            try {
                indices.add(Integer.parseInt(numStr));
            } catch (NumberFormatException e) {
                logger.warn("数组索引不是有效数字：{}", numStr);
            }
            pos = end + 1;
        }
        return indices;
    }
}
