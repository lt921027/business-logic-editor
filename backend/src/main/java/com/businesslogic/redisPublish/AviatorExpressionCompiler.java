package com.businesslogic.redisPublish;

import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Expression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class AviatorExpressionCompiler {

    private static final Logger logger = LoggerFactory.getLogger(AviatorExpressionCompiler.class);

    private static final int MAX_RETRY_COUNT = 3;
    private static final long RETRY_DELAY_MS = 500;

    private final ConcurrentHashMap<String, CompiledExpression> cache = new ConcurrentHashMap<>();

    private final AtomicReference<CompiledExpression> currentExpression = new AtomicReference<>();

    public CompileResult compileWithRetry(String expressionId, Long version, String expression) {
        AviatorEvaluatorInstance evaluator = com.googlecode.aviator.AviatorEvaluator.getInstance();

        for (int attempt = 1; attempt <= MAX_RETRY_COUNT; attempt++) {
            try {
                logger.info("编译表达[{}] v{}, {}/{} 次尝试", expressionId, version, attempt, MAX_RETRY_COUNT);

                Expression compiled = evaluator.compile(expression);

                CompiledExpression ce = new CompiledExpression(expressionId, version, expression, compiled);
                cache.put(expressionId, ce);
                currentExpression.set(ce);

                logger.info("表达[{}] v{} 编译成功", expressionId, version);
                return CompileResult.success(ce);

            } catch (Exception e) {
                logger.error("表达[{}] v{} {}/{} 次编译失 {}", expressionId, version, attempt, MAX_RETRY_COUNT, e.getMessage());

                if (attempt < MAX_RETRY_COUNT) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return CompileResult.failure("编译被中断 " + ie.getMessage());
                    }
                }
            }
        }

        logger.error("表达[{}] v{} 编译最终失败，已重{}", expressionId, version, MAX_RETRY_COUNT);
        return CompileResult.failure("编译失败，已重试 " + MAX_RETRY_COUNT + " 次");
    }

    public CompiledExpression getCompiled(String expressionId) {
        return cache.get(expressionId);
    }

    public CompiledExpression getCurrentExpression() {
        return currentExpression.get();
    }

    public void removeCompiled(String expressionId) {
        cache.remove(expressionId);
    }

    public void clearAll() {
        cache.clear();
        currentExpression.set(null);
    }

    public static class CompiledExpression {
        private final String expressionId;
        private final Long version;
        private final String sourceExpression;
        private final Expression compiledExpression;

        public CompiledExpression(String expressionId, Long version, String sourceExpression, Expression compiledExpression) {
            this.expressionId = expressionId;
            this.version = version;
            this.sourceExpression = sourceExpression;
            this.compiledExpression = compiledExpression;
        }

        public String getExpressionId() { return expressionId; }
        public Long getVersion() { return version; }
        public String getSourceExpression() { return sourceExpression; }
        public Expression getCompiledExpression() { return compiledExpression; }
    }

    public static class CompileResult {
        private final boolean success;
        private final CompiledExpression compiledExpression;
        private final String errorMessage;

        private CompileResult(boolean success, CompiledExpression compiledExpression, String errorMessage) {
            this.success = success;
            this.compiledExpression = compiledExpression;
            this.errorMessage = errorMessage;
        }

        public static CompileResult success(CompiledExpression ce) {
            return new CompileResult(true, ce, null);
        }

        public static CompileResult failure(String errorMessage) {
            return new CompileResult(false, null, errorMessage);
        }

        public boolean isSuccess() { return success; }
        public CompiledExpression getCompiledExpression() { return compiledExpression; }
        public String getErrorMessage() { return errorMessage; }
    }
}