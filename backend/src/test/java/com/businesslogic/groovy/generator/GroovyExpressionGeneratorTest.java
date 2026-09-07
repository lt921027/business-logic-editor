package com.businesslogic.groovy.generator;

import com.businesslogic.dto.BusinessLogicSaveDTO;
import com.businesslogic.dto.FeatureConfigDTO;
import com.businesslogic.dto.LogicStepDTO;
import com.businesslogic.groovy.engine.CompiledGroovyScript;
import com.businesslogic.groovy.engine.GroovyExecutor;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GroovyExpressionGenerator 合并方法的单元测试。
 *
 * <p>验证：合并后的交易码级脚本能正确剥离单特征顶层 return、
 * 生成统一调度段、通过沙箱编译并返回 Map&lt;featureCode, value&gt;。</p>
 */
public class GroovyExpressionGeneratorTest {

    private final GroovyExpressionGenerator generator = new GroovyExpressionGenerator();

    private static String featureScript(String methodName, String tryBody) {
        return "def " + methodName + "() {\n"
                + "    def result = BigDecimal.valueOf(-99999);\n"
                + "    try {\n"
                + tryBody
                + "    } catch (Exception e) {\n"
                + "        return result;\n"
                + "    }\n"
                + "    return result;\n"
                + "}\n"
                + "return " + methodName + "()";
    }

    private static FeatureConfigDTO featureConfig(String featureCode, String expression) {
        FeatureConfigDTO dto = new FeatureConfigDTO();
        dto.setFeatureCode(featureCode);
        dto.setRunExpress(expression);
        return dto;
    }

    private static FeatureConfigDTO featureConfig(String featureCode, String expression,
                                                  String defaultValue, String dataType) {
        FeatureConfigDTO dto = featureConfig(featureCode, expression);
        dto.setFeatureDefaultValue(defaultValue);
        dto.setFeatureDataType(dataType);
        return dto;
    }

    @Test
    public void testMergeFeatureExpressions_generatesRunnableScript() throws Exception {
        String feature1 = featureScript("loanAcctAeApenAtToMons",
                "        def step1 = JsonPathUtil.read(inputData, '$.root.PA01.PA01A');\n"
                        + "        result = step1;\n");

        String feature2 = featureScript("loanAcctStatus",
                "        def step1 = JsonPathUtil.read(inputData, '$.root.PA02.PA02A');\n"
                        + "        def step2 = JsonPathUtil.read(inputData, '$.root.PA02.PA02B');\n"
                        + "        result = (step1 + step2);\n");

        String merged = generator.mergeFeatureExpressions(Arrays.asList(
                featureConfig("loanAcctAeApenAtToMons", feature1),
                featureConfig("loanAcctStatus", feature2)), null);

        // 脚本头元数据 + 每个特征的方法定义原样保留，且单特征顶层 return 被剥离
        assertTrue(merged.startsWith("// ============================================================"));
        assertTrue(merged.contains("// 特征数量: 2"));
        assertTrue(merged.contains("// ===== 特征: loanAcctAeApenAtToMons ====="));
        assertTrue(merged.contains("// ===== 特征: loanAcctStatus ====="));
        assertTrue(merged.contains("def loanAcctAeApenAtToMons() {"));
        assertTrue(merged.contains("def loanAcctStatus() {"));
        assertFalse(merged.contains("return loanAcctAeApenAtToMons()\nreturn"));
        assertFalse(merged.contains("return loanAcctStatus()\nreturn"));

        // 统一调度段：按特征编码调用方法并放入 Map
        assertTrue(merged.contains("return [\n"
                + "    'loanAcctAeApenAtToMons': loanAcctAeApenAtToMons(),\n"
                + "    'loanAcctStatus': loanAcctStatus(),\n"
                + "]"));

        // 编译并执行，验证返回 Map 结果
        CompiledGroovyScript compiled = GroovyExecutor.compile(merged);
        Map<?, ?> result = (Map<?, ?>) GroovyExecutor.execute(compiled,
                "{\"root\":{\"PA01\":{\"PA01A\":123},\"PA02\":{\"PA02A\":100,\"PA02B\":200}}}");

        assertEquals(2, result.size());
        assertEquals(123, result.get("loanAcctAeApenAtToMons"));
        assertEquals(300, result.get("loanAcctStatus"));
    }

