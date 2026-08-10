package com.businesslogic.hotload;

import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Expression;
import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.function.AbstractVariadicFunction;
import com.googlecode.aviator.runtime.type.AviatorFunction;
import com.googlecode.aviator.runtime.type.AviatorObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AviatorFunctionRegistry {

    private static final Logger logger = LoggerFactory.getLogger(AviatorFunctionRegistry.class);

    private final AviatorEvaluatorInstance evaluator;
    private final Map<String, FunctionDefinition> functionDefinitions;
    private final Map<String, AviatorFunction> functionInstances;
    private volatile boolean initialized = false;

    public AviatorFunctionRegistry(AviatorEvaluatorInstance evaluator) {
        this.evaluator = evaluator;
        this.functionDefinitions = new ConcurrentHashMap<>();
        this.functionInstances = new ConcurrentHashMap<>();
    }

    public synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        logger.info("AviatorFunctionRegistry initialized");
    }

    public void registerExpressionFunction(String name, String expression, String... params) {
        FunctionDefinition definition = FunctionDefinition.builder()
                .name(name)
                .type(FunctionType.EXPRESSION)
                .expression(expression)
                .params(params)
                .build();

        functionDefinitions.put(name, definition);
        AviatorFunction instance = new ExpressionFunction(definition);
        functionInstances.put(name, instance);
        evaluator.addFunction(instance);
        logger.info("Registered expression function: {}", name);
    }

    public void registerScriptFunction(String name, String script, String... params) {
        FunctionDefinition definition = FunctionDefinition.builder()
                .name(name)
                .type(FunctionType.SCRIPT)
                .script(script)
                .params(params)
                .build();

        functionDefinitions.put(name, definition);
        AviatorFunction instance = new ScriptFunction(definition);
        functionInstances.put(name, instance);
        evaluator.addFunction(instance);
        logger.info("Registered script function: {}", name);
    }

    public void registerJavaFunction(String name, AbstractFunction function) {
        FunctionDefinition definition = FunctionDefinition.builder()
                .name(name)
                .type(FunctionType.JAVA)
                .javaFunction(function)
                .build();

        functionDefinitions.put(name, definition);
        functionInstances.put(name, function);
        evaluator.addFunction(function);
        logger.info("Registered java function: {}", name);
    }

    public void registerStaticFunctions(String prefix, Class<?> clazz) {
        try {
            evaluator.addStaticFunctions(prefix, clazz);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        logger.info("Registered static functions from {} with prefix '{}'", clazz.getName(), prefix);
    }

    public void updateFunction(String name, String newExpression, String... params) {
        FunctionDefinition newDef = FunctionDefinition.builder()
                .name(name)
                .type(FunctionType.EXPRESSION)
                .expression(newExpression)
                .params(params)
                .build();

        updateFunctionInternal(name, newDef);
    }

    public void updateScriptFunction(String name, String newScript, String... params) {
        FunctionDefinition newDef = FunctionDefinition.builder()
                .name(name)
                .type(FunctionType.SCRIPT)
                .script(newScript)
                .params(params)
                .build();

        updateFunctionInternal(name, newDef);
    }

    public void updateJavaFunction(String name, AbstractFunction newFunction) {
        FunctionDefinition newDef = FunctionDefinition.builder()
                .name(name)
                .type(FunctionType.JAVA)
                .javaFunction(newFunction)
                .build();

        updateFunctionInternal(name, newDef);
    }

    private void updateFunctionInternal(String name, FunctionDefinition newDefinition) {
        FunctionDefinition oldDef = functionDefinitions.get(name);
        if (oldDef == null) {
            throw new IllegalArgumentException("Function not found: " + name);
        }

        unregisterFunction(name);

        functionDefinitions.put(name, newDefinition);
        AviatorFunction instance = createFunctionInstance(newDefinition);
        functionInstances.put(name, instance);
        evaluator.addFunction(instance);
        logger.info("Updated function: {} with type: {}", name, newDefinition.getType());
    }

    private AviatorFunction createFunctionInstance(FunctionDefinition definition) {
        switch (definition.getType()) {
            case EXPRESSION:
                return new ExpressionFunction(definition);
            case SCRIPT:
                return new ScriptFunction(definition);
            case JAVA:
                return definition.getJavaFunction();
            default:
                throw new IllegalArgumentException("Unsupported function type: " + definition.getType());
        }
    }

    public void unregisterFunction(String name) {
        AviatorFunction removed = functionInstances.remove(name);
        if (removed != null) {
            logger.info("Unregistered function: {}", name);
        }
        functionDefinitions.remove(name);
    }

    public FunctionDefinition getFunctionDefinition(String name) {
        return functionDefinitions.get(name);
    }

    public AviatorFunction getFunctionInstance(String name) {
        return functionInstances.get(name);
    }

    public boolean hasFunction(String name) {
        return functionDefinitions.containsKey(name);
    }

    public java.util.Set<String> getAllFunctionNames() {
        return new java.util.HashSet<>(functionDefinitions.keySet());
    }

    private static class ExpressionFunction extends AbstractVariadicFunction {
        private final FunctionDefinition definition;
        private volatile Expression compiledExpression;

        ExpressionFunction(FunctionDefinition definition) {
            this.definition = definition;
        }

        @Override
        public AviatorObject variadicCall(Map<String, Object> env, AviatorObject... args) {
            try {
                Expression expr = getCompiledExpression();
                Map<String, Object> funcEnv = new java.util.HashMap<>(env);

                String[] params = definition.getParams();
                if (params != null) {
                    for (int i = 0; i < Math.min(params.length, args.length); i++) {
                        funcEnv.put(params[i], args[i].getValue(env));
                    }
                }

                Object result = expr.execute(funcEnv);
                return new com.googlecode.aviator.runtime.type.AviatorRuntimeJavaType(result);
            } catch (Exception e) {
                throw new RuntimeException("Expression function execution failed: " + definition.getName(), e);
            }
        }

        private Expression getCompiledExpression() {
            if (compiledExpression == null) {
                synchronized (this) {
                    if (compiledExpression == null) {
                        compiledExpression = com.googlecode.aviator.AviatorEvaluator.compile(definition.getExpression());
                    }
                }
            }
            return compiledExpression;
        }

        @Override
        public String getName() {
            return definition.getName();
        }
    }

    private static class ScriptFunction extends AbstractVariadicFunction {
        private final FunctionDefinition definition;
        private volatile Expression compiledScript;

        ScriptFunction(FunctionDefinition definition) {
            this.definition = definition;
        }

        @Override
        public AviatorObject variadicCall(Map<String, Object> env, AviatorObject... args) {
            try {
                Expression expr = getCompiledScript();
                Map<String, Object> funcEnv = new java.util.HashMap<>(env);

                String[] params = definition.getParams();
                if (params != null) {
                    for (int i = 0; i < Math.min(params.length, args.length); i++) {
                        funcEnv.put(params[i], args[i].getValue(env));
                    }
                }

                Object result = expr.execute(funcEnv);
                return new com.googlecode.aviator.runtime.type.AviatorRuntimeJavaType(result);
            } catch (Exception e) {
                throw new RuntimeException("Script function execution failed: " + definition.getName(), e);
            }
        }

        private Expression getCompiledScript() {
            if (compiledScript == null) {
                synchronized (this) {
                    if (compiledScript == null) {
                        // 参数已在 call() 中写入执行环境（env），脚本内直接按参数名引用即可。
                        // 不要在这里拼 "let p1, p2;" 之类的声明：Aviator 5.x 语法不支持
                        // 无初始化值的 let 声明（多参数会直接编译报错），且会以 nil 遮蔽 env 中的实参。
                        compiledScript = com.googlecode.aviator.AviatorEvaluator.compile(definition.getScript());
                    }
                }
            }
            return compiledScript;
        }

        @Override
        public String getName() {
            return definition.getName();
        }
    }
}
