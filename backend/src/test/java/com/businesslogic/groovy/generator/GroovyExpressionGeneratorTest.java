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
        assertTrue(merged.contains("// 默认值: BigDecimal.valueOf(-99999)"));
        assertTrue(merged.contains("// 返回值类型: BigDecimal"));
        assertTrue(merged.contains("// 源码hash: "));
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
        assertTrue(mergedAdded.contains("// 源码hash: "));
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
        assertTrue(merged.contains("// 默认值: 0"));
        assertTrue(merged.contains("// 返回值类型: Integer"));
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
        assertTrue(merged.contains("// 特征编码: metaFeature"));
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
}
