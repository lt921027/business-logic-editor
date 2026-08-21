package com.businesslogic.config;

import com.businesslogic.executor.AviatorExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * Aviator 安全选项启动配置。
 *
 * <p>在 Spring 启动阶段对全局 {@code AviatorEvaluator.getInstance()} 应用安全选项
 * （循环次数上限、禁用 while），保证任何缓存预热/编译路径在此之前已生效。
 *
 * <p>关联：实际逻辑在 {@link AviatorExecutor#applySecurityOptions()}，幂等可重复调用。
 */
@Configuration
public class AviatorSecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(AviatorSecurityConfig.class);

    @PostConstruct
    public void init() {
        AviatorExecutor.applySecurityOptions();
        logger.info("[AviatorSecurityConfig] 已应用 Aviator 安全选项");
    }
}
