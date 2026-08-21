package com.businesslogic.groovy.engine;

import com.businesslogic.groovy.security.GroovySandbox;
import com.businesslogic.groovy.security.LoopBudget;
import com.businesslogic.groovy.security.LoopBudgetCustomizer;
import com.businesslogic.util.JsonPathUtil;
import com.businesslogic.util.StringUtil;
import com.businesslogic.groovy.util.GroovyDateFunctions;
import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.Script;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Groovy 表达式引擎
 *
 * <p>对应 Aviator 的 AviatorEvaluatorInstance。
 * 负责编译、执行、序列化 Groovy 脚本。
 *
 * <p>核心设计：
 * <ul>
 *   <li>编译：使用 GroovyClassLoader + SecureASTCustomizer 安全编译</li>
 *   <li>编译策略：每次编译生成新的 Script Class，不缓存、不复用 MD5</li>
 *   <li>执行：每次创建新 Script 实例 + Binding，保证线程安全</li>
 *   <li>序列化：返回源码字符串（Groovy Class 无法标准序列化）</li>
 * </ul>
 *
 * <p>关联体系：
 * <ul>
 *   <li>被 {@link GroovyExecutor} 以单例形式包装对外暴露</li>
 *   <li>依赖 {@link GroovySandbox} 提供安全编译器配置</li>
 *   <li>产出的 {@link CompiledGroovyScript} 被各缓存层（{@link com.businesslogic.groovy.cache.GroovyExpressionCache}
 *       / {@link com.businesslogic.groovy.redisCache.GroovyRedisExpressionCache}）持有</li>
 *   <li>自定义函数由 {@link com.businesslogic.groovy.hotload.GroovyFunctionRegistry} 通过
 *       {@link #addFunction(String, Object)} 注入</li>
 * </ul>
 */
public class GroovyExpressionEngine {

    private static final Logger logger = LoggerFactory.getLogger(GroovyExpressionEngine.class);

    /**
     * 每次编译递增的唯一标识，仅用于生成 Groovy 类名，不做编译缓存和 MD5 复用。
     */
    private final AtomicLong classSequence = new AtomicLong();

    /** 安全沙箱，提供 {@link groovy.lang.GroovyClassLoader} 所需的 CompilerConfiguration */
    private final GroovySandbox sandbox;

    /** Groovy 类加载器（使用 {@link GroovySandbox#getCompilerConfiguration()} 的安全配置） */
    private final CleanableGroovyClassLoader classLoader;

    /**
     * 已注册的自定义函数（name → Closure 或 Java 对象）。
     *
     * <p>关联：由  等方法
     * 通过 {@link #addFunction(String, Object)} 写入；在 {@link #execute(CompiledGroovyScript, Map)}
     * 中作为 Binding 变量注入，让脚本中以普通函数名调用（如 `myFunc(a, b)`）。
     */
    private final ConcurrentHashMap<String, Object> registeredFunctions = new ConcurrentHashMap<>();

    /**
     * 已注册的静态函数类（prefix → Class）。
     *
     * <p>关联：由 {@link com.businesslogic.groovy.hotload.GroovyFunctionRegistry#registerStaticFunctions}
     * 调用 {@link #addStaticFunctions(String, Class)} 写入；在 {@link #execute} 中以 prefix 为名注入 Class 对象，
     * 脚本可写 `prefix.methodName(args)` 调用其静态方法。
     */
    private final ConcurrentHashMap<String, Class<?>> registeredStaticClasses = new ConcurrentHashMap<>();

    /** 单脚本最大循环/闭包调用次数（运行时预算，见 {@link LoopBudget}） */
    private volatile int loopBudgetLimit = 200000;

    /** 单脚本最大执行时长（毫秒），超时后中断 worker 并返回超时错误 */
    private volatile long executionTimeoutMillis = 3000;

    /** 单脚本源码最大长度（字符数），防止超大脚本拖垮编译与执行 */
    private volatile int maxScriptLength = 65536;

    /** 脚本执行专用 worker 线程池（有界守护线程，避免脚本挂死占用请求线程） */
    private final ExecutorService scriptExecutor;

    /**
     * 构造引擎：初始化沙箱 + 类加载器。
     *
     * <p>关联：被 {@link GroovyExecutor} 的静态字段持有（单例）；
     * 也被 {@link com.businesslogic.groovy.config.GroovyEngineConfig#groovyExpressionEngine()} 作为 Bean 暴露。
     */
    public GroovyExpressionEngine() {
        this.sandbox = new GroovySandbox();
        this.classLoader = new CleanableGroovyClassLoader(
                getClass().getClassLoader(),
                sandbox.getCompilerConfiguration());
        this.scriptExecutor = Executors.newFixedThreadPool(8, runnable -> {
            Thread thread = new Thread(runnable, "groovy-script-worker");
            thread.setDaemon(true);
            return thread;
        });
        logger.info("[GroovyEngine] 表达式引擎初始化完成");
    }

    /**
     * 编译 Groovy 脚本：先查缓存，未命中再走 GroovyClassLoader.parseClass。
     *
     * <p>为何使用 computeIfAbsent：保证同一源码并发编译时只解析一次，避免重复生成 Class 浪费 Metaspace。
     *
     * <p>为何 classLoader.parseClass 第二参数传 `"GroovyExpr_" + hash`：以 hash 作为生成类名后缀，
     * 便于在堆栈/日志中定位；同名脚本会被 ClassLoader 视为同一类（即使源码已变），因此 hash 必须随源码变化。
     *
     * <p>关联：被 {@link GroovyExecutor#compile(String)}、{@link #deserialize(String)}、
 * 间接被业务缓存层调用。
     *
     * @param scriptSource 脚本源码
     * @return 编译后的包装对象（包含 Script Class、源码、源码 hash）
     */
    public CompiledGroovyScript compile(String scriptSource) {
        if (scriptSource != null && scriptSource.length() > maxScriptLength) {
            throw new GroovyCompileException(
                    "Groovy 脚本长度超限（" + scriptSource.length() + " > " + maxScriptLength + "）", null);
        }
        String uniqueClassId = String.valueOf(classSequence.incrementAndGet());
        String hash = uniqueClassId;
        String h = uniqueClassId;

        {
            try {
                logger.debug("[GroovyEngine] 编译脚本, hash={}, length={}", h, scriptSource.length());
                Class<?> parsed = classLoader.parseClass(scriptSource, "GroovyExpr_" + uniqueClassId);
                Class<? extends Script> clazz;
                try {
                    clazz = parsed.asSubclass(Script.class);
                } catch (Exception e) {
                    cleanupClassCache(parsed);
                    throw new GroovyCompileException("Groovy script result is not a Script: " + e.getMessage(), e);
                }

                cleanupClassCache(clazz);
                return new CompiledGroovyScript(clazz, scriptSource, hash);
            } catch (Exception e) {
                logger.error("[GroovyEngine] 编译失败, hash={}, error={}", h, e.getMessage());
                throw new GroovyCompileException("Groovy 脚本编译失败: " + e.getMessage(), e);
            }
        }
    }

    /**
     * 执行编译后的脚本。
     *
     * <p>为何每次都重建 Binding：Binding 持有运行时变量，不同请求的环境变量不同，
     * 必须独立构建以避免线程间状态污染。
     *
     * <p>注入顺序（后注入覆盖先注入）：工具类 → 自定义函数 → 静态函数类 → 业务 env。
     * 业务 env 最后注入，可让调用方临时覆盖默认工具类（虽然不建议）。
     *
     * @return 脚本 return 语句的返回值；无 return 则返回 null
     */
    private void cleanupClassCache(Class<?> scriptClass) {
        try {
            classLoader.cleanupCompilation(scriptClass);
        } catch (Exception e) {
            logger.warn("[GroovyEngine] 娓呯悊 classCache 澶辫触, class={}, error={}",
                    scriptClass != null ? scriptClass.getName() : "null", e.getMessage());
        }
    }

    public Object execute(CompiledGroovyScript compiled, Map<String, Object> env) {
        try {
            Script script = compiled.newScriptInstance();
            Binding binding = new Binding();

            // 注入工具类（Class 对象，脚本中可通过 JsonPathUtil.read(...) 等方式调用静态方法）
            binding.setVariable("JsonPathUtil", JsonPathUtil.class);
            binding.setVariable("StringUtil", StringUtil.class);
            binding.setVariable("GroovyDateFunctions", GroovyDateFunctions.class);

            // 注入自定义函数（来自 GroovyFunctionRegistry，通常为 Closure）
            for (Map.Entry<String, Object> entry : registeredFunctions.entrySet()) {
                binding.setVariable(entry.getKey(), entry.getValue());
            }

            // 注入静态函数类（按 prefix 暴露整组静态方法）
            for (Map.Entry<String, Class<?>> entry : registeredStaticClasses.entrySet()) {
                binding.setVariable(entry.getKey(), entry.getValue());
            }

            // 注入环境变量（业务入参，最后注入以避免被工具类覆盖）
            if (env != null) {
                for (Map.Entry<String, Object> entry : env.entrySet()) {
                    binding.setVariable(entry.getKey(), entry.getValue());
                }
            }

            // 注入循环/闭包预算守卫（每次执行新实例，随本次执行生命周期，见 LoopBudgetCustomizer）
            binding.setVariable(LoopBudgetCustomizer.GUARD_VAR, new LoopBudget(loopBudgetLimit));

            script.setBinding(binding);
            Future<Object> future = scriptExecutor.submit(
                    (java.util.concurrent.Callable<Object>) script::run);
            try {
                return future.get(executionTimeoutMillis, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                logger.warn("[GroovyEngine] 脚本执行超时（{}ms）, hash={}", executionTimeoutMillis,
                        compiled.getSourceHash());
                throw new GroovyExecuteException(
                        "Groovy 脚本执行超时（" + executionTimeoutMillis + "ms）", e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                throw new GroovyExecuteException("Groovy 脚本执行失败: " + cause.getMessage(), cause);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new GroovyExecuteException("Groovy 脚本执行被中断", e);
            } catch (RejectedExecutionException e) {
                throw new GroovyExecuteException("Groovy 脚本执行并发过高，已拒绝", e);
            }
        } catch (Exception e) {
            if (e instanceof GroovyExecuteException) {
                throw (GroovyExecuteException) e;
            }
            logger.error("[GroovyEngine] 执行失败, hash={}, error={}", compiled.getSourceHash(), e.getMessage());
            throw new GroovyExecuteException("Groovy 脚本执行失败: " + e.getMessage(), e);
        }
    }

    public int getLoopBudgetLimit() {
        return loopBudgetLimit;
    }

    public void setLoopBudgetLimit(int loopBudgetLimit) {
        this.loopBudgetLimit = Math.max(1, loopBudgetLimit);
    }

    public long getExecutionTimeoutMillis() {
        return executionTimeoutMillis;
    }

    public void setExecutionTimeoutMillis(long executionTimeoutMillis) {
        this.executionTimeoutMillis = Math.max(1, executionTimeoutMillis);
    }

    public int getMaxScriptLength() {
        return maxScriptLength;
    }

    public void setMaxScriptLength(int maxScriptLength) {
        this.maxScriptLength = Math.max(1, maxScriptLength);
    }

    /**
     * 一次性编译并执行（便捷方法）。
     *
     * <p>关联：组合 {@link #compile} + {@link #execute}；适合一次性运行、不需要复用编译结果的场景。
     */
    public Object eval(String scriptSource, Map<String, Object> env) {
        return execute(compile(scriptSource), env);
    }

    /**
     * 序列化编译后的脚本（返回源码字符串）。
     *
     * <p>为何如此设计：与 Aviator 不同，Groovy 编译后的 Class 由 GroovyClassLoader 动态生成，
     * 无法被标准 Java 序列化。改用源码字符串作为持久化载体，反序列化时由 {@link #deserialize} 重新编译。
     *
     */
    public String serialize(CompiledGroovyScript compiled) {
        return compiled.getSource();
    }

    /**
     * 反序列化：从源码字符串重新编译为 CompiledGroovyScript。
     */
    public CompiledGroovyScript deserialize(String source) {
        return compile(source);
    }

    /**
     * 注册自定义函数（Groovy Closure 或 Java 对象）。
     *
     * <p>为何接受 Object 类型：GroovyFunctionRegistry 内部将三类函数（EXPRESSION/SCRIPT/JAVA）统一包装为
     * {@link groovy.lang.Closure}，对外接口签名保持宽松以兼容未来扩展。
     *
     * <p>关联：被 {@link com.businesslogic.groovy.hotload.GroovyFunctionRegistry#registerExpressionFunction}
     * 等多个注册方法调用；写入的数据由 {@link #execute} 注入到 Binding。
     */
    public void addFunction(String name, Object function) {
        registeredFunctions.put(name, function);
        logger.info("[GroovyEngine] 注册自定义函数: {}", name);
    }

    /**
     * 注册静态函数类。
     *
     * <p>为何需要：业务可能希望以 `DateUtil.diff(a, b)` 形式调用某工具类的所有静态方法，
     * 此方法将整个 Class 暴露为 Binding 中的一个变量，让脚本通过 prefix 访问。
     *
     * <p>关联：被 {@link com.businesslogic.groovy.hotload.GroovyFunctionRegistry#registerStaticFunctions} 调用；
     * 与 {@link #addFunction} 不同——后者注册的是单个函数（Closure），此处注册的是整组静态方法的宿主类。
     */
    public void addStaticFunctions(String prefix, Class<?> clazz) {
        registeredStaticClasses.put(prefix, clazz);
        logger.info("[GroovyEngine] 注册静态函数类: prefix={}, class={}", prefix, clazz.getName());
    }

    /**
     * 移除自定义函数。
     *
     * <p>关联：被 {@link com.businesslogic.groovy.hotload.GroovyFunctionRegistry#unregisterFunction} 调用，
     * 配合函数热更新/注销流程。
     */
    public void removeFunction(String name) {
        registeredFunctions.remove(name);
        logger.info("[GroovyEngine] 移除自定义函数: {}", name);
    }

    /**
     * 兼容旧接口：当前为无编译缓存模式，不做实际清理。
     */
    public void clearCompileCache() {
        logger.debug("[GroovyEngine] 当前为无编译缓存模式，clearCompileCache 为空操作");
    }

    /** @return 当前编译缓存条目数；无缓存模式下固定返回 0 */
    public int getCompileCacheSize() {
        return 0;
    }

    /**
     * 编译异常：脚本语法错误或违反沙箱规则时抛出。
     *
 * <p>关联：由 {@link #compile} 抛出；上层可根据业务需要捕获并转为可处理结果。
     */
    public static class GroovyCompileException extends RuntimeException {
        public GroovyCompileException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 执行异常：脚本运行时错误（如 NPE、类型转换失败）时抛出。
     *
     * <p>关联：由 {@link #execute} 抛出；被 {@link GroovyExecutor#execute} 等上层方法传播到 Service/Controller。
     */
    public static class GroovyExecuteException extends RuntimeException {
        public GroovyExecuteException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class CleanableGroovyClassLoader extends GroovyClassLoader {

        private CleanableGroovyClassLoader(ClassLoader parent, CompilerConfiguration config) {
            super(parent, config);
        }

        private void cleanupCompilation(Class<?> scriptClass) {
            ClassLoader compileLoader = scriptClass.getClassLoader();
            for (Class<?> loaded : getLoadedClasses()) {
                if (loaded.getClassLoader() == compileLoader) {
                    removeClassCacheEntry(loaded.getName());
                    InvokerHelper.removeClass(loaded);
                }
            }
        }
    }
}