    @Test
    public void testMergeFeatureExpressions_withMeta_includesHeader() {
        String feature1 = featureScript("loanAcctAeApenAtToMons",
                "        def step1 = JsonPathUtil.read(inputData, '$.root.PA01.PA01A');\n"
                        + "        result = step1;\n");
        String feature2 = featureScript("loanAcctStatus",
                "        def step1 = JsonPathUtil.read(inputData, '$.root.PA02.PA02A');\n"
                        + "        result = step1;\n");

        String merged = generator.mergeFeatureExpressions(Arrays.asList(
                featureConfig("loanAcctAeApenAtToMons", feature1),
                featureConfig("loanAcctStatus", feature2)),
                new GroovyExpressionGenerator.MergeMeta(
                        "LOAN_APPROVE", 12L, LocalDateTime.of(2026, 8, 26, 17, 0, 0)));

        assertTrue(merged.contains("// 交易码: LOAN_APPROVE"));
        assertTrue(merged.contains("// 发布版本号: 12"));
        assertTrue(merged.contains("// 发布时间: 2026-08-26 17:00:00"));
        assertTrue(merged.contains("// 特征数量: 2"));
    }

    @Test
    public void testMergeFeatureExpressions_emptyList() {
        assertEquals("return [:]", generator.mergeFeatureExpressions(Collections.emptyList(), null));
        assertEquals("return [:]", generator.mergeFeatureExpressions(null, null));
    }

    @Test
    public void testMergeFeatureExpressions_duplicateMethodName_throws() {
        String feature1 = featureScript("sameName",
                "        def step1 = 1;\n        result = step1;\n");
        String feature2 = featureScript("sameName",
                "        def step1 = 2;\n        result = step1;\n");

        assertThrows(IllegalArgumentException.class, () -> generator.mergeFeatureExpressions(Arrays.asList(
                featureConfig("a", feature1),
                featureConfig("b", feature2)), null));
    }

    @Test
    public void testMergeFeatureExpressions_toleratesTrailingNoiseAndSpecialBraces() throws Exception {
        // 带分号、尾部注释、方法体内含闭包花括号和字符串花括号
        String noisy = "def noisyFeature() {\n"
                + "    def result = BigDecimal.valueOf(-99999);\n"
                + "    try {\n"
                + "        def step1 = [1, 2, 3].inject(0) { x, y -> x + y };\n"
                + "        def step2 = 'a{b}c';\n"
                + "        result = step1;\n"
                + "    } catch (Exception e) {\n"
                + "        return result;\n"
                + "    }\n"
                + "    return result;\n"
                + "}\n"
                + "return noisyFeature(); // 手工加的调用\n"
                + "// 后面还有手工维护的说明文字";

        String merged = generator.mergeFeatureExpressions(Collections.singletonList(
                featureConfig("noisyFeature", noisy)), null);

        // 方法块完整保留，尾随的 return 调用和注释被整体丢弃
        assertTrue(merged.contains("def step1 = [1, 2, 3].inject(0) { x, y -> x + y };"));
        assertTrue(merged.contains("def step2 = 'a{b}c';"));
        assertFalse(merged.contains("return noisyFeature();"));
        assertFalse(merged.contains("手工维护"));
        assertTrue(merged.contains("'noisyFeature': noisyFeature()"));

        Map<?, ?> result = (Map<?, ?>) GroovyExecutor.execute(
                GroovyExecutor.compile(merged), "{}");
        assertEquals(6, result.get("noisyFeature"));
    }

