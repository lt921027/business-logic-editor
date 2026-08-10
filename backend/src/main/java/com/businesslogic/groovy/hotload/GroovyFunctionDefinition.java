package com.businesslogic.groovy.hotload;

import java.util.Arrays;

/**
 * Groovy 函数定义
 *
 * <p>对应 Aviator 的 FunctionDefinition，描述一个可热加载的函数配置。
 *
 * <p>与 Aviator 版本的差异：
 * <ul>
 *   <li>javaFunction 字段类型从 AbstractFunction 改为 {@link GroovyFunction}</li>
 *   <li>移除对 Aviator 类型的依赖</li>
 * </ul>
 *
 * <p>关联体系：
 * <ul>
 *   <li>由 {@link GroovyFunctionRegistry} 的注册方法（register*）创建并保存到 functionDefinitions Map</li>
 *   <li>被 {@link GroovyFunctionRegistry#applyRegistration} 读取 type 字段以分支选择 Closure 包装类</li>
 *   <li>EXPRESSION 类型：{@link #expression} 字段被 {@link GroovyFunctionRegistry#compileExpression} 编译</li>
 *   <li>SCRIPT 类型：{@link #script} 被直接编译，参数经 {@link #params} 在调用时注入 Binding</li>
 *   <li>JAVA 类型：{@link #javaFunction} 字段被 {@link GroovyFunctionRegistry.JavaFunctionClosure} 直接包装</li>
 *   <li>{@link #version} 字段用于热更新时检测变更（参考 Aviator 版本的版本号设计）</li>
 * </ul>
 *
 * <p>采用 Builder 模式以避免构造参数过多导致的可读性问题，与 Aviator 版本保持一致。
 */
public class GroovyFunctionDefinition {

    /**
     * 函数名称（唯一标识，作为注册中心 Map 的 key）
     */
    private String name;

    /**
     * 函数类型
     *
     * <p>决定使用哪个字段（expression/script/javaFunction）以及哪种 Closure 包装。
     */
    private GroovyFunctionType type;

    /**
     * 表达式内容（type=EXPRESSION 时使用）
     *
     * <p>例如 "a + b * 0.1"，编译后由 {@link GroovyFunctionRegistry.ExpressionFunctionClosure} 调用。
     */
    private String expression;

    /**
     * 脚本内容（type=SCRIPT 时使用）
     *
     * <p>多行 Groovy 脚本，直接编译为 {@link com.businesslogic.groovy.engine.CompiledGroovyScript}；
     * 脚本内按 {@link #params} 中的参数名直接引用，实参在调用时注入 Binding。
     */
    private String script;

    /**
     * 参数名称列表
     *
     * <p>对应 Closure.call(Object... args) 时按顺序绑定的变量名，env 中将以 params[i]=args[i] 形式注入。
     * 仅 EXPRESSION/SCRIPT 类型使用。
     */
    private String[] params;

    /**
     * Java 函数实例（type=JAVA 时使用）
     *
     * <p>无需编译，由 {@link GroovyFunctionRegistry.JavaFunctionClosure} 直接持有并委托调用。
     */
    private GroovyFunction javaFunction;

    /**
     * 函数描述
     */
    private String description;

    /**
     * 版本号（用于变更检测）
     *
     * <p>热更新时可通过比较版本号决定是否需要重新编译；Aviator 版本同样使用此字段。
     */
    private String version;

    /**
     * 是否启用
     *
     * <p>默认 true。设置为 false 时 Closure 仍存在但调用方可通过此标志跳过执行
     * （当前实现未强制禁用，保留字段以与 Aviator 版本对齐）。
     */
    private boolean enabled = true;

    /**
     * 默认构造器（供反序列化等场景使用）。
     */
    public GroovyFunctionDefinition() {
    }

    /**
     * 私有构造器，仅供 {@link Builder#build} 使用，确保所有字段通过 Builder 设置。
     */
    private GroovyFunctionDefinition(Builder builder) {
        this.name = builder.name;
        this.type = builder.type;
        this.expression = builder.expression;
        this.script = builder.script;
        this.params = builder.params;
        this.javaFunction = builder.javaFunction;
        this.description = builder.description;
        this.version = builder.version;
        this.enabled = builder.enabled;
    }

    /**
     * 创建 Builder 实例，开始构建函数定义。
     *
     * @return 新的 Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public GroovyFunctionType getType() {
        return type;
    }

    public void setType(GroovyFunctionType type) {
        this.type = type;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    public String[] getParams() {
        return params;
    }

    public void setParams(String[] params) {
        this.params = params;
    }

    public GroovyFunction getJavaFunction() {
        return javaFunction;
    }

    public void setJavaFunction(GroovyFunction javaFunction) {
        this.javaFunction = javaFunction;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 相等性判断：所有字段均参与比较。
     *
     * <p>为何需要 equals：热更新流程中会比较新旧定义是否一致以决定是否需要重新编译，
     * params 数组使用 {@link Arrays#equals} 而非默认引用比较。
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GroovyFunctionDefinition that = (GroovyFunctionDefinition) o;

        if (enabled != that.enabled) return false;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (type != that.type) return false;
        if (expression != null ? !expression.equals(that.expression) : that.expression != null) return false;
        if (script != null ? !script.equals(that.script) : that.script != null) return false;
        if (params != null ? !Arrays.equals(params, that.params) : that.params != null) return false;
        if (javaFunction != null ? !javaFunction.equals(that.javaFunction) : that.javaFunction != null) return false;
        if (description != null ? !description.equals(that.description) : that.description != null) return false;
        return version != null ? version.equals(that.version) : that.version == null;
    }

    /**
     * 哈希码：与 equals 对齐，所有字段参与计算，params 用 {@link Arrays#hashCode}。
     */
    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (expression != null ? expression.hashCode() : 0);
        result = 31 * result + (script != null ? script.hashCode() : 0);
        result = 31 * result + (params != null ? Arrays.hashCode(params) : 0);
        result = 31 * result + (javaFunction != null ? javaFunction.hashCode() : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (version != null ? version.hashCode() : 0);
        result = 31 * result + (enabled ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "GroovyFunctionDefinition{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", expression='" + expression + '\'' +
                ", script='" + script + '\'' +
                ", params=" + Arrays.toString(params) +
                ", javaFunction=" + javaFunction +
                ", description='" + description + '\'' +
                ", version='" + version + '\'' +
                ", enabled=" + enabled +
                '}';
    }

    /**
     * Builder 模式构建器
     *
     * <p>为何用 Builder：函数定义字段较多（9 个），且多数为可选字段，构造器重载会爆炸。
     * Builder 模式保持调用代码可读性，同时支持链式调用。
     */
    public static class Builder {
        private String name;
        private GroovyFunctionType type;
        private String expression;
        private String script;
        private String[] params;
        private GroovyFunction javaFunction;
        private String description;
        private String version;
        private boolean enabled = true;

        private Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder type(GroovyFunctionType type) {
            this.type = type;
            return this;
        }

        public Builder expression(String expression) {
            this.expression = expression;
            return this;
        }

        public Builder script(String script) {
            this.script = script;
            return this;
        }

        public Builder params(String[] params) {
            this.params = params;
            return this;
        }

        public Builder javaFunction(GroovyFunction javaFunction) {
            this.javaFunction = javaFunction;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * 构建不可变的 GroovyFunctionDefinition 实例。
         *
         * @return 新的函数定义实例
         */
        public GroovyFunctionDefinition build() {
            return new GroovyFunctionDefinition(this);
        }
    }
}
