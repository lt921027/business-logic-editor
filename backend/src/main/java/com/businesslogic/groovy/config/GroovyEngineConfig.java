package com.businesslogic.groovy.config;

import com.businesslogic.groovy.engine.GroovyExecutor;
import com.businesslogic.groovy.engine.GroovyExpressionEngine;
import com.businesslogic.groovy.hotload.GroovyFunctionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Groovy 引擎配置类
 *
 * <p>对应 Aviator 的 HotLoadConfig，Spring Boot 自动配置。
 *
 * <p>负责：
 * <ul>
 *   <li>暴露 {@link GroovyExpressionEngine} 单例 Bean（与 {@link GroovyExecutor#getEngine()} 共享同一实例）</li>
 *   <li>初始化 {@link GroovyFunctionRegistry} Bean</li>
 * </ul>
 *
 * <p>注意：{@link com.businesslogic.groovy.util.GroovyDateFunctions} 已在
 * {@link GroovyExpressionEngine#execute} 中默认注入到 Binding，脚本中可直接使用
 * `GroovyDateFunctions.diffMonths(a, b)` 等调用，无需在此重复注册。
 *
 * <p>关联体系：
 * <ul>
 *   <li>Bean 加载顺序：先创建 groovyExpressionEngine，再创建 groovyFunctionRegistry（注入 engine）</li>
 *   <li>引擎单例由 {@link GroovyExecutor} 静态持有，此处仅暴露为 Spring Bean 便于其他组件注入</li>
 *   <li>{@link GroovyFunctionRegistry} 初始化后即可被
 *       {@link com.businesslogic.groovy.controller.GroovyFunctionRegistryController} 注入使用</li>
 * </ul>
 */
@Configuration
public class GroovyEngineConfig {

    private static final Logger logger = LoggerFactory.getLogger(GroovyEngineConfig.class);

    /**
     * 暴露 {@link GroovyExpressionEngine} 为 Spring Bean。
     *
     * <p>为何使用 GroovyExecutor.getEngine() 返回的全局单例而非 new：
     * {@link GroovyExecutor} 的所有静态方法（compile/execute）都通过 getEngine() 获取引擎实例，
     * 若此处 new 新实例会导致 Spring Bean 与 GroovyExecutor 内部使用的引擎不一致，
     * 函数注册、缓存等状态会分裂。必须共享同一实例。
     *
     * <p>关联：被 {@link GroovyFunctionRegistry}、
     * {@link com.businesslogic.groovy.controller.GroovyEngineTestController} 等组件注入。
     *
     * @return Groovy 表达式引擎单例
     */
    @Bean
    public GroovyExpressionEngine groovyExpressionEngine() {
        GroovyExpressionEngine engine = GroovyExecutor.getEngine();
        logger.info("[GroovyConfig] GroovyExpressionEngine Bean 已注册");
        return engine;
    }

    /**
     * 创建 {@link GroovyFunctionRegistry} Bean。
     *
     * <p>为何需要 init：调用 {@link GroovyFunctionRegistry#init} 完成幂等初始化
     * （当前仅日志，预留扩展点）。注入的 engine 参数由上面的 groovyExpressionEngine Bean 提供。
     *
     * <p>关联：被 {@link com.businesslogic.groovy.controller.GroovyFunctionRegistryController}
     * 注入使用；registry 内部通过 {@link GroovyExpressionEngine#addFunction} 将 Closure
     * 注入到引擎全局函数表。
     *
     * @param engine Groovy 表达式引擎（由 groovyExpressionEngine Bean 注入）
     * @return 已初始化的函数注册中心
     */
    @Bean
    public GroovyFunctionRegistry groovyFunctionRegistry(GroovyExpressionEngine engine) {
        GroovyFunctionRegistry registry = new GroovyFunctionRegistry(engine);
        registry.init();
        return registry;
    }
}