    @Test
    public void testMergeFeatureExpressions_brokenMethod_throws() {
        String broken = "def brokenFeature() {\n"
                + "    def result = 1;\n"; // 缺少闭合 }

        assertThrows(IllegalArgumentException.class, () -> generator.mergeFeatureExpressions(
                Collections.singletonList(
                        featureConfig("brokenFeature", broken)), null));
    }

    @Test
    public void testMergeFeatureExpressions_addAndRemoveFeatures() {
        List<FeatureConfigDTO> base = new ArrayList<>(Arrays.asList(
                featureConfig("loanAcctAeApenAtToMons",
                        featureScript("loanAcctAeApenAtToMons",
                                "        def step1 = JsonPathUtil.read(inputData, '$.root.PA01.PA01A');\n"
                                        + "        result = step1;\n")),
                featureConfig("loanAcctStatus",
                        featureScript("loanAcctStatus",
                                "        def step1 = JsonPathUtil.read(inputData, '$.root.PA02.PA02A');\n"
                                        + "        result = step1;\n"))));

        String mergedBase = generator.mergeFeatureExpressions(base, null);
        assertFalse(mergedBase.contains("newFeature"));
        assertTrue(mergedBase.contains("// 特征数量: 2"));

        // 新增一个特征
        List<FeatureConfigDTO> added = new ArrayList<>(base);
        added.add(featureConfig("newFeature",
                featureScript("newFeature",
                        "        def step1 = 5;\n        result = step1;\n")));
        String mergedAdded = generator.mergeFeatureExpressions(added, null);

        assertTrue(mergedAdded.contains("def newFeature() {"));
        assertTrue(mergedAdded.contains("'newFeature': newFeature()"));
        assertTrue(mergedAdded.contains("// ===== 特征: newFeature ====="));
        assertTrue(mergedAdded.contains("// 特征数量: 3"));

        // 删除特征：回到原列表，脚本里不再出现 newFeature
        String mergedRemoved = generator.mergeFeatureExpressions(base, null);
        assertFalse(mergedRemoved.contains("newFeature"));
        assertTrue(mergedRemoved.contains("// 特征数量: 2"));
    }

    @Test
    public void testMergeFeatureExpressions_customDefaultAndReturnType() {
        String feature = featureScript("customFeature",
                "        def step1 = 1;\n        result = step1;\n");

        String merged = generator.mergeFeatureExpressions(Collections.singletonList(
                featureConfig("customFeature", feature, "0", "Integer")), null);

        assertTrue(merged.contains("// ===== 特征: customFeature ====="));
        assertTrue(merged.contains("// 特征名称: -"));
        assertTrue(merged.contains("// 特征数据类型: Integer"));
    }

    /**
     * 验证特征注释块包含特征名称、特征数据类型、版本号、特征描述；
     * 缺失字段显示为 "-"。
     */
    @Test
    public void testMergeFeatureExpressions_featureMetaComments() {
        String feature = featureScript("metaFeature",
                "        def step1 = 1;\n        result = step1;\n");

        FeatureConfigDTO dto = featureConfig("metaFeature", feature);
        dto.setFeatureName("金额校验");
        dto.setFeatureDataType("BigDecimal");
        dto.setVersion("v3");
        dto.setFeatureLogicDesc("校验金额是否大于 0");

        String merged = generator.mergeFeatureExpressions(
                Collections.singletonList(dto), null);

        assertTrue(merged.contains("// ===== 特征: metaFeature ====="));
        assertTrue(merged.contains("// 特征名称: 金额校验"));
        assertTrue(merged.contains("// 特征数据类型: BigDecimal"));
        assertTrue(merged.contains("// 版本号: v3"));
        assertTrue(merged.contains("// 特征描述: 校验金额是否大于 0"));
    }

