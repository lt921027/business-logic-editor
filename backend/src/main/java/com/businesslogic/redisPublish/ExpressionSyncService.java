package com.businesslogic.redisPublish;

import com.businesslogic.cache.FeatureExpressionCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
public class ExpressionSyncService {

    private static final Logger logger = LoggerFactory.getLogger(ExpressionSyncService.class);

    private static final String ONLINE_FEATURE_RECORD_KEY = "ONLINE_FEATURE_RECORD";
    private static final String FIELD_SEPARATOR = "#";

    @Autowired(required = true)
    private StringRedisTemplate stringRedisTemplate;
    @Autowired(required = true)
    private FeatureExpressionCache featureExpressionCache;

    //@EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info("===== Pod 启动自愈检=====");
        syncFromRedis();
        logger.info("===== 表达式缓存就绪，可以对外提供服务 =====");
    }

    public void syncFromRedis() {
        try {
            Map<Object, Object> hashEntries = stringRedisTemplate.opsForHash().entries(ONLINE_FEATURE_RECORD_KEY);

            if (hashEntries == null || hashEntries.isEmpty()) {
                logger.info("ONLINE_FEATURE_RECORD 为空，跳过同");
                return;
            }

            logger.info("ONLINE_FEATURE_RECORD 读取{} 条记录，开始同..", hashEntries.size());

            Set<String> redisFeatureKeys = new HashSet<>();
            int updatedCount = 0;
            int skippedCount = 0;
            int errorCount = 0;

            for (Map.Entry<Object, Object> entry : hashEntries.entrySet()) {
                String field = (String) entry.getKey();
                String expression = (String) entry.getValue();

                ParsedField parsed = parseField(field);
                if (parsed == null) {
                    logger.warn("无法解析字段: {}", field);
                    continue;
                }

                String redisFeatureKey = parsed.transactionCode + FIELD_SEPARATOR + parsed.featureCode;
                redisFeatureKeys.add(redisFeatureKey);

                FeatureExpressionCache.CacheEntry localEntry = featureExpressionCache.getEntry(
                        parsed.transactionCode, parsed.featureCode);

                if (localEntry != null && localEntry.getVersion().equals(parsed.version)) {
                    skippedCount++;
                    continue;
                }

                FeatureExpressionCache.CacheResult result = featureExpressionCache.cacheExpression(
                        parsed.transactionCode, parsed.featureCode, parsed.version, expression);

                if (result.getType() == FeatureExpressionCache.CacheResult.Type.COMPILE_ERROR) {
                    logger.error("编译失败 [{}]: {} - {}", field, expression, result.getErrorMessage());
                    errorCount++;
                } else {
                    updatedCount++;
                }
            }

            logger.info("同步完成: 更新 {}, 跳过 {}, 编译失败 {}, 总计 {}",
                    updatedCount, skippedCount, errorCount, hashEntries.size());

            cleanStaleEntries(redisFeatureKeys);

        } catch (Exception e) {
            logger.error("syncFromRedis 异常", e);
        }
    }

    private ParsedField parseField(String field) {
        if (field == null || field.isEmpty()) {
            return null;
        }

        String[] parts = field.split(FIELD_SEPARATOR, 3);
        if (parts.length != 3) {
            return null;
        }

        try {
            long version = Long.parseLong(parts[2]);
            return new ParsedField(parts[0], parts[1], version);
        } catch (NumberFormatException e) {
            logger.warn("版本号格式错 {}", parts[2]);
            return null;
        }
    }

    //@Scheduled(fixedDelay = 10000)
    public void periodicHealthCheck() {
        try {
            syncFromRedis();
        } catch (Exception e) {
            logger.debug("定时巡检异常（可Redis 暂不可达 {}", e.getMessage());
        }
    }

    public void forceSync() {
        logger.info("收到强制同步请求");
        syncFromRedis();
    }

    private void cleanStaleEntries(Set<String> redisFeatureKeys) {
        int removedCount = 0;
        java.util.ArrayList<FeatureExpressionCache.CacheEntry> localEntries = featureExpressionCache.getAllEntries();

        for (FeatureExpressionCache.CacheEntry entry : localEntries) {
            String key = entry.getTransactionCode() + FIELD_SEPARATOR + entry.getFeatureCode();
            if (!redisFeatureKeys.contains(key)) {
                featureExpressionCache.removeEntry(entry.getTransactionCode(), entry.getFeatureCode());
                logger.info("清理过期缓存: {}", key);
                removedCount++;
            }
        }

        if (removedCount > 0) {
            logger.info("清理过期缓存完成: 移除 {} ", removedCount);
        }
    }

    private static class ParsedField {
        final String transactionCode;
        final String featureCode;
        final Long version;

        ParsedField(String transactionCode, String featureCode, Long version) {
            this.transactionCode = transactionCode;
            this.featureCode = featureCode;
            this.version = version;
        }
    }
}