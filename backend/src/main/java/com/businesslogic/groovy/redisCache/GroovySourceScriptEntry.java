package com.businesslogic.groovy.redisCache;

import com.businesslogic.groovy.engine.CompiledGroovyScript;

import java.util.Objects;

/**
 * 单个源报文对应的本地编译缓存条目。
 *
 * <p>只持有源报文编号、版本号和编译后的整体脚本，不再维护特征级 Map。</p>
 */
public class GroovySourceScriptEntry {

    /** 源报文编号 */
    private final String sourceNo;

    /** 源报文当前版本号 */
    private final long version;

    /** 编译后的整体 Groovy 脚本 */
    private final CompiledGroovyScript script;

    public GroovySourceScriptEntry(String sourceNo, long version, CompiledGroovyScript script) {
        this.sourceNo = sourceNo;
        this.version = version;
        this.script = script;
    }

    public String getSourceNo() {
        return sourceNo;
    }

    public long getVersion() {
        return version;
    }

    public CompiledGroovyScript getScript() {
        return script;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GroovySourceScriptEntry that = (GroovySourceScriptEntry) o;
        return version == that.version
                && Objects.equals(sourceNo, that.sourceNo)
                && Objects.equals(script, that.script);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceNo, version, script);
    }

    @Override
    public String toString() {
        return "GroovySourceScriptEntry{"
                + "sourceNo='" + sourceNo + '\''
                + ", version=" + version
                + ", script=" + script
                + '}';
    }
}