    /**
     * 验证特征元数据缺失时注释中显示 "-"。
     */
    @Test
    public void testMergeFeatureExpressions_featureMetaMissingShowsDash() {
        String feature = featureScript("bareFeature",
                "        def step1 = 1;\n        result = step1;\n");

        String merged = generator.mergeFeatureExpressions(
                Collections.singletonList(featureConfig("bareFeature", feature)), null);

        assertTrue(merged.contains("// 特征名称: -"));
        assertTrue(merged.contains("// 特征数据类型: -"));
        assertTrue(merged.contains("// 版本号: -"));
        assertTrue(merged.contains("// 特征描述: -"));
    }

    @Test
    public void testMergeFeatureExpressions_exceedsEngineLengthLimit_throws() {
        // 单个特征表达式超过引擎 maxScriptLength（65536），合并时直接报错
        String longLiteral = String.join("", Collections.nCopies(70000, "a"));
        String hugeFeature = "def hugeFeature() {\n"
                + "    def result = BigDecimal.valueOf(-99999);\n"
                + "    try {\n"
                + "        def step1 = '" + longLiteral + "';\n"
                + "        result = step1;\n"
                + "    } catch (Exception e) {\n"
                + "        return result;\n"
                + "    }\n"
                + "    return result;\n"
                + "}\n"
                + "return hugeFeature()";

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> generator.mergeFeatureExpressions(Collections.singletonList(
                        featureConfig("hugeFeature", hugeFeature)), null));

