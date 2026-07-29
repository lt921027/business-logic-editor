package com.businesslogic.redisPublish;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ExpressionVersionTracker {

    private static final Logger logger = LoggerFactory.getLogger(ExpressionVersionTracker.class);

    private static final String REDIS_KEY_LATEST = "expression:latest";
    private static final String REDIS_KEY_HISTORY_PREFIX = "expression:history:";
    private static final String REDIS_KEY_POD_STATUS_PREFIX = "expression:pod:status:";

    private static final String FIELD_VERSION = "version";
    private static final String FIELD_EXPRESSION = "expression";
    private static final String FIELD_UPDATED_AT = "updatedAt";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_COMPILED_AT = "compiledAt";
    private static final String FIELD_ERROR_MSG = "errorMsg";

    private final StringRedisTemplate stringRedisTemplate;
    private final String podId;

    private final ConcurrentHashMap<String, Long> localVersions = new ConcurrentHashMap<>();

    public ExpressionVersionTracker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.podId = generatePodId();
        logger.info("Pod ID: {}", podId);
    }

    private String generatePodId() {
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            hostname = "unknown";
        }
        return hostname + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public String getPodId() {
        return podId;
    }

    public void saveLatestExpression(String expressionId, Long version, String expression) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        Map<String, String> data = new java.util.HashMap<>();
        data.put("expressionId", expressionId);
        data.put(FIELD_VERSION, String.valueOf(version));
        data.put(FIELD_EXPRESSION, expression);
        data.put(FIELD_UPDATED_AT, now);

        stringRedisTemplate.opsForHash().putAll(REDIS_KEY_LATEST, data);

        String historyKey = REDIS_KEY_HISTORY_PREFIX + expressionId + ":" + version;
        stringRedisTemplate.opsForHash().putAll(historyKey, data);

        logger.info("持久化表达式 [{}] v{} Redis", expressionId, version);
    }

    public ExpressionBroadcastMessage getLatestExpression() {
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(REDIS_KEY_LATEST);
        if (entries == null || entries.isEmpty()) {
            return null;
        }

        String expressionId = (String) entries.get("expressionId");
        String versionStr = (String) entries.get(FIELD_VERSION);
        String expression = (String) entries.get(FIELD_EXPRESSION);

        if (expressionId == null || versionStr == null || expression == null) {
            return null;
        }

        return ExpressionBroadcastMessage.builder()
                .expressionId(expressionId)
                .version(Long.parseLong(versionStr))
                .expression(expression)
                .build();
    }

    public ExpressionBroadcastMessage getHistoryExpression(String expressionId, Long version) {
        String historyKey = REDIS_KEY_HISTORY_PREFIX + expressionId + ":" + version;
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(historyKey);
        if (entries == null || entries.isEmpty()) {
            return null;
        }

        String expr = (String) entries.get(FIELD_EXPRESSION);
        if (expr == null) {
            return null;
        }

        return ExpressionBroadcastMessage.builder()
                .expressionId(expressionId)
                .version(version)
                .expression(expr)
                .build();
    }

    public void reportCompileStatus(String expressionId, Long version, boolean success, String errorMsg) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String statusKey = REDIS_KEY_POD_STATUS_PREFIX + podId;

        Map<String, String> data = new java.util.HashMap<>();
        data.put("expressionId", expressionId);
        data.put(FIELD_VERSION, String.valueOf(version));
        data.put(FIELD_STATUS, success ? "COMPILED" : "FAILED");
        data.put(FIELD_COMPILED_AT, now);
        if (errorMsg != null) {
            data.put(FIELD_ERROR_MSG, errorMsg);
        }

        stringRedisTemplate.opsForHash().putAll(statusKey, data);

        logger.info("上报编译状 pod={}, expressionId={}, v{}, status={}", podId, expressionId, version, success ? "COMPILED" : "FAILED");
    }

    public Map<Object, Object> getPodCompileStatus(String targetPodId) {
        String statusKey = REDIS_KEY_POD_STATUS_PREFIX + targetPodId;
        return stringRedisTemplate.opsForHash().entries(statusKey);
    }

    public Long getLocalVersion(String expressionId) {
        return localVersions.getOrDefault(expressionId, -1L);
    }

    public void updateLocalVersion(String expressionId, Long version) {
        localVersions.put(expressionId, version);
        logger.info("更新本地版本: expressionId={}, v{}", expressionId, version);
    }

    public boolean isVersionBehind(String expressionId, Long latestVersion) {
        Long localVersion = localVersions.getOrDefault(expressionId, -1L);
        return localVersion < latestVersion;
    }

    public void clearPodStatus() {
        String statusKey = REDIS_KEY_POD_STATUS_PREFIX + podId;
        stringRedisTemplate.delete(statusKey);
    }
}