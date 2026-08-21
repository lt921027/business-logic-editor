package com.businesslogic.groovy.controller;

import com.businesslogic.common.Result;
import com.businesslogic.groovy.engine.GroovyExecutor;
import com.businesslogic.groovy.redisCache.GroovyRedisExpressionCache;
import com.businesslogic.groovy.redisCache.GroovySourceScriptEntry;
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

import java.util.HashMap;
import java.util.Map;

/**
 * Groovy Redis 源报文脚本缓存测试 Controller。
 *
 * <p>所有接口按 sourceNo 维度操作，不再使用交易码和特征码。</p>
 */
@RestController
@RequestMapping("/test/groovy-expr-cache")
@CrossOrigin(origins = "*", maxAge = 3600)
public class GroovyRedisTestController {

    private static final Logger logger = LoggerFactory.getLogger(GroovyRedisTestController.class);

    private final GroovyRedisExpressionCache cache;

    public GroovyRedisTestController(GroovyRedisExpressionCache cache) {
        this.cache = cache;
    }

    /**
     * 发布源报文脚本。
     */
    @PostMapping("/publish")
    public Result<Map<String, Object>> publish(@RequestBody Map<String, String> body) {
        String sourceNo = body.get("sourceNo");
        String script = body.get("script");

        if (sourceNo == null || sourceNo.isEmpty()) {
            return Result.error("sourceNo 不能为空");
        }
        if (script == null || script.isEmpty()) {
            return Result.error("script 不能为空");
        }

        try {
            cache.publishSourceScript(sourceNo, script);
            Map<String, Object> resp = new HashMap<>();
            resp.put("sourceNo", sourceNo);
            resp.put("scriptLength", script.length());
            resp.put("status", cache.getStatus());
            return Result.success("发布成功", resp);
        } catch (Exception e) {
            logger.error("[GroovySourceCacheTest] publish 失败", e);
            return Result.error("发布失败: " + e.getMessage());
        }
    }

    /**
     * 更新源报文脚本。
     */
    @PostMapping("/update")
    public Result<Map<String, Object>> update(@RequestBody Map<String, String> body) {
        String sourceNo = body.get("sourceNo");
        String script = body.get("script");

        if (sourceNo == null || script == null || script.isEmpty()) {
            return Result.error("sourceNo 和 script 不能为空");
        }

        try {
            cache.updateSourceScript(sourceNo, script);
            Map<String, Object> resp = new HashMap<>();
            resp.put("sourceNo", sourceNo);
            resp.put("scriptLength", script.length());
            resp.put("status", cache.getStatus());
            return Result.success("更新成功", resp);
        } catch (Exception e) {
            logger.error("[GroovySourceCacheTest] update 失败", e);
            return Result.error("更新失败: " + e.getMessage());
        }
    }

    /**
     * 查询源报文本地编译缓存。
     */
    @GetMapping("/query/{sourceNo}")
    public Result<Map<String, Object>> query(@PathVariable String sourceNo) {
        GroovySourceScriptEntry entry = cache.getBySourceNo(sourceNo);

        Map<String, Object> resp = new HashMap<>();
        resp.put("sourceNo", sourceNo);
        resp.put("status", cache.getStatus());

        if (entry == null) {
            resp.put("found", false);
            return Result.success("源报文不存在或缓存未就绪", resp);
        }

        resp.put("found", true);
        resp.put("version", entry.getVersion());
        resp.put("scriptLength", entry.getScript().getSource() != null
                ? entry.getScript().getSource().length() : 0);
        return Result.success("ok", resp);
    }

    /**
     * 执行源报文整体脚本。
     */
    @PostMapping("/execute/{sourceNo}")
    public Result<Map<String, Object>> execute(@PathVariable String sourceNo,
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

        GroovySourceScriptEntry entry = cache.getBySourceNo(sourceNo);
        if (entry == null) {
            return Result.error("源报文不存在或缓存未就绪: " + sourceNo);
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("sourceNo", sourceNo);
        resp.put("env", env);

        try {
            Object result = GroovyExecutor.execute(entry.getScript(), null, env);
            resp.put("result", result);
            resp.put("status", cache.getStatus());
            return Result.success("ok", resp);
        } catch (Exception e) {
            logger.error("[GroovySourceCacheTest] execute 失败, sourceNo={}", sourceNo, e);
            return Result.error("执行失败: " + e.getMessage());
        }
    }

    /**
     * 获取缓存状态。
     */
    @GetMapping("/status")
    public Result<GroovyRedisExpressionCache.CacheStatus> status() {
        return Result.success("ok", cache.getStatus());
    }

    /**
     * 手动触发全量预热。
     */
    @PostMapping("/reload")
    public Result<Map<String, Object>> reload() {
        cache.triggerFullReload();

        Map<String, Object> resp = new HashMap<>();
        resp.put("status", cache.getStatus());
        return Result.success("全量预热已触发", resp);
    }

    /**
     * 删除源报文脚本。
     */
    @DeleteMapping("/remove-source/{sourceNo}")
    public Result<Map<String, Object>> removeSource(@PathVariable String sourceNo) {
        cache.removeSourceScript(sourceNo);

        Map<String, Object> resp = new HashMap<>();
        resp.put("sourceNo", sourceNo);
        resp.put("status", cache.getStatus());
        return Result.success("删除成功", resp);
    }
}
