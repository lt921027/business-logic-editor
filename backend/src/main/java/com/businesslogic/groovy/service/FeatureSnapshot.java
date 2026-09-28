package com.businesslogic.groovy.service;

import com.businesslogic.entity.LogicStep;
import java.util.List;

/**
 * 特征快照：一次查询同时产出表达式与步骤。
 *
 * <p>供操作日志切面使用。切面需要在"改前/改后"各存一份快照，每份包含 Groovy 表达式
 * 与步骤的原样实体。若表达式和步骤分别去查，步骤表会被重复查询一次；
 * 由本对象统一承载后，每次快照只需一次主表查询 + 一次步骤查询。</p>
 */
public class FeatureSnapshot {

    /** Groovy 表达式（business_logic.groovy_expression） */
    private final String groovyExpression;

    /** 步骤的原样实体，字段与 logic_step 表一致 */
    private final List<LogicStep> steps;

    public FeatureSnapshot(String groovyExpression, List<LogicStep> steps) {
        this.groovyExpression = groovyExpression;
        this.steps = steps;
    }

    public String getGroovyExpression() {
        return groovyExpression;
    }

    public List<LogicStep> getSteps() {
        return steps;
    }
}
