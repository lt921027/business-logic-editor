package com.businesslogic.groovy.security;

import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.CodeVisitorSupport;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.DoWhileStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.ForStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.ast.stmt.WhileStatement;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;

/**
 * 编译期向脚本注入"循环/闭包预算检查"的 customizer（运行时防护的第一道闸）。
 *
 * <p>在脚本的每个循环体（while/do-while/for）和每个闭包体（{@code .each{}}、
 * {@code .times{}}、递归 lambda 等）入口插入一行：
 * <pre>__sandboxLoopGuard__.check()</pre>
 * 其中 {@code __sandboxLoopGuard__} 是 {@link GroovyExpressionEngine} 每次执行前注入 Binding 的
 * {@link LoopBudget} 实例，超限时由 {@link LoopBudget#check()} 自身抛出
 * {@link LoopBudgetExceededException}。这样即便脚本没有 while 语句（如 `(1..2000000000).each{}`），
 * 每次迭代也会消耗预算，超限立即抛异常退出，而不是无限占用 CPU。
 *
 * <p>为何注入单行方法调用而非 if/throw：注入的 AST 节点同样会经过 SecureASTCustomizer 复查，
 * 构造器（new RuntimeException）不在 imports 白名单内会被拦；而 Binding 动态变量上的方法调用
 * （接收者类型 Object）在白名单放行范围内，天然满足沙箱约束。
 *
 * <p>覆盖范围：脚本主语句块（即 run() 方法体）与脚本类上定义的方法内的循环/闭包；
 * 脚本内声明的自定义类（class Foo {...}）不注入，但其循环若引用守卫变量会因
 * 变量不可解析而抛异常中止，不会无限执行。
 */
public class LoopBudgetCustomizer extends CompilationCustomizer {

    /** Binding 中预算对象的变量名（与 {@link GroovyExpressionEngine} 注入名保持一致） */
    public static final String GUARD_VAR = "__sandboxLoopGuard__";

    public LoopBudgetCustomizer() {
        super(CompilePhase.CONVERSION);
    }

    @Override
    public void call(SourceUnit source, GeneratorContext context, ClassNode classNode) {
        ModuleNode module = source.getAST();
        Statement scriptBlock = module.getStatementBlock();

        if (scriptBlock != null) {
            scriptBlock.visit(new GuardInjector());
        }

        // 脚本类上定义的方法（run 之外的用户方法，如 def foo() {...}）
        for (MethodNode method : classNode.getMethods()) {
            if (method.getCode() != null && method.getCode() != scriptBlock) {
                method.getCode().visit(new GuardInjector());
            }
        }
    }

    /**
     * 遍历 AST，给循环体/闭包体注入预算检查。
     */
    private static class GuardInjector extends CodeVisitorSupport {

        @Override
        public void visitWhileLoop(WhileStatement loop) {
            prependGuard(loop.getLoopBlock());
            super.visitWhileLoop(loop);
        }

        @Override
        public void visitDoWhileLoop(DoWhileStatement loop) {
            prependGuard(loop.getLoopBlock());
            super.visitDoWhileLoop(loop);
        }

        @Override
        public void visitForLoop(ForStatement loop) {
            prependGuard(loop.getLoopBlock());
            super.visitForLoop(loop);
        }

        @Override
        public void visitClosureExpression(ClosureExpression expression) {
            prependGuard(expression.getCode());
            super.visitClosureExpression(expression);
        }

        /**
         * 在语句块开头插入预算检查；非块语句先包装成块。
         */
        private void prependGuard(Statement body) {
            BlockStatement block;
            if (body instanceof BlockStatement) {
                block = (BlockStatement) body;
            } else {
                block = new BlockStatement();
                block.addStatement(body);
            }
            block.getStatements().add(0, buildGuardStatement());
        }

        /** 构造：__sandboxLoopGuard__.check()（超限由 check() 自身抛异常） */
        private Statement buildGuardStatement() {
            return new ExpressionStatement(new MethodCallExpression(
                    new VariableExpression(GUARD_VAR),
                    "check",
                    MethodCallExpression.NO_ARGUMENTS));
        }
    }
}
