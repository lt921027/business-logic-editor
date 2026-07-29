package com.businesslogic.redisPublish;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

@Component
public class RedisReconnectListener implements DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(RedisReconnectListener.class);

    private final LettuceConnectionFactory connectionFactory;
    private final ExpressionSyncService syncService;

    private volatile Disposable subscription;

    public RedisReconnectListener(LettuceConnectionFactory connectionFactory,
                                  ExpressionSyncService syncService) {
        this.connectionFactory = connectionFactory;
        this.syncService = syncService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
           /* ClientResources resources = connectionFactory.getClientConfiguration().getClientResources().orElse(null);
            if (resources == null) {
                logger.warn("无法获取 Lettuce ClientResources，跳过事件监听注");
                return;
            }*/

            /*subscription = resources.eventBus()
                    .filter(event -> event instanceof ConnectionActivatedEvent)
                    .cast(ConnectionActivatedEvent.class)
                    .publishOn(Schedulers.boundedElastic())
                    .subscribe(event -> {
                        ConnectionActivatedEvent activatedEvent = (ConnectionActivatedEvent) event;
                        logger.info("Lettuce EventBus: 连接已激[{} -> {}], 立即触发版本追赶",
                                activatedEvent.getLocal(), activatedEvent.getRemote());
                        syncService.forceSync();
                    });*/

            logger.info("Lettuce EventBus 重连监听已注");

        } catch (Exception e) {
            logger.error("注册 Lettuce EventBus 监听失败，将仅依赖定时巡检", e);
        }
    }

    @Override
    public void destroy() {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
            logger.info("Lettuce EventBus 重连监听已关");
        }
    }
}