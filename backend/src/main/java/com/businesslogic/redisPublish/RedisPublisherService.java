package com.businesslogic.redisPublish;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisPublisherService {

    private static final Logger logger = LoggerFactory.getLogger(RedisPublisherService.class);

    private static final String BROADCAST_CHANNEL = "business-logic:broadcast";
    private static final String ONLINE_FEATURE_RECORD_KEY = "ONLINE_FEATURE_RECORD";
    private static final String FIELD_SEPARATOR = "#";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public RedisPublisherService(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }


    public PublishResult publishExpression(String transactionCode, String featureCode, String expression, Long version) {

        if (version == -1L) {
            deleteOldVersions(transactionCode, featureCode);
        } else {
            String field = transactionCode + FIELD_SEPARATOR + featureCode + FIELD_SEPARATOR + version;
            stringRedisTemplate.opsForHash().put(ONLINE_FEATURE_RECORD_KEY, field, expression);
            logger.info("已写Redis Hash: {}", field);
        }

        ExpressionBroadcastMessage broadcastMsg = ExpressionBroadcastMessage.builder()
                .transactionCode(transactionCode)
                .featureCode(featureCode)
                .version(version)
                .expression(version == -1L ? null : expression)
                .timestamp(System.currentTimeMillis())
                .build();

        try {
            String jsonMsg = objectMapper.writeValueAsString(broadcastMsg);
            stringRedisTemplate.convertAndSend(BROADCAST_CHANNEL, jsonMsg);
            logger.info("广播已发 {}", jsonMsg);
        } catch (JsonProcessingException e) {
            logger.error("序列化广播消息失 {}", e.getMessage());
            return PublishResult.failure("序列化广播消息失 " + e.getMessage());
        }

        return PublishResult.success(version);
    }

    private void deleteOldVersions(String transactionCode, String featureCode) {
        String prefix = transactionCode + FIELD_SEPARATOR + featureCode + FIELD_SEPARATOR;
        for (Object field : stringRedisTemplate.opsForHash().keys(ONLINE_FEATURE_RECORD_KEY)) {
            String f = (String) field;
            if (f.startsWith(prefix)) {
                stringRedisTemplate.opsForHash().delete(ONLINE_FEATURE_RECORD_KEY, f);
            }
        }
    }

    public static class PublishResult {
        private final boolean success;
        private final Long version;
        private final String errorMessage;

        private PublishResult(boolean success, Long version, String errorMessage) {
            this.success = success;
            this.version = version;
            this.errorMessage = errorMessage;
        }

        public static PublishResult success(Long version) {
            return new PublishResult(true, version, null);
        }

        public static PublishResult failure(String errorMessage) {
            return new PublishResult(false, null, errorMessage);
        }

        public boolean isSuccess() { return success; }
        public Long getVersion() { return version; }
        public String getErrorMessage() { return errorMessage; }
    }
}
