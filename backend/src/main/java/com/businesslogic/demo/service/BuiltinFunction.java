package com.businesslogic.demo.service;

import java.util.List;
import java.util.Map;

/**
 * 内置函数接口
 */
public interface BuiltinFunction {
    
    /**
     * 函数名称
     */
    String getName();
    
    /**
     * 执行函数
     * @param value 输入     * @param params 函数参数
     * @return 执行结果
     */
    Object execute(Object value, List<String> params);
    
    /**
     * 函数描述
     */
    String getDescription();
    
    /**
     * 返回值类     */
    String getReturnType();
}