        assertTrue(ex.getMessage().contains("长度超限"));
        assertTrue(ex.getMessage().contains("超限特征: hugeFeature"));
    }

    @Test
    public void testGenerate_usesConfiguredDefaultValue() {
        LogicStepDTO step = new LogicStepDTO();
        step.setFunctionCategory("direct");
        step.setMappedField("amount");
        step.setOutputVar("step1");

        BusinessLogicSaveDTO dto = new BusinessLogicSaveDTO();
        dto.setName("loanAcctAeApenAtToMons");
        dto.setDefaultValue("-1");
        dto.setReturnType("BigDecimal");
        dto.setLogicSteps(Collections.singletonList(step));

        String script = generator.generate(dto);
        assertTrue(script.contains("def result = BigDecimal.valueOf(-1);"));
    }

    @Test
    public void testGenerate_emptyDefaultValue_throws() {
        LogicStepDTO step = new LogicStepDTO();
        step.setFunctionCategory("direct");
        step.setMappedField("amount");
        step.setOutputVar("step1");

        BusinessLogicSaveDTO dto = new BusinessLogicSaveDTO();
        dto.setName("stringFeature");
        dto.setReturnType("String");
        dto.setLogicSteps(Collections.singletonList(step));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> generator.generate(dto));
        assertTrue(ex.getMessage().contains("默认值不能为空"));
    }

    @Test
    public void testGenerate_defaultValueMismatchesReturnType_throws() {
        LogicStepDTO step = new LogicStepDTO();
        step.setFunctionCategory("direct");
        step.setMappedField("amount");
        step.setOutputVar("step1");

        BusinessLogicSaveDTO dto = new BusinessLogicSaveDTO();
        dto.setName("mismatchFeature");
        dto.setDefaultValue("''");
        dto.setReturnType("BigDecimal");
        dto.setLogicSteps(Collections.singletonList(step));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> generator.generate(dto));
        assertTrue(ex.getMessage().contains("与返回值类型"));
    }

    @Test
    public void testGenerate_wrapsRawStringDefaultValue() {
        LogicStepDTO step = new LogicStepDTO();
        step.setFunctionCategory("direct");
        step.setMappedField("amount");
        step.setOutputVar("step1");

        BusinessLogicSaveDTO dto = new BusinessLogicSaveDTO();
        dto.setName("stringFeature");
        dto.setDefaultValue("abc");
        dto.setReturnType("String");
        dto.setLogicSteps(Collections.singletonList(step));

        String script = generator.generate(dto);
        assertTrue(script.contains("def result = 'abc';"));
    }

    /**
     * 最后一步结果为 null 时不覆盖默认值；结果非 null 时才赋值给 result。
     */
    @Test
    public void testGenerate_onlyAssignsResultWhenLastStepValueNotNull() throws Exception {
        LogicStepDTO step = new LogicStepDTO();
        step.setFunctionCategory("direct");
        step.setMappedField("amount");
        step.setOutputVar("step1");

        BusinessLogicSaveDTO dto = new BusinessLogicSaveDTO();
        dto.setName("nullGuardFeature");
        dto.setDefaultValue("-1");
        dto.setReturnType("BigDecimal");
        dto.setLogicSteps(Collections.singletonList(step));

        String script = generator.generate(dto);
        assertTrue(script.contains("if (step1 != null) {"));
        assertTrue(script.contains("result = step1;"));

        // 字段缺失导致 step1 为 null：保持默认值 -1
        Object nullResult = GroovyExecutor.execute(script, "{}");
        assertEquals(-1, ((Number) nullResult).intValue());

        // 字段存在时 step1 非 null：使用步骤结果 42
        Object valueResult = GroovyExecutor.execute(script, "{\"amount\": 42}");
        assertEquals(42, ((Number) valueResult).intValue());
    }

    /**
     * 完整示例：展示 mergeFeatureExpressions 合并后的交易码级脚本结构。
     * 仅用于演示与人工查看输出，不校验具体断言。
     */
    @Test
    public void demoMergeFeatureExpressions_fullOutput() {
        // 特征 1：贷款发放日期归集（金额校验）
        FeatureConfigDTO feature1 = featureConfig("loanAcctAeApenAtToMons",
                featureScript("loanAcctAeApenAtToMons",
                        "        def step1 = JsonPathUtil.read(inputData, '$.root.PA01.PA01A');\n"
                                + "        result = step1;\n"),
                "BigDecimal.valueOf(-99999)", "BigDecimal");
        feature1.setFeatureName("贷款发放日期归集");
        feature1.setVersion("v1");
        feature1.setFeatureLogicDesc("取报文 root.PA01.PA01A 作为发放日期归集结果");

        // 特征 2：账户状态汇总（相加）
        FeatureConfigDTO feature2 = featureConfig("loanAcctStatus",
                featureScript("loanAcctStatus",
                        "        def step1 = JsonPathUtil.read(inputData, '$.root.PA02.PA02A');\n"
                                + "        def step2 = JsonPathUtil.read(inputData, '$.root.PA02.PA02B');\n"
                                + "        result = (step1 + step2);\n"),
                "BigDecimal.valueOf(-99999)", "BigDecimal");
        feature2.setFeatureName("账户状态汇总");
        feature2.setVersion("v2");
        feature2.setFeatureLogicDesc("对 PA02A 与 PA02B 求和，缺字段时返回默认值");

        GroovyExpressionGenerator.MergeMeta meta = new GroovyExpressionGenerator.MergeMeta(
                "LOAN_APPROVE", 12L, LocalDateTime.of(2026, 8, 31, 10, 30, 0));

        String merged = generator.mergeFeatureExpressions(
                Arrays.asList(feature1, feature2), meta);
        System.out.println("==================== 合并后的交易码级脚本 ====================");
        System.out.println(merged);
        System.out.println("============================================================");
    }

    /**
     * 从合并脚本中删除指定特征（按方法名），其余特征保持不变，调度段与特征数量同步更新。
     */
    @Test
    public void testRemoveFeature_keepsOtherFeaturesAndFixesDispatch() throws Exception {
        FeatureConfigDTO feature1 = featureConfig("loanAcctAeApenAtToMons",
                featureScript("loanAcctAeApenAtToMons",
                        "        def step1 = JsonPathUtil.read(inputData, '$.root.PA01.PA01A');\n"
                                + "        result = step1;\n"));
        feature1.setFeatureName("贷款发放日期归集");
        feature1.setFeatureDataType("BigDecimal");
        feature1.setVersion("v1");
        feature1.setFeatureLogicDesc("发放日期归集");

        FeatureConfigDTO feature2 = featureConfig("loanAcctStatus",
                featureScript("loanAcctStatus",
                        "        def step1 = JsonPathUtil.read(inputData, '$.root.PA02.PA02A');\n"
                                + "        result = step1;\n"));
        feature2.setFeatureName("账户状态汇总");
        feature2.setFeatureDataType("BigDecimal");
        feature2.setVersion("v2");
        feature2.setFeatureLogicDesc("账户状态汇总");

        String merged = generator.mergeFeatureExpressions(
                Arrays.asList(feature1, feature2), null);

        String removed = generator.removeFeature(merged, Collections.singletonList("loanAcctStatus"));

        // 被删特征整体消失：注释块、方法体、调度行、特征数量
        assertFalse(removed.contains("// ===== 特征: loanAcctStatus ====="));
        assertFalse(removed.contains("// 特征名称: 账户状态汇总"));
        assertFalse(removed.contains("def loanAcctStatus()"));
        assertFalse(removed.contains("'loanAcctStatus': loanAcctStatus(),"));
        assertTrue(removed.contains("// 特征数量: 1"));

        // 保留特征完整不变
        assertTrue(removed.contains("// ===== 特征: loanAcctAeApenAtToMons ====="));
        assertTrue(removed.contains("// 特征名称: 贷款发放日期归集"));
        assertTrue(removed.contains("def loanAcctAeApenAtToMons()"));
        assertTrue(removed.contains("'loanAcctAeApenAtToMons': loanAcctAeApenAtToMons(),"));

        // 删除后仍可编译执行，只剩保留的特征
        CompiledGroovyScript compiled = GroovyExecutor.compile(removed);
        Map<?, ?> result = (Map<?, ?>) GroovyExecutor.execute(compiled,
                "{\"root\":{\"PA01\":{\"PA01A\":123},\"PA02\":{\"PA02A\":100}}}");
        assertEquals(1, result.size());
        assertEquals(123, result.get("loanAcctAeApenAtToMons"));
    }

    /**
     * 删除不存在的特征方法名时抛出异常。
     */
    @Test
    public void testRemoveFeature_unknownMethod_throws() {
        FeatureConfigDTO feature1 = featureConfig("loanAcctAeApenAtToMons",
                featureScript("loanAcctAeApenAtToMons",
                        "        def step1 = 1;\n        result = step1;\n"));
        String merged = generator.mergeFeatureExpressions(
                Collections.singletonList(feature1), null);

        assertThrows(IllegalArgumentException.class,
                () -> generator.removeFeature(merged, Collections.singletonList("NOT_EXIST")));
    }

    /**
     * 删除最后一个特征后，脚本退化为空结果 return [:]。
     */
    @Test
    public void testRemoveFeature_lastFeature_returnsEmptyMap() {
        FeatureConfigDTO feature1 = featureConfig("onlyFeature",
                featureScript("onlyFeature",
                        "        def step1 = 1;\n        result = step1;\n"));
        String merged = generator.mergeFeatureExpressions(
                Collections.singletonList(feature1), null);

        String removed = generator.removeFeature(merged, Collections.singletonList("onlyFeature"));
        System.out.println("===== 删除唯一特征后的脚本 =====\n" + removed + "\n=====");
        assertFalse(removed.contains("def onlyFeature()"));
        assertFalse(removed.contains("'onlyFeature'"));
        assertTrue(removed.trim().endsWith("return [:]"));
    }

    /**
     * 演示：三个特征的合并脚本，删除中间特征，展示删除前后的完整脚本。
     */
    @Test
    public void demoRemoveFeature_middleFeature() {
        FeatureConfigDTO feature1 = featureConfig("FEAT0001",
                featureScript("featLoanAmt",
                        "        def amount = JsonPathUtil.readInt(inputData, '$.loan.amount');\n"
                                + "        result = amount > 0 ? amount : BigDecimal.valueOf(-99999);\n"));
        feature1.setFeatureName("贷款金额校验");
        feature1.setFeatureDataType("BigDecimal");
        feature1.setVersion("v1");
        feature1.setFeatureLogicDesc("校验贷款金额大于 0");

        FeatureConfigDTO feature2 = featureConfig("FEAT0002",
                featureScript("featCustLevel",
                        "        def level = JsonPathUtil.readString(inputData, '$.customer.level');\n"
                                + "        result = level == 'GOLD' ? BigDecimal.valueOf(1) : BigDecimal.valueOf(0);\n"));
        feature2.setFeatureName("客户等级判断");
        feature2.setFeatureDataType("BigDecimal");
        feature2.setVersion("v2");
        feature2.setFeatureLogicDesc("客户为 GOLD 等级时返回 1");

        FeatureConfigDTO feature3 = featureConfig("FEAT0003",
                featureScript("featRiskFlag",
                        "        def risk = JsonPathUtil.readString(inputData, '$.risk.flag');\n"
                                + "        result = risk == 'Y' ? BigDecimal.valueOf(1) : BigDecimal.valueOf(0);\n"));
        feature3.setFeatureName("风险标记判断");
        feature3.setFeatureDataType("BigDecimal");
        feature3.setVersion("v3");
        feature3.setFeatureLogicDesc("风险标记为 Y 时返回 1");

        GroovyExpressionGenerator.MergeMeta meta = new GroovyExpressionGenerator.MergeMeta(
                "LOAN_RISK", 5L, LocalDateTime.of(2026, 9, 3, 14, 30, 0));

        String merged = generator.mergeFeatureExpressions(
                Arrays.asList(feature1, feature2, feature3), meta);

        System.out.println("######################## 删除前 ########################");
        System.out.println(merged);
        System.out.println("######################## 执行删除特征编码: FEAT0002 ########################");

        String removed = generator.removeFeature(merged, Collections.singletonList("FEAT0002"));

        System.out.println("######################## 删除后 ########################");
        System.out.println(removed);
        System.out.println("######################## 结束 ########################");
    }

    /**
     * 特征编码与方法名不一致时，按特征编码删除仍能正确定位并移除整个特征块。
     */
    @Test
    public void testRemoveFeature_byFeatureCode_whenMethodNameDiffers() {
        FeatureConfigDTO feature1 = featureConfig("FEAT0001",
                featureScript("featLoanAmt",
                        "        def step1 = 1;\n        result = step1;\n"));
        FeatureConfigDTO feature2 = featureConfig("FEAT0002",
                featureScript("featCustLevel",
                        "        def step1 = 2;\n        result = step1;\n"));

        String merged = generator.mergeFeatureExpressions(
                Arrays.asList(feature1, feature2), null);

        String removed = generator.removeFeature(merged, Collections.singletonList("FEAT0002"));

        assertFalse(removed.contains("// ===== 特征: FEAT0002 ====="));
        assertFalse(removed.contains("def featCustLevel()"));
        assertFalse(removed.contains("'FEAT0002': featCustLevel(),"));
        assertTrue(removed.contains("// 特征数量: 1"));
        assertTrue(removed.contains("def featLoanAmt()"));
        assertTrue(removed.contains("'FEAT0001': featLoanAmt(),"));
    }

    /**
     * 批量删除多个特征：一次删除 FEAT0002、FEAT0003，保留 FEAT0001。
     */
    @Test
    public void testRemoveFeature_batchRemoves() {
        FeatureConfigDTO feature1 = featureConfig("FEAT0001",
                featureScript("featLoanAmt",
                        "        def step1 = 1;\n        result = step1;\n"));
        FeatureConfigDTO feature2 = featureConfig("FEAT0002",
                featureScript("featCustLevel",
                        "        def step1 = 2;\n        result = step1;\n"));
        FeatureConfigDTO feature3 = featureConfig("FEAT0003",
                featureScript("featRiskFlag",
                        "        def step1 = 3;\n        result = step1;\n"));

        String merged = generator.mergeFeatureExpressions(
                Arrays.asList(feature1, feature2, feature3), null);

        String removed = generator.removeFeature(
                merged, Arrays.asList("FEAT0002", "FEAT0003"));

        assertTrue(removed.contains("// 特征数量: 1"));
        assertTrue(removed.contains("// ===== 特征: FEAT0001 ====="));
        assertTrue(removed.contains("def featLoanAmt()"));
        assertFalse(removed.contains("FEAT0002"));
        assertFalse(removed.contains("featCustLevel"));
        assertFalse(removed.contains("FEAT0003"));
        assertFalse(removed.contains("featRiskFlag"));
        assertTrue(removed.contains("'FEAT0001': featLoanAmt(),"));
    }

    /**
     * 批量删除时存在缺失编码：抛异常并提示缺失编码，不做部分删除。
     */
    @Test
    public void testRemoveFeature_batchWithMissingCode_throws() {
        FeatureConfigDTO feature1 = featureConfig("FEAT0001",
                featureScript("featLoanAmt",
                        "        def step1 = 1;\n        result = step1;\n"));

        String merged = generator.mergeFeatureExpressions(
                Collections.singletonList(feature1), null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> generator.removeFeature(merged, Arrays.asList("FEAT0001", "NOT_EXIST")));

        assertTrue(ex.getMessage().contains("NOT_EXIST"));
    }

    /**
     * 传入现有合并脚本时，应在保留原特征的基础上追加新特征，并同步更新特征数量与调度段。
     */
    @Test
    public void testMergeFeatureExpressions_withExistingScript_appendsFeature() throws Exception {
        FeatureConfigDTO existingFeature = featureConfig("FEAT0001",
                featureScript("featExisting",
                        "        def step1 = 1;\n        result = step1;\n"));
        String existingScript = generator.mergeFeatureExpressions(
                Collections.singletonList(existingFeature), null);

        FeatureConfigDTO newFeature = featureConfig("FEAT0002",
                featureScript("featNew",
                        "        def step1 = 2;\n        result = step1;\n"));
        String merged = generator.mergeFeatureExpressions(
                Collections.singletonList(newFeature), null, existingScript);

        assertTrue(merged.contains("// 特征数量: 2"));
        assertTrue(merged.contains("// ===== 特征: FEAT0001 ====="));
        assertTrue(merged.contains("def featExisting()"));
        assertTrue(merged.contains("'FEAT0001': featExisting(),"));
        assertTrue(merged.contains("// ===== 特征: FEAT0002 ====="));
        assertTrue(merged.contains("def featNew()"));
        assertTrue(merged.contains("'FEAT0002': featNew(),"));

        CompiledGroovyScript compiled = GroovyExecutor.compile(merged);
        Map<?, ?> result = (Map<?, ?>) GroovyExecutor.execute(compiled, "{}");
        assertEquals(2, result.size());
        assertEquals(1, result.get("FEAT0001"));
        assertEquals(2, result.get("FEAT0002"));
    }

    /**
     * 新增方法名与现有合并脚本方法名重复时直接抛异常，不追加任何内容。
     */
    @Test
    public void testMergeFeatureExpressions_withExistingScript_duplicateMethod_throws() {
        FeatureConfigDTO existingFeature = featureConfig("FEAT0001",
                featureScript("sameName",
                        "        def step1 = 1;\n        result = step1;\n"));
        String existingScript = generator.mergeFeatureExpressions(
                Collections.singletonList(existingFeature), null);

        FeatureConfigDTO newFeature = featureConfig("FEAT0002",
                featureScript("sameName",
                        "        def step1 = 2;\n        result = step1;\n"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> generator.mergeFeatureExpressions(
                        Collections.singletonList(newFeature), null, existingScript));

        assertTrue(ex.getMessage().contains("sameName"));
        assertTrue(ex.getMessage().contains("现有合并脚本"));
    }
}
