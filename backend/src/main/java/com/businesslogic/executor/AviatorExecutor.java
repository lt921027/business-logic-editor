package com.businesslogic.executor;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.businesslogic.util.StringUtil;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Expression;
import com.businesslogic.util.JsonPathUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Aviator 表达式执行器
 * 用于执行预编译的 Aviator 表达式，支持传入自定义数 */
public class AviatorExecutor {

    private static final Logger logger = LoggerFactory.getLogger(AviatorExecutor.class);

    /**
     * 执行 Aviator 表达     * 
     * @param expressionCode Aviator 表达式代     * @param inputData 输入数据（通常JSON 字符串）
     * @param additionalData 额外的输入数据（可选）
     * @return 执行结果
     * @throws Exception 执行异常
     */
    public static Object execute(String expressionCode, String inputData, Map<String, Object> additionalData) throws Exception {
        logger.info("执行 Aviator 表达式，输入数据长度：{}", inputData != null ? inputData.length() : 0);
        
        //获取 Aviator 评估器实
        AviatorEvaluatorInstance evaluator = com.googlecode.aviator.AviatorEvaluator.getInstance();
        
        // 准备执行环境
        Map<String, Object> env = prepareEnvironment(inputData, additionalData);
        
        // 编译并执行表达式
        Expression compiled = evaluator.compile(expressionCode);
        Object result = compiled.execute(env);
        
        logger.info("执行结果：{}", result);
        return result;
    }

    /**
     * 执行 Aviator 表达式（简化版，只传入输入数据     * 
     * @param expressionCode Aviator 表达式代     * @param inputData 输入数据（通常JSON 字符串）
     * @return 执行结果
     * @throws Exception 执行异常
     */
    public static Object execute(String expressionCode, String inputData) throws Exception {
        return execute(expressionCode, inputData, null);
    }

    /**
     * 执行预编译的 Aviator 表达     * 
     * @param expression 预编译的表达     * @param inputData 输入数据（通常JSON 字符串）
     * @param additionalData 额外的输入数据（可选）
     * @return 执行结果
     * @throws Exception 执行异常
     */
    public static Object execute(Expression expression, String inputData, Map<String, Object> additionalData) throws Exception {
        logger.info("执行预编译的 Aviator 表达式，输入数据长度：{}", inputData != null ? inputData.length() : 0);
        
        // 准备执行环境
        Map<String, Object> env = prepareEnvironment(inputData, additionalData);
        
        //执行预编译的表达
        Object result = expression.execute(env);
        
        logger.info("执行结果：{}", result);
        return result;
    }

    /**
     * 执行预编译的 Aviator 表达式（简化版     * 
     * @param expression 预编译的表达     * @param inputData 输入数据（通常JSON 字符串）
     * @return 执行结果
     * @throws Exception 执行异常
     */
    public static Object execute(Expression expression, String inputData) throws Exception {
        return execute(expression, inputData, null);
    }

    /**
     * 准备执行环境
     * 
     * @param inputData 输入数据
     * @param additionalData 额外数据
     * @return 执行环境
     */
    private static Map<String, Object> prepareEnvironment(String inputData, Map<String, Object> additionalData) {
        Map<String, Object> env = new HashMap<>();
        
        //注册工具
        registerUtilities(env);
        
        // 添加输入数据
        env.put("inputData", inputData);
        
        // 添加内置变量
        addBuiltInVariables(env);
        
        // 添加额外数据
        if (additionalData != null && !additionalData.isEmpty()) {
            env.putAll(additionalData);
        }
        
        return env;
    }

    /**
     * 注册工具类到执行环境
     * 
     * @param env 执行环境
     */
    private static void registerUtilities(Map<String, Object> env) {
        //注册 JsonPathUtil 类，用于JSON 中读取数
        env.put("JsonPathUtil", JsonPathUtil.class);
        
        // 可以在这里添加其他工具类
        env.put("StringUtil", StringUtil.class);
        //env.put("DateUtils", DateUtils.class);
    }

    /**
     * 添加内置变量
     * 
     * @param env 执行环境
     */
    private static void addBuiltInVariables(Map<String, Object> env) {
        // 当前日期时间
        env.put("currentDate", new Date());
        
        // 可以在这里添加其他内置变        // env.put("currentTime", System.currentTimeMillis());
        // env.put("appVersion", "1.0.0");
    }

    /**
     * 批量执行多个表达     * 
     * @param expressions 表达式映射（key: 表达式名称，value: 表达式代码）
     * @param inputData 输入数据
     * @param additionalData 额外数据
     * @return 执行结果映射（key: 表达式名称，value: 执行结果     */
    public static Map<String, Object> executeBatch(Map<String, String> expressions, String inputData, Map<String, Object> additionalData) {
        Map<String, Object> results = new HashMap<>();
        
        for (Map.Entry<String, String> entry : expressions.entrySet()) {
            String name = entry.getKey();
            String expression = entry.getValue();
            
            try {
                Object result = execute(expression, inputData, additionalData);
                results.put(name, result);
            } catch (Exception e) {
                logger.error("执行表达{} 失败", name, e);
                results.put(name, "执行失败" + e.getMessage());
            }
        }
        
        return results;
    }

    /**
     * 预编译表达式
     * 
     * @param expressionCode 表达式代     * @return 预编译的表达     */
    public static Expression compile(String expressionCode) {
        AviatorEvaluatorInstance evaluator = com.googlecode.aviator.AviatorEvaluator.getInstance();
        return evaluator.compile(expressionCode);
    }
}
