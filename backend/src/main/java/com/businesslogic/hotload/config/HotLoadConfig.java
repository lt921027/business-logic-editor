package com.businesslogic.hotload.config;

import com.businesslogic.hotload.AviatorFunctionRegistry;
import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * 热加载配置类
 *
 * Spring Boot 自动配置，初始化 AviatorFunctionRegistry
 */
@Configuration
public class HotLoadConfig {

    private static final Logger logger = LoggerFactory.getLogger(HotLoadConfig.class);

    @Bean
    public AviatorEvaluatorInstance aviatorEvaluatorInstance() {
        AviatorEvaluatorInstance instance = AviatorEvaluator.getInstance();
        instance.setCachedExpressionByDefault(true);
        instance.useLRUExpressionCache(100);
        return instance;
    }

    @Bean
    public AviatorFunctionRegistry aviatorFunctionRegistry(AviatorEvaluatorInstance evaluator) {
        AviatorFunctionRegistry registry = new AviatorFunctionRegistry(evaluator);
        registry.init();
        return registry;
    }
}
