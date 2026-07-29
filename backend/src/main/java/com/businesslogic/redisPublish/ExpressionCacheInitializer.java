package com.businesslogic.redisPublish;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class ExpressionCacheInitializer implements SmartLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(ExpressionCacheInitializer.class);

    @Autowired(required = true)
    private ExpressionSyncService syncService;

    private volatile boolean running = false;

    @Override
    public void start() {
        logger.info("===== SmartLifecycle: Web 容器启动前加载表达式缓存 =====");
        syncService.syncFromRedis();
        running = true;
        logger.info("===== 表达式缓存加载完 Web 容器即将就绪 =====");
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MIN_VALUE;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }
}