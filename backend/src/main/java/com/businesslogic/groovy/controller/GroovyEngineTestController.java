package com.businesslogic.groovy.controller;

import com.businesslogic.common.Result;
import com.businesslogic.groovy.engine.CompiledGroovyScript;
import com.businesslogic.groovy.engine.GroovyExecutor;
import com.businesslogic.groovy.engine.GroovyExpressionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Groovy 引擎测试 Controller
 *
 * <p>提供直接执行 Groovy 脚本的测试接口，用于验证引擎和沙箱功能。
 *
 * <p>使用独立的 URL 前缀 /test/groovy-engine/ 与其他控制器隔离。
 *
 * <p>关联体系：
 * <ul>
 *   <li>直接持有 {@link GroovyExpressionEngine} 单例，绕过 Service 层用于测试</li>
 *   <li>通过 {@link GroovyExecutor#execute} 执行编译后的脚本（自动注入工具类和沙箱）</li>
 *   <li>sandbox-test 接口验证 {@link com.businesslogic.groovy.security.GroovySandbox} 的拦截能力</li>
 *   <li>clear-cache 接口调用 {@link GroovyExpressionEngine#clearCompileCache} 清空编译缓存</li>
 * </ul>
 */
@RestController
@RequestMapping("/test/groovy-engine")
@CrossOrigin(origins = "*", maxAge = 3600)
public class GroovyEngineTestController {

    private static final Logger logger = LoggerFactory.getLogger(GroovyEngineTestController.class);

    private final GroovyExpressionEngine engine;

    /**
     * 构造器注入引擎单例。
     *
     * @param engine Groovy 表达式引擎
     */
    public GroovyEngineTestController(GroovyExpressionEngine engine) {
        this.engine = engine;
    }

    /**
     * 1. 执行 Groovy 脚本
     *
     * <p>分别统计编译耗时和执行耗时，便于性能分析。
     *
     * <p>关联：调用 {@link GroovyExpressionEngine#compile} 编译；
     * 通过 {@link GroovyExecutor#execute} 执行（会自动注入 GroovyDateFunctions 等工具类）。
     *
     * <p>请求体：
     * <pre>
     * {
     *   "script": "def x = 10; def y = 20; return x + y",
     *   "env": { "a": "100", "b": "200" }
     * }
     * </pre>
     *
     * @param body 请求体
     * @return 执行结果、耗时、缓存大小等信息
     */
    @PostMapping("/execute")
    public Result<Map<String, Object>> execute(@RequestBody Map<String, Object> body) {
        String script = (String) body.get("script");
        if (script == null || script.isEmpty()) {
            return Result.error("script 不能为空");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> env = (Map<String, Object>) body.get("env");
        if (env == null) {
            env = new HashMap<>();
        }

        try {
            long startCompile = System.currentTimeMillis();
            CompiledGroovyScript compiled = engine.compile(script);
            long compileTime = System.currentTimeMillis() - startCompile;

            long startExecute = System.currentTimeMillis();
            Object result = GroovyExecutor.execute(compiled, null, env);
            long executeTime = System.currentTimeMillis() - startExecute;

            Map<String, Object> resp = new HashMap<>();
            resp.put("script", script);
            resp.put("result", result);
            resp.put("resultType", result != null ? result.getClass().getName() : "null");
            resp.put("sourceHash", compiled.getSourceHash());
            resp.put("compileTimeMs", compileTime);
            resp.put("executeTimeMs", executeTime);
            resp.put("compileCacheSize", engine.getCompileCacheSize());
            return Result.success("执行成功", resp);
        } catch (Exception e) {
            logger.error("[GroovyEngineTest] 执行失败", e);
            Map<String, Object> resp = new HashMap<>();
            resp.put("script", script);
            resp.put("error", e.getMessage());
            resp.put("errorType", e.getClass().getName());
            return Result.error("执行失败: " + e.getMessage());
        }
    }

    /**
     * 2. 获取引擎状态
     *
     * <p>关联：调用 {@link GroovyExpressionEngine#getCompileCacheSize} 查询当前编译缓存大小。
     *
     * @return 引擎状态信息
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("compileCacheSize", engine.getCompileCacheSize());
        resp.put("engineClass", engine.getClass().getName());
        return Result.success("ok", resp);
    }

    /**
     * 3. 清空编译缓存
     *
     * <p>为何需要此接口：测试场景下可能修改了沙箱配置或函数注册，需清空缓存强制重新编译。
     *
     * <p>关联：调用 {@link GroovyExpressionEngine#clearCompileCache}。
     *
     * @return 清理前后的缓存大小
     */
    @PostMapping("/clear-cache")
    public Result<Map<String, Object>> clearCache() {
        int size = engine.getCompileCacheSize();
        engine.clearCompileCache();
        Map<String, Object> resp = new HashMap<>();
        resp.put("clearedCount", size);
        resp.put("currentSize", engine.getCompileCacheSize());
        return Result.success("编译缓存已清空", resp);
    }

    /**
     * 4. 安全沙箱测试：尝试执行危险操作（应被拒绝）
     *
     * <p>默认测试用例尝试执行 `Runtime.getRuntime().exec('ls')`，应被
     * {@link com.businesslogic.groovy.security.GroovySandbox} 的 RECEIVERS_BLACKLIST
     * （包含 Runtime.class）拦截。
     *
     * <p>关联：通过 {@link GroovyExecutor#execute(String, String, Map)} 直接触发编译+执行，
     * 沙箱在编译阶段就会拒绝。
     *
     * @param body 可选，自定义测试脚本
     * @return blocked=true 表示沙箱成功拦截（符合预期）
     */
    @PostMapping("/sandbox-test")
    public Result<Map<String, Object>> sandboxTest(@RequestBody(required = false) Map<String, Object> body) {
        // 默认测试用例：尝试执行系统命令（应被沙箱拦截）
        String script = body != null ? (String) body.getOrDefault("script",
                "Runtime.getRuntime().exec('ls')") : "Runtime.getRuntime().exec('ls')";

        Map<String, Object> resp = new HashMap<>();
        resp.put("script", script);

        try {
            Object result = GroovyExecutor.execute(script, null, null);
            resp.put("result", result);
            resp.put("blocked", false);
            return Result.success("脚本执行成功（沙箱未拦截）", resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage());
            resp.put("errorType", e.getClass().getSimpleName());
            resp.put("blocked", true);
            return Result.success("脚本被沙箱拦截（符合预期）", resp);
        }
    }
}
