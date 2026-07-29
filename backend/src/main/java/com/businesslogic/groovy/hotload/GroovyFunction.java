package com.businesslogic.groovy.hotload;

import java.util.Map;

/**
 * Groovy Java 函数接口
 *
 * <p>对应 Aviator 的 AbstractFunction，供 Java 类型的自定义函数实现。
 *
 * <p>设计上避免依赖 Aviator 类型系统，使用纯 Java 类型。
 * 函数实现需保证线程安全（无共享可变状态，或使用同步机制），因为同一函数实例会被
 * 多个 Groovy 脚本执行线程并发调用。
 *
 * <p>关联体系：
 * <ul>
 *   <li>被 {@link GroovyFunctionDefinition#javaFunction} 字段持有，作为 JAVA 类型函数的实际逻辑载体</li>
 *   <li>被 {@link GroovyFunctionRegistry#registerJavaFunction} 注册到引擎</li>
 *   <li>调用入口：{@link GroovyFunctionRegistry.JavaFunctionClosure#call} 会将 Groovy 调用参数
 *       转发到本接口的 {@link #call} 方法</li>
 *   <li>与 EXPRESSION/SCRIPT 类型不同，本接口无需编译，注册即生效</li>
 * </ul>
 */
public interface GroovyFunction {

    /**
     * 获取函数名称
     *
     * <p>必须与注册时使用的 name 一致，用于在异常信息中标识函数。
     *
     * @return 函数名
     */
    String getName();

    /**
     * 调用函数
     *
     * <p>实现者可从 env 读取上下文变量（如当前交易数据），args 为 Groovy 调用方传入的参数列表。
     *
     * <p>为何传入 env：与 EXPRESSION/SCRIPT 类型一致，使 Java 函数也能访问当前执行上下文，
     * 例如读取 `inputData` 字段或前序步骤的输出变量，保持三类函数语义对齐。
     *
     * @param env  当前执行环境变量（包含 inputData、step1/step2... 等）
     * @param args 参数列表，顺序与 {@link GroovyFunctionDefinition#getParams} 一致
     * @return 执行结果，将被 Groovy 脚本作为函数返回值使用
     */
    Object call(Map<String, Object> env, Object... args);
}
