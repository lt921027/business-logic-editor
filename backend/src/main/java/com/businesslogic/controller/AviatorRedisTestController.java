package com.businesslogic.controller;

import com.businesslogic.common.Result;
import com.businesslogic.redisCache.AviatorRedisExpressionCache;
import com.businesslogic.redisCache.TxnExpressionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Aviator Redis 表达式缓存测试 Controller
 */
@RestController
@RequestMapping("/test/expr-cache")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AviatorRedisTestController {

    private static final Logger logger = LoggerFactory.getLogger(AviatorRedisTestController.class);

    private final AviatorRedisExpressionCache cache;

    public AviatorRedisTestController(AviatorRedisExpressionCache cache) {
        this.cache = cache;
    }

    /**
     * 1. 写入测试数据：给交易码添加特征表达式
     */
    @PostMapping("/put")
    public Result<Map<String, Object>> putFeature(@RequestBody Map<String, String> body) {
        String txnCode = body.getOrDefault("txnCode", "TXN001");
        String featureCode = body.get("featureCode");
        String expression = body.getOrDefault("expression", "long(amount) > 100 && status == 'ACTIVE'");

        if (featureCode == null || featureCode.isEmpty()) {
            return Result.error("featureCode 不能为空");
        }

        try {
            cache.putFeature(txnCode, featureCode, expression);
            Map<String, Object> resp = new HashMap<>();
            resp.put("txnCode", txnCode);
            resp.put("featureCode", featureCode);
            resp.put("expression", expression);
            return Result.success("写入成功", resp);
        } catch (Exception e) {
            logger.error("[Test] putFeature 失败", e);
            return Result.error("写入失败: " + e.getMessage());
        }
    }

    /**
     * 2. 根据交易码查询所有特征及表达式（走本地缓存）
     */
    @GetMapping("/query/{txnCode}")
    public Result<Map<String, Object>> queryByTxn(@PathVariable String txnCode) {
        TxnExpressionContext ctx = cache.getByTxnCode(txnCode);

        if (ctx == null) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("txnCode", txnCode);
            resp.put("featureCount", 0);
            resp.put("features", java.util.Collections.emptyList());
            resp.put("cacheStatus", cache.getStatus());
            return Result.success("交易码不存在", resp);
        }

        java.util.List<Map<String, Object>> features = ctx.getFeatures().values().stream()
                .map(feat -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("featureCode", feat.getFeatureCode());
                    m.put("featureVersion", feat.getVersion());
                    m.put("expressionClass", feat.getExpression().getClass().getSimpleName());
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Object> resp = new HashMap<>();
        resp.put("txnCode", ctx.getTxnCode());
        resp.put("txnVersion", ctx.getVersion());
        resp.put("featureCount", features.size());
        resp.put("features", features);
        resp.put("cacheStatus", cache.getStatus());
        return Result.success("ok", resp);
    }

    /**
     * 3. 查询并执行某交易码下所有表达式
     */
    @PostMapping("/execute/{txnCode}")
    public Result<Map<String, Object>> execute(@PathVariable String txnCode,
                                                @RequestBody(required = false) Map<String, Object> env) {
        if (env == null) {
            env = new HashMap<>();
            env.put("amount", "250");
            env.put("status", "ACTIVE");
            env.put("price", "99.9");
            env.put("quantity", "5");
            env.put("discount", "0.2");
            env.put("score", "85");
            env.put("age", "30");
        }

        TxnExpressionContext ctx = cache.getByTxnCode(txnCode);

        Map<String, Object> results = new HashMap<>();
        if (ctx != null) {
            for (TxnExpressionContext.FeatureVersionedExpression feat : ctx.getFeatures().values()) {
                try {
                    Object result = feat.getExpression().execute(env);
                    results.put(feat.getFeatureCode(), result);
                } catch (Exception e) {
                    results.put(feat.getFeatureCode(), "ERROR: " + e.getMessage());
                }
            }
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("txnCode", txnCode);
        resp.put("env", env);
        resp.put("results", results);
        resp.put("cacheStatus", cache.getStatus());
        return Result.success("ok", resp);
    }

    /**
     * 4. 获取缓存状态
     */
    @GetMapping("/status")
    public Result<AviatorRedisExpressionCache.CacheStatus> status() {
        return Result.success("ok", cache.getStatus());
    }

    /**
     * 5. 手动触发全量重载
     */
    @PostMapping("/reload")
    public Result<Map<String, Object>> reload() {
        cache.triggerFullReload();

        Map<String, Object> resp = new HashMap<>();
        resp.put("status", cache.getStatus());
        return Result.success("重载完成", resp);
    }

    /**
     * 6. 更新特征表达式
     */
    @PostMapping("/update")
    public Result<Map<String, Object>> updateFeature(@RequestBody Map<String, String> body) {
        String txnCode = body.get("txnCode");
        String featureCode = body.get("featureCode");
        String expression = body.get("expression");

        if (txnCode == null || featureCode == null || expression == null) {
            return Result.error("txnCode、featureCode、expression 不能为空");
        }

        try {
            cache.updateFeature(txnCode, featureCode, expression);
            Map<String, Object> resp = new HashMap<>();
            resp.put("txnCode", txnCode);
            resp.put("featureCode", featureCode);
            resp.put("expression", expression);
            return Result.success("更新成功", resp);
        } catch (Exception e) {
            logger.error("[Test] updateFeature 失败", e);
            return Result.error("更新失败: " + e.getMessage());
        }
    }

    /**
     * 7. 删除交易码下的特征
     */
    @DeleteMapping("/remove-feature")
    public Result<Map<String, Object>> removeFeature(@RequestBody Map<String, String> body) {
        String txnCode = body.get("txnCode");
        String featureCode = body.get("featureCode");

        if (txnCode == null || featureCode == null) {
            return Result.error("txnCode 和 featureCode 不能为空");
        }

        cache.removeFeature(txnCode, featureCode);
        Map<String, Object> resp = new HashMap<>();
        resp.put("txnCode", txnCode);
        resp.put("featureCode", featureCode);
        return Result.success("删除成功", resp);
    }

    /**
     * 8. 删除整个交易码
     */
    @DeleteMapping("/remove-txn/{txnCode}")
    public Result<Map<String, Object>> removeTxn(@PathVariable String txnCode) {
        cache.removeTxn(txnCode);
        Map<String, Object> resp = new HashMap<>();
        resp.put("txnCode", txnCode);
        return Result.success("删除成功", resp);
    }

    /**
     * 9. 批量写入测试数据（快速初始化）
     */
    @PostMapping("/init-test-data")
    public Result<Map<String, Object>> initTestData() throws Exception {
        // TXN001 3 个特征
        cache.putFeature("TXN001", "F_AMOUNT_CHECK", "amount > 100 && status == 'ACTIVE'");
        cache.putFeature("TXN001", "F_DISCOUNT_CALC", "double(price) * long(quantity) * (1 - double(discount))");
        cache.putFeature("TXN001", "F_RISK_SCORE", "long(score) >= 60 ? 'PASS' : 'FAIL'");

        // TXN002 2 个特征
        cache.putFeature("TXN002", "F_AGE_CHECK", "long(age) >= 18 && long(age) <= 65");
        cache.putFeature("TXN002", "F_AMOUNT_LIMIT", "long(amount) <= 10000");

        Map<String, Object> resp = new HashMap<>();
        resp.put("txnCodes", cache.getAllTxnCodes());
        resp.put("status", cache.getStatus());
        return Result.success("测试数据初始化完成", resp);
    }
}
