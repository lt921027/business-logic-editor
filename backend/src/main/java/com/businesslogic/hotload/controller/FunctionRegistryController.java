package com.businesslogic.hotload.controller;

import com.businesslogic.common.Result;
import com.businesslogic.hotload.AviatorFunctionRegistry;
import com.businesslogic.hotload.FunctionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 函数注册管理接口
 *
 * 提供函数的增删改查和热更新操 */
@RestController
@RequestMapping("/api/functions")
public class FunctionRegistryController {

    private static final Logger logger = LoggerFactory.getLogger(FunctionRegistryController.class);

    private final AviatorFunctionRegistry registry;

    public FunctionRegistryController(AviatorFunctionRegistry registry) {
        this.registry = registry;
    }

    /**
     * 获取所有已注册的函     */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> listFunctions() {
        List<Map<String, Object>> functions = new ArrayList<>();
        for (String name : registry.getAllFunctionNames()) {
            Map<String, Object> func = new HashMap<>();
            func.put("name", name);
            func.put("loaded", registry.hasFunction(name));
            functions.add(func);
        }
        return Result.success(functions);
    }

    /**
     * 注册表达式函     */
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
            logger.error("Failed to register expression function", e);
            return Result.error("Registration failed: " + e.getMessage());
        }
    }

    /**
     * 注册脚本函数
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
            logger.error("Failed to register script function", e);
            return Result.error("Registration failed: " + e.getMessage());
        }
    }

    /**
     * 更新函数（热更新     */
    @PostMapping("/update/{name}")
    public Result<String> updateFunction(@PathVariable String name, @RequestBody Map<String, Object> request) {
        try {
            String expression = (String) request.get("expression");
            @SuppressWarnings("unchecked")
            List<String> params = (List<String>) request.get("params");

            registry.updateFunction(name, expression,
                    params != null ? params.toArray(new String[0]) : new String[0]);
            return Result.success("Function updated: " + name);
        } catch (Exception e) {
            logger.error("Failed to update function", e);
            return Result.error("Update failed: " + e.getMessage());
        }
    }

    /**
     * 删除函数
     */
    @DeleteMapping("/delete/{name}")
    public Result<String> deleteFunction(@PathVariable String name) {
        try {
            registry.unregisterFunction(name);
            return Result.success("Function deleted: " + name);
        } catch (Exception e) {
            logger.error("Failed to delete function", e);
            return Result.error("Delete failed: " + e.getMessage());
        }
    }

    /**
     * 测试函数执行
     */
    @PostMapping("/test/{name}")
    public Result<Object> testFunction(@PathVariable String name, @RequestBody Map<String, Object> env) {
        try {
            com.googlecode.aviator.AviatorEvaluatorInstance evaluator =
                    com.googlecode.aviator.AviatorEvaluator.getInstance();
            String expression = name + "(" + String.join(", ", env.keySet()) + ")";
            Object result = evaluator.execute(expression, env);
            return Result.success(result);
        } catch (Exception e) {
            logger.error("Function test failed", e);
            return Result.error("Test failed: " + e.getMessage());
        }
    }
}
