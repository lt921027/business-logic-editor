package com.businesslogic.groovy.hotload;

import com.businesslogic.groovy.engine.CompiledGroovyScript;
import com.businesslogic.groovy.engine.GroovyExecutor;
import com.businesslogic.groovy.engine.GroovyExpressionEngine;
import groovy.lang.Closure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Groovy 自定义函数注册中心
 *
 * <p>对应 Aviator 的 AviatorFunctionRegistry，业务逻辑保持一致。
 *
 * <p>核心设计：
 * <ul>
 *   <li>EXPRESSION 类型：将表达式编译为 {@link CompiledGroovyScript}，运行时构造闭包调用</li>
 *   <li>SCRIPT 类型：将多行脚本（带参数声明）编译为 {@link CompiledGroovyScript}，运行时构造闭包调用</li>
 *   <li>JAVA 类型：直接注册 {@link GroovyFunction} 实例，包装为 Groovy Closure</li>
 *   <li>所有函数最终都以 {@link Closure} 形式注入到 {@link GroovyExpressionEngine} 的执行环境中</li>
 * </ul>
 *
 * <p>线程安全策略：
 * <ul>
 *   <li>注册/更新/注销操作使用 synchronized 保护，避免并发修改导致编译缓存与函数定义不一致</li>
 *   <li>查询操作走 ConcurrentHashMap，无锁</li>
 *   <li>EXPRESSION/SCRIPT 类型的编译结果缓存到 {@link #compiledCache}，避免每次调用都重新编译</li>
 * </ul>
 *
 * <p>关联体系：
 * <ul>
 *   <li>持有 {@link GroovyExpressionEngine} 引用，通过 {@link GroovyExpressionEngine#addFunction}
 *       将 Closure 注入到引擎的全局函数表，所有后续编译/执行的 Groovy 脚本均可调用</li>
 *   <li>被 {@link com.businesslogic.groovy.config.GroovyEngineConfig} 在启动时初始化</li>
 *   <li>被 {@link com.businesslogic.groovy.controller.GroovyFunctionRegistryController} 暴露为 REST 接口</li>
 *   <li>调用入口：业务脚本通过 `myFunc(arg1, arg2)` 语法触发，Groovy 引擎查找到对应 Closure 后调用其 call 方法</li>
 * </ul>
 */
public class GroovyFunctionRegistry {

    private static final Logger logger = LoggerFactory.getLogger(GroovyFunctionRegistry.class);

    /**
     * 引擎引用，用于编译表达式/脚本以及将 Closure 注入到全局函数表。
     */
    private final GroovyExpressionEngine engine;

    /**
     * 函数定义：name → {@link GroovyFunctionDefinition}
     *
     * <p>保存函数的元信息（类型、源码、参数等），供查询和热更新使用。
     */
    private final Map<String, GroovyFunctionDefinition> functionDefinitions;

    /**
     * 函数实例（Closure 包装）：name → Closure
     *
     * <p>保存已包装为 Closure 的可调用实例，业务脚本执行时引擎通过此 Map 查找函数。
     * 同时通过 {@link GroovyExpressionEngine#addFunction} 注入到引擎，保证两处同步。
     */
    private final Map<String, Closure<?>> functionInstances;

    /**
     * EXPRESSION/SCRIPT 类型的编译缓存：name → {@link CompiledGroovyScript}
     *
     * <p>为何单独缓存：避免每次 Closure.call 时都重新编译源码，编译开销大。
     * JAVA 类型不需要此缓存，因为 Java 函数无需编译。
     */
    private final Map<String, CompiledGroovyScript> compiledCache;

    /**
     * 初始化标志（幂等保护）。
     *
     * <p>为何用 volatile：init 方法加 synchronized 后写入，其他线程读取时需保证可见性。
     */
    private volatile boolean initialized = false;

    /**
     * 默认构造器：使用 {@link GroovyExecutor} 的单例引擎。
     */
    public GroovyFunctionRegistry() {
        this(GroovyExecutor.getEngine());
    }

    /**
     * 注入引擎的构造器（便于测试时替换引擎实现）。
     *
     * @param engine 已初始化的 Groovy 表达式引擎
     */
    public GroovyFunctionRegistry(GroovyExpressionEngine engine) {
        this.engine = engine;
        this.functionDefinitions = new ConcurrentHashMap<>();
        this.functionInstances = new ConcurrentHashMap<>();
        this.compiledCache = new ConcurrentHashMap<>();
    }

    /**
     * 初始化（幂等）。
     *
     * <p>为何需要 init：当前实现仅打印日志，预留扩展点（例如从数据库加载函数定义）。
     * synchronized + volatile 双重保护确保多线程场景下只执行一次。
     */
    public synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        logger.info("[GroovyFuncRegistry] initialized, engine compile cache size={}",
                engine.getCompileCacheSize());
    }

    // ==================== 注册函数 ====================

    /**
     * 注册表达式函数
     *
     * <p>为何提前编译：在注册阶段就触发编译，可尽早发现语法错误，避免在运行时才抛出异常
     * 导致业务调用失败。编译结果存入 {@link #compiledCache} 供 Closure 调用时复用。
     *
     * <p>关联：构造 {@link ExpressionFunctionClosure} 包装编译后的脚本；
     * 通过 {@link GroovyExpressionEngine#addFunction} 注入到引擎全局函数表。
     *
     * @param name       函数名（业务脚本中通过此名称调用）
     * @param expression Groovy 表达式（可引用 params 中的参数名）
     * @param params     参数名列表
     */
    public synchronized void registerExpressionFunction(String name, String expression, String... params) {
        GroovyFunctionDefinition definition = GroovyFunctionDefinition.builder()
                .name(name)
                .type(GroovyFunctionType.EXPRESSION)
                .expression(expression)
                .params(params)
                .build();

        functionDefinitions.put(name, definition);
        // 提前编译验证
        CompiledGroovyScript compiled = compileExpression(expression);
        compiledCache.put(name, compiled);

        Closure<?> closure = new ExpressionFunctionClosure(definition, compiled);
        functionInstances.put(name, closure);
        engine.addFunction(name, closure);

        logger.info("[GroovyFuncRegistry] Registered expression function: {}, params={}",
                name, params != null ? java.util.Arrays.toString(params) : "[]");
    }

    /**
     * 注册脚本函数
     *
     * <p>脚本中可直接引用参数名（如 `return a + b`）：实际参数值在
     * {@link ScriptFunctionClosure#call} 时按 params 顺序写入 env，
     * 最终注入到 Groovy 执行引擎的 Binding 中，脚本内直接按参数名读取。
     * 不要为参数拼接 `def a; def b;` 声明：那会把参数声明为 null 局部变量，
     * 遮蔽 Binding 中的实参，导致脚本内参数永远为 null。
     *
     * <p>关联：直接编译原始脚本，构造 {@link ScriptFunctionClosure} 包装。
     *
     * @param name   函数名
     * @param script Groovy 多行脚本
     * @param params 参数名列表
     */
    public synchronized void registerScriptFunction(String name, String script, String... params) {
        GroovyFunctionDefinition definition = GroovyFunctionDefinition.builder()
                .name(name)
                .type(GroovyFunctionType.SCRIPT)
                .script(script)
                .params(params)
                .build();

        functionDefinitions.put(name, definition);
        // 直接编译原始脚本；参数在 call() 时注入 Binding，脚本内按参数名引用
        CompiledGroovyScript compiled = compileExpression(script);
        compiledCache.put(name, compiled);

        Closure<?> closure = new ScriptFunctionClosure(definition, compiled);
        functionInstances.put(name, closure);
        engine.addFunction(name, closure);

        logger.info("[GroovyFuncRegistry] Registered script function: {}, params={}",
                name, params != null ? java.util.Arrays.toString(params) : "[]");
    }

    /**
     * 注册 Java 函数
     *
     * <p>为何 JAVA 类型不需要编译：Java 函数逻辑由 {@link GroovyFunction} 实现类提供，
     * 已编译为字节码，无需 Groovy 编译器介入。直接包装为 {@link JavaFunctionClosure} 即可。
     *
     * <p>关联：构造 {@link JavaFunctionClosure} 持有 GroovyFunction 实例；
     * 不写入 {@link #compiledCache}（无编译产物）。
     *
     * @param name     函数名
     * @param function GroovyFunction 实现
     */
    public synchronized void registerJavaFunction(String name, GroovyFunction function) {
        GroovyFunctionDefinition definition = GroovyFunctionDefinition.builder()
                .name(name)
                .type(GroovyFunctionType.JAVA)
                .javaFunction(function)
                .build();

        functionDefinitions.put(name, definition);
        Closure<?> closure = new JavaFunctionClosure(function);
        functionInstances.put(name, closure);
        engine.addFunction(name, closure);

        logger.info("[GroovyFuncRegistry] Registered java function: {}", name);
    }

    /**
     * 注册静态函数类
     *
     * <p>对应 Aviator 的 registerStaticFunctions，使用 Groovy 引擎的静态函数注册能力。
     *
     * <p>为何单独提供此方法：静态函数（如 {@link com.businesslogic.groovy.util.GroovyDateFunctions}）
     * 是按类批量注册，而非单个函数，与上述按 name 注册的流程不同，故不进入 functionDefinitions Map，
     * 直接委托 {@link GroovyExpressionEngine#addStaticFunctions}。
     *
     * @param prefix 调用前缀（Groovy 脚本中通过 prefix.method() 调用）
     * @param clazz  含静态方法的 Java 类
     */
    public void registerStaticFunctions(String prefix, Class<?> clazz) {
        engine.addStaticFunctions(prefix, clazz);
        logger.info("[GroovyFuncRegistry] Registered static functions from {} with prefix '{}'",
                clazz.getName(), prefix);
    }

    // ==================== 更新函数 ====================

    /**
     * 更新表达式函数（热更新）
     *
     * <p>实现策略：先 {@link #unregisterFunction} 注销旧定义（清理 compiledCache 和引擎中的 Closure），
     * 再用新表达式构造新定义并通过 {@link #applyRegistration} 注册。
     *
     * <p>为何不直接覆盖：unregister 会从引擎的 registeredFunctions 移除旧 Closure，
     * 避免 applyRegistration 时引擎报"函数已存在"。applyRegistration 内部不做注销，
     * 保证 update 流程中先注销后注册的顺序清晰。
     *
     * @param name          函数名
     * @param newExpression 新的 Groovy 表达式
     * @param params        新的参数名列表
     */
    public synchronized void updateFunction(String name, String newExpression, String... params) {
        GroovyFunctionDefinition oldDef = functionDefinitions.get(name);
        if (oldDef == null) {
            throw new IllegalArgumentException("Function not found: " + name);
        }

        unregisterFunction(name);

        GroovyFunctionDefinition newDef = GroovyFunctionDefinition.builder()
                .name(name)
                .type(GroovyFunctionType.EXPRESSION)
                .expression(newExpression)
                .params(params)
                .build();
        applyRegistration(newDef);

        logger.info("[GroovyFuncRegistry] Updated expression function: {}", name);
    }

    /**
     * 更新脚本函数（热更新）
     *
     * <p>流程同 {@link #updateFunction}，区别在于 SCRIPT 类型直接编译多行脚本。
     *
     * @param name      函数名
     * @param newScript 新的 Groovy 脚本
     * @param params    新的参数名列表
     */
    public synchronized void updateScriptFunction(String name, String newScript, String... params) {
        GroovyFunctionDefinition oldDef = functionDefinitions.get(name);
        if (oldDef == null) {
            throw new IllegalArgumentException("Function not found: " + name);
        }

        unregisterFunction(name);

        GroovyFunctionDefinition newDef = GroovyFunctionDefinition.builder()
                .name(name)
                .type(GroovyFunctionType.SCRIPT)
                .script(newScript)
                .params(params)
                .build();
        applyRegistration(newDef);

        logger.info("[GroovyFuncRegistry] Updated script function: {}", name);
    }

    /**
     * 更新 Java 函数（热更新）
     *
     * <p>流程同 {@link #updateFunction}，区别在于 JAVA 类型不需要编译，
     * {@link #applyRegistration} 中直接包装为 {@link JavaFunctionClosure}。
     *
     * @param name        函数名
     * @param newFunction 新的 GroovyFunction 实例
     */
    public synchronized void updateJavaFunction(String name, GroovyFunction newFunction) {
        GroovyFunctionDefinition oldDef = functionDefinitions.get(name);
        if (oldDef == null) {
            throw new IllegalArgumentException("Function not found: " + name);
        }

        unregisterFunction(name);

        GroovyFunctionDefinition newDef = GroovyFunctionDefinition.builder()
                .name(name)
                .type(GroovyFunctionType.JAVA)
                .javaFunction(newFunction)
                .build();
        applyRegistration(newDef);

        logger.info("[GroovyFuncRegistry] Updated java function: {}", name);
    }

    /**
     * 内部：应用注册（不触发注销）
     *
     * <p>为何单独抽出此方法：update* 方法已经先调用了 unregisterFunction，
     * 若再调用 register* 方法会导致重复检查和冗余日志。applyRegistration 只做"注册新定义"，
     * 不做注销，使 update 流程更清晰。
     *
     * <p>按 {@link GroovyFunctionType} 分支选择编译方式和 Closure 包装类：
     * <ul>
     *   <li>EXPRESSION：直接编译 expression 字段，包装为 {@link ExpressionFunctionClosure}</li>
     *   <li>SCRIPT：直接编译脚本，包装为 {@link ScriptFunctionClosure}</li>
     *   <li>JAVA：不编译，直接包装为 {@link JavaFunctionClosure}</li>
     * </ul>
     *
     * @param definition 新的函数定义
     */
    private void applyRegistration(GroovyFunctionDefinition definition) {
        String name = definition.getName();
        functionDefinitions.put(name, definition);

        Closure<?> closure;
        switch (definition.getType()) {
            case EXPRESSION: {
                CompiledGroovyScript compiled = compileExpression(definition.getExpression());
                compiledCache.put(name, compiled);
                closure = new ExpressionFunctionClosure(definition, compiled);
                break;
            }
            case SCRIPT: {
                CompiledGroovyScript compiled = compileExpression(definition.getScript());
                compiledCache.put(name, compiled);
                closure = new ScriptFunctionClosure(definition, compiled);
                break;
            }
            case JAVA: {
                closure = new JavaFunctionClosure(definition.getJavaFunction());
                break;
            }
            default:
                throw new IllegalArgumentException("Unsupported function type: " + definition.getType());
        }

        functionInstances.put(name, closure);
        engine.addFunction(name, closure);
    }

    // ==================== 注销/查询 ====================

    /**
     * 注销函数
     *
     * <p>清理三个 Map 中的记录，并通过 {@link GroovyExpressionEngine#removeFunction}
     * 从引擎全局函数表移除，使后续脚本调用此函数时报"函数未定义"。
     *
     * <p>为何同时清理 compiledCache：避免内存泄漏，旧编译产物不再被引用后应回收。
     *
     * @param name 函数名
     */
    public synchronized void unregisterFunction(String name) {
        Closure<?> removed = functionInstances.remove(name);
        if (removed != null) {
            engine.removeFunction(name);
            logger.info("[GroovyFuncRegistry] Unregistered function: {}", name);
        }
        functionDefinitions.remove(name);
        compiledCache.remove(name);
    }

    /**
     * 获取函数定义
     *
     * <p>供 {@link com.businesslogic.groovy.controller.GroovyFunctionRegistryController}
     * 查询函数详情使用。
     *
     * @param name 函数名
     * @return 函数定义，不存在返回 null
     */
    public GroovyFunctionDefinition getFunctionDefinition(String name) {
        return functionDefinitions.get(name);
    }

    /**
     * 获取函数实例（Closure）
     *
     * <p>供 {@link com.businesslogic.groovy.controller.GroovyFunctionRegistryController#testFunction}
     * 直接调用测试使用，绕过脚本编译直接调用 Closure.call。
     *
     * @param name 函数名
     * @return Closure 实例，不存在返回 null
     */
    public Closure<?> getFunctionInstance(String name) {
        return functionInstances.get(name);
    }

    /**
     * 是否已注册
     *
     * @param name 函数名
     * @return true 表示已注册
     */
    public boolean hasFunction(String name) {
        return functionDefinitions.containsKey(name);
    }

    /**
     * 获取所有函数名
     *
     * <p>为何返回 new HashSet 而非原 keySet：避免调用方修改影响内部状态。
     *
     * @return 函数名集合的拷贝
     */
    public Set<String> getAllFunctionNames() {
        return new HashSet<>(functionDefinitions.keySet());
    }

    /**
     * 获取已注册函数数量
     *
     * @return 函数数量
     */
    public int size() {
        return functionDefinitions.size();
    }

    // ==================== 工具方法 ====================

    /**
     * 编译表达式（语法错误会抛出 GroovyCompileException）
     *
     * <p>委托给 {@link GroovyExpressionEngine#compile}，引擎内部有 MD5 源码缓存，
     * 相同源码不会重复编译。
     *
     * @param expression Groovy 源码
     * @return 编译后的脚本
     */
    private CompiledGroovyScript compileExpression(String expression) {
        return engine.compile(expression);
    }

    // ==================== Closure 实现 ====================

    /**
     * 表达式函数 Closure
     *
     * <p>对应 Aviator 的 ExpressionFunction。
     * 调用时将参数按 params 顺序绑定到环境，然后执行编译后的表达式。
     *
     * <p>为何每次 call 都创建新 env：Groovy 脚本执行需通过 Binding 注入变量，
     * 而 Binding 不是线程安全的。每次调用新建 HashMap 避免并发污染。
     *
     * <p>关联：由 {@link #registerExpressionFunction} 和 {@link #applyRegistration} 创建；
     * 业务脚本中通过 `funcName(arg1, arg2)` 触发其 call 方法。
     */
    private static class ExpressionFunctionClosure extends Closure<Object> {
        private final GroovyFunctionDefinition definition;
        private final CompiledGroovyScript compiled;

        ExpressionFunctionClosure(GroovyFunctionDefinition definition, CompiledGroovyScript compiled) {
            super(null);
            this.definition = definition;
            this.compiled = compiled;
        }

        /**
         * 调用表达式函数
         *
         * <p>参数绑定：按 definition.getParams() 顺序与 args 一一对应写入 funcEnv，
         * 缺失参数不写入（脚本中将以 null 处理），多余参数忽略。
         * 然后委托 {@link GroovyExpressionEngine#execute} 执行编译后的脚本。
         *
         * @param args 调用方传入的参数
         * @return 表达式执行结果
         */
        @Override
        public Object call(Object... args) {
            try {
                Map<String, Object> funcEnv = new HashMap<>();
                String[] params = definition.getParams();
                if (params != null) {
                    int limit = Math.min(params.length, args != null ? args.length : 0);
                    for (int i = 0; i < limit; i++) {
                        funcEnv.put(params[i], args[i]);
                    }
                }
                return GroovyExecutor.getEngine().execute(compiled, funcEnv);
            } catch (Exception e) {
                throw new RuntimeException("Expression function execution failed: "
                        + definition.getName(), e);
            }
        }

        /**
         * 无参调用，转发到 {@link #call(Object...)}。
         */
        @Override
        public Object call() {
            return call(new Object[0]);
        }
    }

    /**
     * 脚本函数 Closure
     *
     * <p>对应 Aviator 的 ScriptFunction。
     * 与 {@link ExpressionFunctionClosure} 实现几乎一致，区别在于编译的是多行脚本；
     * 参数同样在 call() 时写入 env 并注入 Binding，脚本中可直接引用参数名。
     *
     * <p>为何不与 ExpressionFunctionClosure 合并：保留两个类便于通过 instanceof 区分类型，
     * 也对应 Aviator 版本中 ExpressionFunction 和 ScriptFunction 的分离设计。
     */
    private static class ScriptFunctionClosure extends Closure<Object> {
        private final GroovyFunctionDefinition definition;
        private final CompiledGroovyScript compiled;

        ScriptFunctionClosure(GroovyFunctionDefinition definition, CompiledGroovyScript compiled) {
            super(null);
            this.definition = definition;
            this.compiled = compiled;
        }

        /**
         * 调用脚本函数，逻辑与 {@link ExpressionFunctionClosure#call} 完全一致。
         */
        @Override
        public Object call(Object... args) {
            try {
                Map<String, Object> funcEnv = new HashMap<>();
                String[] params = definition.getParams();
                if (params != null) {
                    int limit = Math.min(params.length, args != null ? args.length : 0);
                    for (int i = 0; i < limit; i++) {
                        funcEnv.put(params[i], args[i]);
                    }
                }
                return GroovyExecutor.getEngine().execute(compiled, funcEnv);
            } catch (Exception e) {
                throw new RuntimeException("Script function execution failed: "
                        + definition.getName(), e);
            }
        }

        @Override
        public Object call() {
            return call(new Object[0]);
        }
    }

    /**
     * Java 函数 Closure
     *
     * <p>包装 {@link GroovyFunction} 实例为 Closure，使其能在 Groovy 脚本中被调用。
     *
     * <p>为何需要包装：Groovy 脚本中调用函数时，引擎会从 registeredFunctions 查找 Closure，
     * 不接受任意 Java 对象。JavaFunctionClosure 桥接 Java 函数到 Groovy 调用体系。
     *
     * <p>关联：由 {@link #registerJavaFunction} 和 {@link #applyRegistration} 创建。
     * 与 Expression/Script Closure 不同，不持有 CompiledGroovyScript。
     */
    private static class JavaFunctionClosure extends Closure<Object> {
        private final GroovyFunction function;

        JavaFunctionClosure(GroovyFunction function) {
            super(null);
            this.function = function;
        }

        /**
         * 调用 Java 函数
         *
         * <p>为何传入空 env：当前实现未将 Groovy 调用上下文（如当前 Binding 变量）
         * 透传给 Java 函数，仅传 args。若 Java 函数需要访问上下文，需自行通过其他方式获取。
         * 这与 Aviator 版本的行为一致。
         *
         * @param args 调用方传入的参数
         * @return Java 函数返回值
         */
        @Override
        public Object call(Object... args) {
            try {
                return function.call(new HashMap<String, Object>(), args);
            } catch (Exception e) {
                throw new RuntimeException("Java function execution failed: "
                        + function.getName(), e);
            }
        }

        @Override
        public Object call() {
            return call(new Object[0]);
        }
    }
}
