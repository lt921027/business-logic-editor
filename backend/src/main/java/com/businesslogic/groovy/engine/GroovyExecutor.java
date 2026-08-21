package com.businesslogic.groovy.engine;

import com.businesslogic.util.JsonPathUtil;
import com.businesslogic.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Groovy 表达式执行器
 *
 * <p>对应 Aviator 的 AviatorExecutor。
 * 封装表达式编译和执行流程，提供静态方法。
 *
 * <p>使用单例引擎实例，保证函数注册等运行时状态的全局共享。
 *
 * <p>关联体系：
 * <ul>
 *   <li>静态持有 {@link GroovyExpressionEngine} 单例，对外通过 {@link #getEngine()} 暴露</li>
 *   <li>被 {@link com.businesslogic.groovy.service.GroovyBusinessLogicService} 调用执行业务逻辑</li>
 *   <li>被 {@link com.businesslogic.groovy.hotload.GroovyFunctionRegistry} 获取引擎实例</li>
 *   <li>被各测试 Controller（{@link com.businesslogic.groovy.controller.GroovyRedisTestController}
 *       / {@link com.businesslogic.groovy.controller.GroovyEngineTestController}）直接调用</li>
 * </ul>
 */
public class GroovyExecutor {

    private static final Logger logger = LoggerFactory.getLogger(GroovyExecutor.class);

    /**
     * 单例引擎。
     *
     * <p>为何使用静态 final 单例：
     * <ul>
     *   <li>引擎实例需全局唯一，保证函数注册等运行时状态一致</li>
     * </ul>
     */
    private static final GroovyExpressionEngine engine = new GroovyExpressionEngine();

    /**
     * 执行 Groovy 脚本（源码字符串形式）。
     *
     * <p>关联：内部组合 {@link #prepareEnvironment} + {@link GroovyExpressionEngine#compile} +
     * {@link GroovyExpressionEngine#execute}；适合不需要复用编译结果的一次性执行场景。
     *
     * @param scriptCode     脚本源码
     * @param inputData      输入数据（JSON 字符串，会以 "inputData" 为 key 注入到 Binding）
     * @param additionalData 额外数据（可选，扁平展开到 Binding 中）
     * @return 执行结果
     */
    public static Object execute(String scriptCode, String inputData, Map<String, Object> additionalData) throws Exception {
        logger.info("执行 Groovy 脚本，输入数据长度：{}", inputData != null ? inputData.length() : 0);

        Map<String, Object> env = prepareEnvironment(inputData, additionalData);
        CompiledGroovyScript compiled = engine.compile(scriptCode);
        Object result = engine.execute(compiled, env);

        logger.info("执行结果：{}", result);
        return result;
    }

    /**
     * 执行 Groovy 脚本（简化版，无额外数据）。
     *
     * <p>关联：委托给 {@link #execute(String, String, Map)}，传 null additionalData。
     */
    public static Object execute(String scriptCode, String inputData) throws Exception {
        return execute(scriptCode, inputData, null);
    }

    /**
     * 执行预编译的 Groovy 脚本。
     *
     * <p>为何提供此重载：业务侧通常会在保存业务逻辑时预编译并缓存 {@link CompiledGroovyScript}，
     * 后续执行直接复用，避免每次重复编译。性能上比 {@link #execute(String, String, Map)} 更优。
     *
     * <p>关联：被 {@link com.businesslogic.groovy.service.GroovyBusinessLogicService#executeLogic} 调用
     * （走缓存路径），被 {@link com.businesslogic.groovy.controller.GroovyRedisTestController#execute} 调用。
     *
     * @param compiled       预编译的脚本（来自缓存或 {@link #compile(String)}）
     * @param inputData      输入数据
     * @param additionalData 额外数据
     * @return 执行结果
     */
    public static Object execute(CompiledGroovyScript compiled, String inputData, Map<String, Object> additionalData) throws Exception {
        logger.info("执行预编译的 Groovy 脚本，输入数据长度：{}", inputData != null ? inputData.length() : 0);

        Map<String, Object> env = prepareEnvironment(inputData, additionalData);
        Object result = engine.execute(compiled, env);

        logger.info("执行结果：{}", result);
        return result;
    }

    /**
     * 执行预编译的 Groovy 脚本（简化版，无额外数据）。
     *
     * <p>关联：委托给 {@link #execute(CompiledGroovyScript, String, Map)}。
     */
    public static Object execute(CompiledGroovyScript compiled, String inputData) throws Exception {
        return execute(compiled, inputData, null);
    }

    /**
     * 预编译脚本（不执行）。
     *
     * <p>关联：委托给 {@link GroovyExpressionEngine#compile(String)}；
     * 被 {@link com.businesslogic.groovy.service.GroovyBusinessLogicService#save} 等方法用于保存时预编译校验。
     *
     * @param scriptCode 脚本源码
     * @return 编译后的脚本
     */
    public static CompiledGroovyScript compile(String scriptCode) {
        return engine.compile(scriptCode);
    }

    /**
     * 获取全局单例引擎。
     *
     * <p>为何暴露：部分组件（如 {@link com.businesslogic.groovy.hotload.GroovyFunctionRegistry}）
     * 需要直接操作引擎（注册函数等），通过此方法获取单例引用。
     *
     * @return 全局引擎实例
     */
    public static GroovyExpressionEngine getEngine() {
        return engine;
    }

    /**
     * 准备执行环境：构建包含 inputData/currentDate/additionalData 的 env Map。
     *
     * <p>为何统一在此组装：避免每个 execute 重载重复写 env 构建逻辑，保证内置变量一致。
     *
     * <p>注入内容：
     * <ul>
     *   <li>inputData：原始 JSON 字符串（脚本中可通过 JsonPathUtil.read(inputData, "$.field") 访问字段）</li>
     *   <li>currentDate：当前时间，供日期比较函数使用</li>
     *   <li>additionalData：扁平展开（业务侧自定义变量）</li>
     * </ul>
     *
     * <p>关联：被所有 execute 重载调用；产出的 env 最终注入到 {@link GroovyExpressionEngine#execute} 的 Binding。
     */
    private static Map<String, Object> prepareEnvironment(String inputData, Map<String, Object> additionalData) {
        Map<String, Object> env = new HashMap<>();

        // 添加输入数据（脚本中以 inputData 变量名访问原始 JSON）
        env.put("inputData", inputData);

        // 添加内置变量（业务脚本常需与当前日期比较，如 withinLast3Months(inputData字段)）
        env.put("currentDate", new Date());

        // 添加额外数据（扁平展开，业务侧自定义变量直接以 key 进入 Binding）
        if (additionalData != null && !additionalData.isEmpty()) {
            env.putAll(additionalData);
        }

        return env;
    }

    /**
     * 批量执行多个脚本。
     *
     * <p>为何需要：业务场景如"同一份输入数据跑多条规则"，批量执行可减少 env 构建开销与日志噪声。
     *
     * <p>关联：内部循环调用 {@link #execute(String, String, Map)}；任一脚本异常不影响其他脚本，
     * 异常以字符串形式放入返回 Map。
     *
     * @param scripts        脚本名 → 源码
     * @param inputData      输入数据
     * @param additionalData 额外数据
     * @return 脚本名 → 执行结果（或异常描述）
     */
    public static Map<String, Object> executeBatch(Map<String, String> scripts, String inputData, Map<String, Object> additionalData) {
        Map<String, Object> results = new HashMap<>();

        for (Map.Entry<String, String> entry : scripts.entrySet()) {
            String name = entry.getKey();
            String script = entry.getValue();
            try {
                Object result = execute(script, inputData, additionalData);
                results.put(name, result);
            } catch (Exception e) {
                logger.error("执行脚本 {} 失败", name, e);
                results.put(name, "执行失败: " + e.getMessage());
            }
        }

        return results;
    }
}
