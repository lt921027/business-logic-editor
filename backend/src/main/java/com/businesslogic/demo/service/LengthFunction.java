package com.businesslogic.demo.service;

import java.util.List;
import java.util.Map;

/**
 * 字符串长度函 */
public class LengthFunction implements BuiltinFunction {
    
    @Override
    public String getName() {
        return "length";
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object value, List<String> params) {
        if (value == null) {
            return 0;
        }
        
        if (value instanceof String) {
            return ((String) value).length();
        }
        
        if (value instanceof Map) {
            return ((Map<?, ?>) value).size();
        }
        
        if (value instanceof Iterable) {
            int count = 0;
            for (Object obj : (Iterable<?>) value) {
                count++;
            }
            return count;
        }
        
        if (value.getClass().isArray()) {
            return java.lang.reflect.Array.getLength(value);
        }
        
        //其他类型转为字符串计算长
        return String.valueOf(value).length();
    }
    
    @Override
    public String getDescription() {
        return "返回字符串、数组、集合或映射的长";
    }
    
    @Override
    public String getReturnType() {
        return "number";
    }
}
