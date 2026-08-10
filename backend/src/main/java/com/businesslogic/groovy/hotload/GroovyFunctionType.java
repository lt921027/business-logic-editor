package com.businesslogic.groovy.hotload;

/**
 * Groovy 自定义函数类型枚举
 *
 * <p>对应 Aviator 的 FunctionType，业务逻辑保持一致。三种类型在注册流程、编译方式、
 * Closure 包装方式上均有差异，详见 {@link GroovyFunctionRegistry} 的注册方法。
 *
 * <p>关联体系：
 * <ul>
 *   <li>被 {@link GroovyFunctionDefinition#type} 字段引用，作为函数定义的属性</li>
 *   <li>被 {@link GroovyFunctionRegistry#applyRegistration} 用于分支选择 Closure 包装类</li>
 *   <li>EXPRESSION/SCRIPT 由 {@link GroovyFunctionRegistry#compileExpression} 编译；
 *       JAVA 不需要编译，直接包装为 {@link GroovyFunctionRegistry.JavaFunctionClosure}</li>
 * </ul>
 */
public enum GroovyFunctionType {

    /**
     * Groovy 单行表达式类型
     *
     * <p>示例: "a + b * 0.1"
     *
     * <p>为何需要此类型：对应 Aviator 中通过 `let f = lambda(a,b) -> a + b end` 定义的表达式函数，
     * 在 Groovy 中直接编译为单条返回表达式的脚本，由
     * {@link GroovyFunctionRegistry.ExpressionFunctionClosure} 包装为 Closure。
     */
    EXPRESSION,

    /**
     * Groovy 多行脚本类型
     *
     * <p>支持变量定义、条件判断、循环等复杂逻辑。
     *
     * <p>为何需要此类型：对应 Aviator 中通过 `let f = lambda(a,b) -> { ... } end` 定义的多行函数，
     * 在 Groovy 中直接编译多行脚本（参数由调用方在 call() 时注入 Binding），由
     * {@link GroovyFunctionRegistry.ScriptFunctionClosure} 包装为 Closure。
     */
    SCRIPT,

    /**
     * Java 函数类型
     *
     * <p>通过实现 {@link GroovyFunction} 接口的 Java 对象实现，无需编译。
     *
     * <p>为何需要此类型：对应 Aviator 中通过继承 AbstractFunction 实现的 Java 自定义函数。
     * 用于在 Groovy 脚本无法表达（如涉及 IO、外部服务调用）的场景下，桥接 Java 逻辑。
     * 由 {@link GroovyFunctionRegistry.JavaFunctionClosure} 包装为 Closure。
     */
    JAVA
}
