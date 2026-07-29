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
 * 被 {@link GroovyExpressionEngine#execute(CompiledGroovyScript, Map)} 执行；
 * 被 {@link GroovyExpressionCache} / {@link com.businesslogic.groovy.redisCache.GroovyRedisExpressionCache}
 * 等缓存层持有；被 {@link com.businesslogic.groovy.hotload.GroovyFunctionRegistry} 内部 Closure 调用。
 */
public class CompiledGroovyScript {

    /** 编译后的 Script Class（由 GroovyClassLoader.parseClass 产生，沙箱已注入安全策略） */
    private final Class<? extends Script> scriptClass;

    /** 原始源码（用于序列化和缓存：Groovy Class 不可序列化，存储/恢复以源码字符串为载体） */
    private final String source;

    /** 源码的 MD5 hash 值（用作 {@link GroovyExpressionEngine#compile(String)} 的编译缓存 key） */
    private final String sourceHash;

    /**
     * 构造编译后的脚本包装对象。
     *
     * @param scriptClass 已编译的 Script 子类
     * @param source      原始源码字符串
     * @param sourceHash  源码 MD5（由 {@link GroovyExpressionEngine#md5(String)} 计算）
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
     * <p>关联：被 {@link GroovyExpressionEngine#execute(CompiledGroovyScript, Map)} 调用。
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

    /** @return 源码 hash，被 equals/hashCode 用于缓存层比对 */
    public String getSourceHash() {
        return sourceHash;
    }

    /**
     * 基于 sourceHash 判等：两份源码 hash 相同即视为同一编译结果。
     *
     * <p>为何如此设计：Groovy Class 对象身份敏感（每次 parseClass 会生成新 Class），
     * 直接比较 Class 不稳定；改用源码 hash 可让缓存命中判断变得确定性。
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CompiledGroovyScript that = (CompiledGroovyScript) o;
        return Objects.equals(sourceHash, that.sourceHash);
    }

    /** 与 {@link #equals} 一致，基于 sourceHash */
    @Override
    public int hashCode() {
        return sourceHash != null ? sourceHash.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "CompiledGroovyScript{sourceHash='" + sourceHash + "', sourceLength=" + (source != null ? source.length() : 0) + '}';
    }
}
