package com.businesslogic.redisPublish;

import com.businesslogic.cache.FeatureExpressionCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class RedisMessageListener implements MessageListener {

    private static final Logger logger = LoggerFactory.getLogger(RedisMessageListener.class);

    private static final String ONLINE_FEATURE_RECORD_KEY = "ONLINE_FEATURE_RECORD";
    private static final String FIELD_SEPARATOR = "#";

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final FeatureExpressionCache featureExpressionCache;
    private final ExpressionSyncService syncService;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public RedisMessageListener(ObjectMapper objectMapper,
                                StringRedisTemplate stringRedisTemplate,
                                FeatureExpressionCache featureExpressionCache,
                                ExpressionSyncService syncService) {
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.featureExpressionCache = featureExpressionCache;
        this.syncService = syncService;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        executor.submit(() -> handleMessage(body));
    }

    private void handleMessage(String body) {
        try {
            ExpressionBroadcastMessage msg = objectMapper.readValue(body, ExpressionBroadcastMessage.class);

            String txn = msg.getTransactionCode();
            String feature = msg.getFeatureCode();
            String expression = msg.getExpression();
            Long version = msg.getVersion();

            if (txn == null || feature == null || version == null) {
                logger.warn("广播消息字段不完 txn={}, feature={}, version={}", txn, feature, version);
                return;
            }

            if (version == -1L) {
                handleDelete(txn, feature, version);
            } else {
                handleUpdate(txn, feature, version, expression);
            }

        } catch (Exception e) {
            logger.error("处理广播消息异常: {}", body, e);
        }
    }

    private void handleDelete(String txn, String feature, Long version) {
        FeatureExpressionCache.CacheResult result = featureExpressionCache.cacheExpression(txn, feature, version, "");
        logger.info("广播处理-删除: {}#{} v{}, 结果={}", txn, feature, version, result);
    }

    private void handleUpdate(String txn, String feature, Long version, String expression) {

        if (expression == null || expression.isEmpty()) {
            String field = txn + FIELD_SEPARATOR + feature + FIELD_SEPARATOR + version;
            expression = (String) stringRedisTemplate.opsForHash().get(ONLINE_FEATURE_RECORD_KEY, field);
            if (expression == null) {
                logger.warn("广播处理-字段缺失且消息无表达 {}, 触发全量同步兜底", field);
                syncService.syncFromRedis();
                return;
            }
        }

        FeatureExpressionCache.CacheResult result = featureExpressionCache.cacheExpression(txn, feature, version, expression);
        logger.info("广播处理-更新: {}#{} v{}, 结果={}", txn, feature, version, result);
    }
}
