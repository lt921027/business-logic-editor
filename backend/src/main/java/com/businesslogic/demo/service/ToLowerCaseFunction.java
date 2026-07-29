package com.businesslogic.demo.service;

import java.util.List;

/**
 * 字符串转小写函数
 */
public class ToLowerCaseFunction implements BuiltinFunction {
    
    @Override
    public String getName() {
        return "toLowerCase";
    }
    
    @Override
    public Object execute(Object value, List<String> params) {
        if (value == null) {
            return null;
        }
        return value.toString().toLowerCase();
    }
    
    @Override
    public String getDescription() {
        return "将字符串转换为小";
    }
    
    @Override
    public String getReturnType() {
        return "string";
    }
}
