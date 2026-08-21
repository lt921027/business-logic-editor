package com.businesslogic.groovy.security;

/**
 * 脚本循环/闭包调用预算计数器（运行时防护）。
 *
 * <p>为何需要：AST 层的语句黑名单只能拦掉 while/do-while，拦不住
 * `(1..N).each {}`、`N.times {}`、递归闭包这类"没有循环语句"的迭代路径。
 * 每次执行脚本前由 {@link com.businesslogic.groovy.engine.GroovyExpressionEngine} 在 Binding
 * 中注入一个新的预算实例，脚本内每个循环体/闭包体入口（由 {@link LoopBudgetCustomizer}
 * 编译期注入）调用 {@link #check()}，累计超过上限后持续返回 false，脚本随即抛异常退出，
 * 避免 while(true){} 之类的脚本无限循环挂死请求线程（DoS）。
 *
 * <p>为何按执行隔离：编译后的脚本类会被缓存复用（多请求共享），预算必须跟随单次执行生命周期，
 * 因此每次 {@code execute} 都 new 一个实例，不做线程共享。
 *
 * <p>线程安全：单次脚本执行是单线程的（配合执行超时也只在单个 worker 线程上运行），
 * 无需额外同步；count 用 volatile 兜底，防止未来脚本并发调用时读到过期值。
 */
public class LoopBudget {

    /** 预算上限 */
    private final int limit;

    /** 已执行次数 */
    private volatile int count;

    /**
     * @param limit 允许的最大循环/闭包调用次数，至少为 1
     */
    public LoopBudget(int limit) {
        this.limit = Math.max(1, limit);
    }

    /**
     * 每次进入循环体/闭包体时调用一次。
     *
     * @return true 表示仍在预算内
     * @throws LoopBudgetExceededException 超过预算上限时抛出，脚本随即终止
     */
    public boolean check() {
        if (++count > limit) {
            throw new LoopBudgetExceededException();
        }
        return true;
    }

    public int getLimit() {
        return limit;
    }
}
