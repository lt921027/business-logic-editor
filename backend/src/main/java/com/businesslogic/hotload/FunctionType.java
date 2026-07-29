package com.businesslogic.hotload;

/**
 * 函数类型枚举
 */
public enum FunctionType {

    /**
     * Aviator 表达     * 示例: "a + b * 0.1"
     */
    EXPRESSION,

    /**
     * AviatorScript 多行脚本
     * 支持变量定义、条件判断、循环等复杂逻辑
     */
    SCRIPT,

    /**
     * Java 函数
     * 通过 AbstractFunction 子类实现
     */
    JAVA
}
