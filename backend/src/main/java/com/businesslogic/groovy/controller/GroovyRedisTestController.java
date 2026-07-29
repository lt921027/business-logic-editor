package com.businesslogic.groovy.controller;

import com.businesslogic.common.Result;
import com.businesslogic.groovy.engine.GroovyExecutor;
import com.businesslogic.groovy.redisCache.GroovyRedisExpressionCache;
import com.businesslogic.groovy.redisCache.GroovyTxnExpressionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Groovy Redis 表达式缓存测试 Controller
 *
 * <p>对应 Aviator 的 AviatorRedisTestController，业务逻辑保持一致。
 *
 * <p>使用独立的 URL 前缀 /test/groovy-expr-cache/ 与 Aviator 测试控制器隔离。
 *
 * <p>关联体系：
 * <ul>
 *   <li>所有操作委托给 {@link GroovyRedisExpressionCache}</li>
 *   <li>execute 接口通过 {@link GroovyExecutor#execute} 执行缓存中的编译产物</li>
 *   <li>注意 Groovy 语法：env 中变量默认为 String 类型，表达式需显式类型转换
 *       （如 Long.valueOf(amount.toString())），与 Aviator 自动类型转换不同</li>
 * </ul>
 */
@RestController
@RequestMapping("/test/groovy-expr-cache")
@CrossOrigin(origins = "*", maxAge = 3600)
public class GroovyRedisTestController {

    private static final Logger logger = LoggerFactory.getLogger(GroovyRedisTestController.class);

    private final GroovyRedisExpressionCache cache;

    /**
     * 构造器注入。
     *
     * @param cache Groovy Redis 表达式缓存
     */
    public GroovyRedisTestController(GroovyRedisExpressionCache cache) {
        this.cache = cache;
    }

    /**
     * 1. 写入测试数据：给交易码添加特征表达式
     *
     * <p>关联：委托 {@link GroovyRedisExpressionCache#putFeature}。
     *
     * @param body 含 txnCode、featureCode、expression 的请求体
     * @return 写入结果
     */
    @PostMapping("/put")
    public Result<Map<String, Object>> putFeature(@RequestBody Map<String, String> body) {
        String txnCode = body.getOrDefault("txnCode", "TXN001");
        String featureCode = body.get("featureCode");
        // Groovy 语法示例：def amount = long(amount); amount > 100 && status == 'ACTIVE'
        String expression = body.getOrDefault("expression",
                "Long.valueOf(amount.toString()) > 100 && status == 'ACTIVE'");

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
            logger.error("[GroovyTest] putFeature 失败", e);
            return Result.error("写入失败: " + e.getMessage());
        }
    }

    /**
     * 2. 根据交易码查询所有特征及表达式（走本地缓存）
     *
     * <p>关联：调用 {@link GroovyRedisExpressionCache#getByTxnCode} 获取上下文，
     * 遍历其 features 输出每个特征的版本号和表达式哈希。
     *
     * @param txnCode 交易码
     * @return 交易码下的特征列表及缓存状态
     */
    @GetMapping("/query/{txnCode}")
    public Result<Map<String, Object>> queryByTxn(@PathVariable String txnCode) {
        GroovyTxnExpressionContext ctx = cache.getByTxnCode(txnCode);

        if (ctx == null) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("txnCode", txnCode);
            resp.put("featureCount", 0);
            resp.put("features", Collections.emptyList());
            resp.put("cacheStatus", cache.getStatus());
            return Result.success("交易码不存在", resp);
        }

        List<Map<String, Object>> features = ctx.getFeatures().values().stream()
                .map(feat -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("featureCode", feat.getFeatureCode());
                    m.put("featureVersion", feat.getVersion());
                    m.put("expressionSourceHash", feat.getExpression().getSourceHash());
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
     *
     * <p>为何提供默认 env：方便快速测试，未传 env 时使用预设的测试数据。
     *
     * <p>关联：通过 {@link GroovyRedisExpressionCache#getByTxnCode} 获取上下文，
     * 遍历 features 调用 {@link GroovyExecutor#execute} 执行每个表达式。
     *
     * @param txnCode 交易码
     * @param env     执行环境变量（可选）
     * @return 每个特征的执行结果
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

        GroovyTxnExpressionContext ctx = cache.getByTxnCode(txnCode);

        Map<String, Object> results = new HashMap<>();
        if (ctx != null) {
            for (GroovyTxnExpressionContext.FeatureVersionedExpression feat : ctx.getFeatures().values()) {
                try {
                    // 使用 GroovyExecutor 执行（自动注入工具类、安全沙箱）
                    Object result = GroovyExecutor.execute(feat.getExpression(), null, env);
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
     *
     * <p>关联：调用 {@link GroovyRedisExpressionCache#getStatus}。
     *
     * @return 缓存状态对象
     */
    @GetMapping("/status")
    public Result<GroovyRedisExpressionCache.CacheStatus> status() {
        return Result.success("ok", cache.getStatus());
    }

    /**
     * 5. 手动触发全量重载
     *
     * <p>为何需要手动触发：测试场景下写入数据后需立即刷新本地缓存，
     * 不必等待定时轮询。
     *
     * <p>关联：调用 {@link GroovyRedisExpressionCache#triggerFullReload}。
     *
     * @return 重载后的缓存状态
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
     *
     * <p>关联：委托 {@link GroovyRedisExpressionCache#updateFeature}。
     *
     * @param body 含 txnCode、featureCode、expression 的请求体
     * @return 更新结果
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
            logger.error("[GroovyTest] updateFeature 失败", e);
            return Result.error("更新失败: " + e.getMessage());
        }
    }

    /**
     * 7. 删除交易码下的特征
     *
     * <p>关联：委托 {@link GroovyRedisExpressionCache#removeFeature}。
     *
     * @param body 含 txnCode、featureCode 的请求体
     * @return 删除结果
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
     *
     * <p>关联：委托 {@link GroovyRedisExpressionCache#removeTxn}。
     *
     * @param txnCode 交易码
     * @return 删除结果
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
     *
     * <p>为何需要此接口：测试时需快速构造一批数据验证缓存和执行流程，
     * 避免逐条调用 put 接口。
     *
     * <p>注意：Groovy 语法与 Aviator 不同，所有变量需显式类型转换（env 默认传 String）。
     *
     * <p>关联：多次调用 {@link GroovyRedisExpressionCache#putFeature} 写入不同交易码的特征。
     *
     * @return 写入的交易码列表和缓存状态
     */
    @PostMapping("/init-test-data")
    public Result<Map<String, Object>> initTestData() throws Exception {
        // TXN001 3 个特征（Groovy 语法）
        cache.putFeature("TXN001", "F_AMOUNT_CHECK",
                "Long.valueOf(amount.toString()) > 100 && status == 'ACTIVE'");
        cache.putFeature("TXN001", "F_DISCOUNT_CALC",
                "Double.valueOf(price.toString()) * Long.valueOf(quantity.toString()) * (1 - Double.valueOf(discount.toString()))");
        cache.putFeature("TXN001", "F_RISK_SCORE",
                "Long.valueOf(score.toString()) >= 60 ? 'PASS' : 'FAIL'");

        // TXN002 2 个特征
        cache.putFeature("TXN002", "F_AGE_CHECK",
                "Long.valueOf(age.toString()) >= 18 && Long.valueOf(age.toString()) <= 65");
        cache.putFeature("TXN002", "F_AMOUNT_LIMIT",
                "Long.valueOf(amount.toString()) <= 10000");

        Map<String, Object> resp = new HashMap<>();
        resp.put("txnCodes", cache.getAllTxnCodes());
        resp.put("status", cache.getStatus());
        return Result.success("测试数据初始化完成", resp);
    }
}
