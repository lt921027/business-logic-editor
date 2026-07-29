package com.businesslogic.controller;

import com.alibaba.fastjson.JSON;
import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.Expression;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 测试：根据交易码查询所有特征编码和表达式，执行后存Map JSON
 *
 * <p>模拟实际业务流程 * <ol>
 *   <li>根据交易码查询该交易码下的所有特征编码及编译后的表达/li>
 *   <li>传入业务参数，逐个执行每个特征的表达式</li>
 *   <li>将执行结果按 featureCode result 存入 Map</li>
 *   <li>Map 转为 JSON 字符串输/li>
 * </ol>
 */
public class FeatureExecuteTest {

    public static void main(String[] args) {
        // ========== 1. 模拟：根据交易码查询到的 5 个特征及表达==========
        String txnCode = "TXN001";

        // 特征编码 表达式（模拟AviatorRedisExpressionCache.getByTxnCode() 查询结果        // 每个表达式返回不同类型，模拟实际业务场景
        Map<String, Expression> featureExpressions = new LinkedHashMap<>();
        featureExpressions.put("F_AMOUNT_CHECK", AviatorEvaluator.compile("return amount > 1000;"));
        featureExpressions.put("F_RISK_SCORE",   AviatorEvaluator.compile("return long(amount * riskRate);"));
        featureExpressions.put("F_LEVEL_TAG",    AviatorEvaluator.compile("return string(level);"));
        featureExpressions.put("F_IS_VIP",       AviatorEvaluator.compile("return isVip == true;"));
        featureExpressions.put("F_DISCOUNT_RATE", AviatorEvaluator.compile("return double(discount * 100);"));

        System.out.println("========== 交易 " + txnCode + " ==========");
        System.out.println("特征数量: " + featureExpressions.size());
        System.out.println();

        // ========== 2. 模拟：业务参数（外部传入的变量） ==========
        Map<String, Object> env = new HashMap<>();
        env.put("amount", 2500L);
        env.put("riskRate", 0.85);
        env.put("level", "GOLD");
        env.put("isVip", true);
        env.put("discount", 0.15);

        System.out.println("========== 输入参数 ==========");
        for (Map.Entry<String, Object> entry : env.entrySet()) {
            System.out.printf("  %s = %s (%s)%n",
                    entry.getKey(), entry.getValue(),
                    entry.getValue() != null ? entry.getValue().getClass().getSimpleName() : "null");
        }
        System.out.println();

        // ========== 3. 执行所有表达式，结果存Map ==========
        Map<String, Object> resultMap = new LinkedHashMap<>();

        System.out.println("========== 执行结果详情 ==========");
        for (Map.Entry<String, Expression> entry : featureExpressions.entrySet()) {
            String featureCode = entry.getKey();
            Expression exp = entry.getValue();

            try {
                Object result = exp.execute(env);

                //打印每个特征的执行详
                System.out.printf("[%s] 表达 %s%n", featureCode, exp.toString());
                System.out.printf("  返回类型: %s%n", result != null ? result.getClass().getSimpleName() : "null");
                System.out.printf("  返回   %s%n", result);
                System.out.println();

                resultMap.put(featureCode, result);
            } catch (Exception e) {
                System.out.printf("[%s] 执行异常: %s%n%n", featureCode, e.getMessage());
                resultMap.put(featureCode, "ERROR: " + e.getMessage());
            }
        }

        // ========== 4. JSON 并打==========
        String jsonStr = JSON.toJSONString(resultMap, true);

        System.out.println("========== 最终结JSON ==========");
        System.out.println(jsonStr);
    }
}
