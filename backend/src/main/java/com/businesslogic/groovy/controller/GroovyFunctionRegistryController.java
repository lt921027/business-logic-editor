package com.businesslogic.groovy.controller;

import com.businesslogic.common.Result;
import com.businesslogic.groovy.engine.GroovyExecutor;
import com.businesslogic.groovy.hotload.GroovyFunctionDefinition;
import com.businesslogic.groovy.hotload.GroovyFunctionRegistry;
import com.businesslogic.groovy.hotload.GroovyFunctionType;
import groovy.lang.Closure;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Groovy 函数注册管理接口
 *
 * <p>对应 Aviator 的 FunctionRegistryController，业务逻辑保持一致。
 *
 * <p>使用独立的 URL 前缀 /api/groovy-functions/ 与 Aviator 控制器隔离。
 *
 * <p>关联体系：
 * <ul>
 *   <li>所有写操作委托给 {@link GroovyFunctionRegistry} 的 register/update/unregister 系列方法</li>
 *   <li>test 接口通过 {@link GroovyFunctionRegistry#getFunctionInstance} 获取 Closure 直接调用</li>
 *   <li>list/detail 接口通过 {@link GroovyFunctionRegistry#getFunctionDefinition} 查询元信息</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/groovy-functions")
@CrossOrigin(origins = "*", maxAge = 3600)
public class GroovyFunctionRegistryController {

    private static final Logger logger = LoggerFactory.getLogger(GroovyFunctionRegistryController.class);

    private final GroovyFunctionRegistry registry;

    /**
     * 构造器注入。
     *
     * @param registry Groovy 函数注册中心
     */
    public GroovyFunctionRegistryController(GroovyFunctionRegistry registry) {
        this.registry = registry;
    }

    /**
     * 获取所有已注册的函数
     *
     * <p>关联：调用 {@link GroovyFunctionRegistry#getAllFunctionNames} 获取名称列表，
     * 再通过 {@link GroovyFunctionRegistry#getFunctionDefinition} 查询每个函数的元信息。
     *
     * @return 函数信息列表
     */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> listFunctions() {
        List<Map<String, Object>> functions = new ArrayList<>();
        for (String name : registry.getAllFunctionNames()) {
            Map<String, Object> func = new HashMap<>();
            func.put("name", name);
            func.put("loaded", registry.hasFunction(name));
            GroovyFunctionDefinition def = registry.getFunctionDefinition(name);
            if (def != null) {
                func.put("type", def.getType());
                func.put("description", def.getDescription());
                func.put("version", def.getVersion());
                func.put("enabled", def.isEnabled());
            }
            functions.add(func);
        }
        return Result.success(functions);
    }

    /**
     * 注册表达式函数
     *
     * <p>关联：委托 {@link GroovyFunctionRegistry#registerExpressionFunction}。
     *
     * @param request 含 name、expression、params 的请求体
     * @return 注册结果消息
     */
    @PostMapping("/register/expression")
    public Result<String> registerExpressionFunction(@RequestBody Map<String, Object> request) {
        try {
            String name = (String) request.get("name");
            String expression = (String) request.get("expression");
            @SuppressWarnings("unchecked")
            List<String> params = (List<String>) request.get("params");

            registry.registerExpressionFunction(name, expression,
                    params != null ? params.toArray(new String[0]) : new String[0]);
            return Result.success("Expression function registered: " + name);
        } catch (Exception e) {
            logger.error("[Groovy] Failed to register expression function", e);
            return Result.error("Registration failed: " + e.getMessage());
        }
    }

    /**
     * 注册脚本函数
     *
     * <p>关联：委托 {@link GroovyFunctionRegistry#registerScriptFunction}。
     *
     * @param request 含 name、script、params 的请求体
     * @return 注册结果消息
     */
    @PostMapping("/register/script")
    public Result<String> registerScriptFunction(@RequestBody Map<String, Object> request) {
        try {
            String name = (String) request.get("name");
            String script = (String) request.get("script");
            @SuppressWarnings("unchecked")
            List<String> params = (List<String>) request.get("params");

            registry.registerScriptFunction(name, script,
                    params != null ? params.toArray(new String[0]) : new String[0]);
            return Result.success("Script function registered: " + name);
        } catch (Exception e) {
            logger.error("[Groovy] Failed to register script function", e);
            return Result.error("Registration failed: " + e.getMessage());
        }
    }

    /**
     * 更新函数（热更新）
     *
     * <p>根据现有函数的 type 自动选择对应的 update 方法。
     * JAVA 类型不支持通过此接口更新（因为 Java 函数实例无法通过 JSON 传递）。
     *
     * <p>关联：委托 {@link GroovyFunctionRegistry#updateFunction} 或
     * {@link GroovyFunctionRegistry#updateScriptFunction}。
     *
     * @param name    函数名
     * @param request 含 expression/script/params 的请求体
     * @return 更新结果消息
     */
    @PostMapping("/update/{name}")
    public Result<String> updateFunction(@PathVariable String name, @RequestBody Map<String, Object> request) {
        try {
            GroovyFunctionDefinition existing = registry.getFunctionDefinition(name);
            if (existing == null) {
                return Result.error("Function not found: " + name);
            }

            GroovyFunctionType type = existing.getType();
            String expression = (String) request.get("expression");
            String script = (String) request.get("script");
            @SuppressWarnings("unchecked")
            List<String> params = (List<String>) request.get("params");

            if (type == GroovyFunctionType.EXPRESSION) {
                registry.updateFunction(name,
                        expression != null ? expression : existing.getExpression(),
                        params != null ? params.toArray(new String[0]) : existing.getParams());
            } else if (type == GroovyFunctionType.SCRIPT) {
                registry.updateScriptFunction(name,
                        script != null ? script : existing.getScript(),
                        params != null ? params.toArray(new String[0]) : existing.getParams());
            } else {
                return Result.error("Cannot update JAVA type function via this endpoint: " + name);
            }

            return Result.success("Function updated: " + name);
        } catch (Exception e) {
            logger.error("[Groovy] Failed to update function", e);
            return Result.error("Update failed: " + e.getMessage());
        }
    }

    /**
     * 删除函数
     *
     * <p>关联：委托 {@link GroovyFunctionRegistry#unregisterFunction}。
     *
     * @param name 函数名
     * @return 删除结果消息
     */
    @DeleteMapping("/delete/{name}")
    public Result<String> deleteFunction(@PathVariable String name) {
        try {
            registry.unregisterFunction(name);
            return Result.success("Function deleted: " + name);
        } catch (Exception e) {
            logger.error("[Groovy] Failed to delete function", e);
            return Result.error("Delete failed: " + e.getMessage());
        }
    }

    /**
     * 测试函数执行
     *
     * <p>对应 Aviator 版本：构造表达式 `name(arg1, arg2)` 并执行。
     * Groovy 版本：直接调用注册的 Closure，传入 env 的值作为参数。
     *
     * <p>关联：通过 {@link GroovyFunctionRegistry#getFunctionInstance} 获取 Closure；
     * 通过 {@link GroovyFunctionRegistry#getFunctionDefinition} 获取参数名列表，
     * 按 params 顺序从 env 取值后调用 Closure.call。
     *
     * @param name 函数名
     * @param env  参数环境（key 为参数名，value 为参数值）
     * @return 函数执行结果
     */
    @PostMapping("/test/{name}")
    public Result<Object> testFunction(@PathVariable String name, @RequestBody Map<String, Object> env) {
        try {
            Closure<?> closure = registry.getFunctionInstance(name);
            if (closure == null) {
                return Result.error("Function not found: " + name);
            }

            GroovyFunctionDefinition def = registry.getFunctionDefinition(name);
            Object result;
            if (def != null && def.getParams() != null && def.getParams().length > 0) {
                // 按参数名顺序从 env 中取值
                Object[] args = new Object[def.getParams().length];
                for (int i = 0; i < def.getParams().length; i++) {
                    args[i] = env.get(def.getParams()[i]);
                }
                result = closure.call(args);
            } else {
                // 无参数函数
                result = closure.call();
            }

            return Result.success(result);
        } catch (Exception e) {
            logger.error("[Groovy] Function test failed", e);
            return Result.error("Test failed: " + e.getMessage());
        }
    }

    /**
     * 获取函数详情
     *
     * <p>关联：调用 {@link GroovyFunctionRegistry#getFunctionDefinition}。
     *
     * @param name 函数名
     * @return 函数详情
     */
    @GetMapping("/detail/{name}")
    public Result<Map<String, Object>> getFunctionDetail(@PathVariable String name) {
        GroovyFunctionDefinition def = registry.getFunctionDefinition(name);
        if (def == null) {
            return Result.error("Function not found: " + name);
        }

        Map<String, Object> detail = new HashMap<>();
        detail.put("name", def.getName());
        detail.put("type", def.getType());
        detail.put("expression", def.getExpression());
        detail.put("script", def.getScript());
        detail.put("params", def.getParams());
        detail.put("description", def.getDescription());
        detail.put("version", def.getVersion());
        detail.put("enabled", def.isEnabled());
        return Result.success(detail);
    }

    /**
     * 获取函数注册数量
     *
     * <p>关联：调用 {@link GroovyFunctionRegistry#size} 和
     * {@link GroovyFunctionRegistry#getAllFunctionNames}。
     *
     * @return 数量和名称列表
     */
    @GetMapping("/count")
    public Result<Map<String, Object>> getCount() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("count", registry.size());
        resp.put("names", registry.getAllFunctionNames());
        return Result.success(resp);
    }
}
