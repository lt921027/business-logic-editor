package com.businesslogic.hotload;

import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Expression;
import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.type.AviatorObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AviatorFunctionRegistry {

    private static final Logger logger = LoggerFactory.getLogger(AviatorFunctionRegistry.class);

    private final AviatorEvaluatorInstance evaluator;
    private final Map<String, FunctionDefinition> functionDefinitions;
    private final Map<String, AbstractFunction> functionInstances;
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
        AbstractFunction instance = new ExpressionFunction(definition);
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
        AbstractFunction instance = new ScriptFunction(definition);
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
        AbstractFunction instance = createFunctionInstance(newDefinition);
        functionInstances.put(name, instance);
        evaluator.addFunction(instance);
        logger.info("Updated function: {} with type: {}", name, newDefinition.getType());
    }

    private AbstractFunction createFunctionInstance(FunctionDefinition definition) {
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
        AbstractFunction removed = functionInstances.remove(name);
        if (removed != null) {
            logger.info("Unregistered function: {}", name);
        }
        functionDefinitions.remove(name);
    }

    public FunctionDefinition getFunctionDefinition(String name) {
        return functionDefinitions.get(name);
    }

    public AbstractFunction getFunctionInstance(String name) {
        return functionInstances.get(name);
    }

    public boolean hasFunction(String name) {
        return functionDefinitions.containsKey(name);
    }

    public java.util.Set<String> getAllFunctionNames() {
        return new java.util.HashSet<>(functionDefinitions.keySet());
    }

    private static class ExpressionFunction extends AbstractFunction {
        private final FunctionDefinition definition;
        private volatile Expression compiledExpression;

        ExpressionFunction(FunctionDefinition definition) {
            this.definition = definition;
        }

        public AviatorObject call(Map<String, Object> env, AviatorObject... args) {
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

    private static class ScriptFunction extends AbstractFunction {
        private final FunctionDefinition definition;
        private volatile Expression compiledScript;

        ScriptFunction(FunctionDefinition definition) {
            this.definition = definition;
        }

        public AviatorObject call(Map<String, Object> env, AviatorObject... args) {
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
                        String fullScript = buildFullScript();
                        compiledScript = com.googlecode.aviator.AviatorEvaluator.compile(fullScript);
                    }
                }
            }
            return compiledScript;
        }

        private String buildFullScript() {
            StringBuilder sb = new StringBuilder();
            String[] params = definition.getParams();
            if (params != null && params.length > 0) {
                sb.append("let ").append(String.join(", ", params)).append(";\n");
            }
            sb.append(definition.getScript());
            return sb.toString();
        }

        @Override
        public String getName() {
            return definition.getName();
        }
    }
}