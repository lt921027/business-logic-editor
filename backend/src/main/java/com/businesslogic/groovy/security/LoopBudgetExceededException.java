package com.businesslogic.groovy.security;

/**
 * 脚本循环/闭包执行次数超过预算上限时抛出的异常。
 *
 * <p>由 {@link LoopBudget#check()} 抛出，经执行引擎包装后对外暴露；
 * 消息包含固定的中文标识，供 sandbox-test 等接口分类为 LOOP_LIMIT。
 */
public class LoopBudgetExceededException extends RuntimeException {

    /** 固定消息（同时作为分类标识，见 GroovyEngineTestController#sandboxTest） */
    public static final String MESSAGE = "脚本循环/闭包执行次数超过上限";

    public LoopBudgetExceededException() {
        super(MESSAGE);
    }
}
