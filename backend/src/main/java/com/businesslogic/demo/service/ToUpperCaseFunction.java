package com.businesslogic.demo.service;

import java.util.List;

/**
 * 字符串转大写函数
 */
public class ToUpperCaseFunction implements BuiltinFunction {
    
    @Override
    public String getName() {
        return "toUpperCase";
    }
    
    @Override
    public Object execute(Object value, List<String> params) {
        if (value == null) {
            return null;
        }
        return value.toString().toUpperCase();
    }
    
    @Override
    public String getDescription() {
        return "将字符串转换为大";
    }
    
    @Override
    public String getReturnType() {
        return "string";
    }
}
