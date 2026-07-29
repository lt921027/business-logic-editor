package com.businesslogic.demo.service;

import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * 内置函数注册 */
@Component
public class FunctionRegistry {
    
    private final Map<String, BuiltinFunction> functionMap = new HashMap<>();
    
    @PostConstruct
    public void init() {
        //注册所有内置函
        registerFunction(new LengthFunction());
        registerFunction(new ToUpperCaseFunction());
        registerFunction(new ToLowerCaseFunction());
        registerFunction(new SubstringFunction());
    }
    
    /**
     * 注册函数
     */
    public void registerFunction(BuiltinFunction function) {
        functionMap.put(function.getName().toLowerCase(), function);
    }
    
    /**
     * 获取函数
     */
    public BuiltinFunction getFunction(String name) {
        return functionMap.get(name.toLowerCase());
    }
    
    /**
     * 检查函数是否存     */
    public boolean hasFunction(String name) {
        return functionMap.containsKey(name.toLowerCase());
    }
    
    /**
     * 获取所有已注册的函     */
    public Map<String, BuiltinFunction> getAllFunctions() {
        return new HashMap<>(functionMap);
    }
}
