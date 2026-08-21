package com.businesslogic.groovy.engine;

import groovy.lang.Script;

import java.util.Objects;

/**
 * 编译后的 Groovy 脚本包装类
 *
 * <p>对应 Aviator 的 com.googlecode.aviator.Expression。
 * 内部持有编译后的 Script Class 和原始源码。
 *
 * <p>由于 Groovy 编译后的 Class 无法被标准 Java 序列化，
 * 序列化/反序列化通过源码字符串实现（存源码 → 重新编译）。
 *
 * <p>关联：由 {@link GroovyExpressionEngine#compile(String)} 创建；
 * 等缓存层持有；被 {@link com.businesslogic.groovy.hotload.GroovyFunctionRegistry} 内部 Closure 调用。
 */
public class CompiledGroovyScript {

    /** 编译后的 Script Class（由 GroovyClassLoader.parseClass 产生，沙箱已注入安全策略） */
    private final Class<? extends Script> scriptClass;

    /** 原始源码（用于序列化和缓存：Groovy Class 不可序列化，存储/恢复以源码字符串为载体） */
    private final String source;

    /** 本次编译的唯一标识（非 MD5，也不用于编译缓存复用） */
    private final String sourceHash;

    /**
     * 构造编译后的脚本包装对象。
     *
     * @param scriptClass 已编译的 Script 子类
     * @param source      原始源码字符串
     * @param sourceHash  本次编译的唯一标识
     */
    public CompiledGroovyScript(Class<? extends Script> scriptClass, String source, String sourceHash) {
        this.scriptClass = scriptClass;
        this.source = source;
        this.sourceHash = sourceHash;
    }

    /**
     * 创建新的 Script 实例。
     *
     * <p>为何每次执行都要新建：Groovy {@link Script} 实例持有 {@link groovy.lang.Binding} 状态（变量、闭包等），
     * 多线程共享同一实例会发生状态串扰。每次执行前调用此方法拿到独立实例，再绑定各自的 Binding，保证线程安全。
     *
     * @return 新的 Script 实例
     */
    public Script newScriptInstance() throws IllegalAccessException, InstantiationException {
        return scriptClass.newInstance();
    }

    /** @return 编译后的 Script Class */
    public Class<? extends Script> getScriptClass() {
        return scriptClass;
    }

    /** @return 原始源码（被 {@link GroovyExpressionEngine#serialize(CompiledGroovyScript)} 用于持久化） */
    public String getSource() {
        return source;
    }

    /** @return 本次编译的唯一标识 */
    public String getSourceHash() {
        return sourceHash;
    }

    /**
     * 基于每次编译生成的唯一标识判等。
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CompiledGroovyScript that = (CompiledGroovyScript) o;
        return Objects.equals(sourceHash, that.sourceHash);
    }

    /** 与 {@link #equals} 一致，基于本次编译的唯一标识 */
    @Override
    public int hashCode() {
        return sourceHash != null ? sourceHash.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "CompiledGroovyScript{sourceHash='" + sourceHash + "', sourceLength=" + (source != null ? source.length() : 0) + '}';
    }
}
