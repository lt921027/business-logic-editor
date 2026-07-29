package com.businesslogic.demo.service;

import java.util.List;

/**
 * 字符串截取函 */
public class SubstringFunction implements BuiltinFunction {
    
    @Override
    public String getName() {
        return "substring";
    }
    
    @Override
    public Object execute(Object value, List<String> params) {
        if (value == null) {
            return null;
        }
        
        String str = value.toString();
        
        if (params == null || params.isEmpty()) {
            return str;
        }
        
        try {
            int start = Integer.parseInt(params.get(0));
            
            if (params.size() > 1) {
                int end = Integer.parseInt(params.get(1));
                return str.substring(Math.min(start, str.length()), Math.min(end, str.length()));
            } else {
                return str.substring(Math.min(start, str.length()));
            }
        } catch (Exception e) {
            return str;
        }
    }
    
    @Override
    public String getDescription() {
        return "截取字符串，参数：开始位置，结束位置（可选）";
    }
    
    @Override
    public String getReturnType() {
        return "string";
    }
}
