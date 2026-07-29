package com.businesslogic.hotload;

import com.googlecode.aviator.runtime.function.AbstractFunction;

/**
 * 函数定义
 *
 * 描述一个可热加载的函数配置
 */
public class FunctionDefinition {

    /**
     * 函数名称
     */
    private String name;

    /**
     * 函数类型
     */
    private FunctionType type;

    /**
     * 表达式内容（type=EXPRESSION 时使用）
     */
    private String expression;

    /**
     * 脚本内容（type=SCRIPT 时使用）
     */
    private String script;

    /**
     * 参数名称列表
     */
    private String[] params;

    /**
     * Java 函数实例（type=JAVA 时使用）
     */
    private AbstractFunction javaFunction;

    /**
     * 函数描述
     */
    private String description;

    /**
     * 版本号（用于变更检测）
     */
    private String version;

    /**
     * 是否启用
     */
    private boolean enabled = true;

    public FunctionDefinition() {
    }

    private FunctionDefinition(Builder builder) {
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

    public static Builder builder() {
        return new Builder();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public FunctionType getType() {
        return type;
    }

    public void setType(FunctionType type) {
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

    public AbstractFunction getJavaFunction() {
        return javaFunction;
    }

    public void setJavaFunction(AbstractFunction javaFunction) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FunctionDefinition that = (FunctionDefinition) o;

        if (enabled != that.enabled) return false;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (type != that.type) return false;
        if (expression != null ? !expression.equals(that.expression) : that.expression != null) return false;
        if (script != null ? !script.equals(that.script) : that.script != null) return false;
        if (params != null ? !params.equals(that.params) : that.params != null) return false;
        if (javaFunction != null ? !javaFunction.equals(that.javaFunction) : that.javaFunction != null) return false;
        if (description != null ? !description.equals(that.description) : that.description != null) return false;
        return version != null ? version.equals(that.version) : that.version == null;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (expression != null ? expression.hashCode() : 0);
        result = 31 * result + (script != null ? script.hashCode() : 0);
        result = 31 * result + (params != null ? params.hashCode() : 0);
        result = 31 * result + (javaFunction != null ? javaFunction.hashCode() : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (version != null ? version.hashCode() : 0);
        result = 31 * result + (enabled ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "FunctionDefinition{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", expression='" + expression + '\'' +
                ", script='" + script + '\'' +
                ", params=" + java.util.Arrays.toString(params) +
                ", javaFunction=" + javaFunction +
                ", description='" + description + '\'' +
                ", version='" + version + '\'' +
                ", enabled=" + enabled +
                '}';
    }

    public static class Builder {
        private String name;
        private FunctionType type;
        private String expression;
        private String script;
        private String[] params;
        private AbstractFunction javaFunction;
        private String description;
        private String version;
        private boolean enabled = true;

        private Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder type(FunctionType type) {
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

        public Builder javaFunction(AbstractFunction javaFunction) {
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

        public FunctionDefinition build() {
            return new FunctionDefinition(this);
        }
    }
}
