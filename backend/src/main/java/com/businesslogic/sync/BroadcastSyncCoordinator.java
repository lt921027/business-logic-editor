package com.businesslogic.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.businesslogic.redisPublish.RedisPublisherService;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class BroadcastSyncCoordinator {

    private static final Logger logger = LoggerFactory.getLogger(BroadcastSyncCoordinator.class);

    private static final String ACK_KEY_PREFIX = "broadcast:ack:v";
    private static final String ACK_VALUE = "ok";
    private static final int ACK_TTL_SECONDS = 120;
    private static final int POLL_INTERVAL_MS = 1000;
    private static final int WAIT_TIMEOUT_SECONDS = 30;

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisPublisherService publisherService;
    private final PodRegistryService podRegistry;

    public BroadcastSyncCoordinator(StringRedisTemplate stringRedisTemplate,
                                    RedisPublisherService publisherService,
                                    PodRegistryService podRegistry) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.publisherService = publisherService;
        this.podRegistry = podRegistry;
    }

   /* public SyncResult publishAndWait(String transactionCode, String featureCode,
                                      String expression, Long version) {

        Set<String> expectedPods = podRegistry.getActivePods();

        if (expectedPods.isEmpty()) {
            logger.warn("无活Pod，仅写入 Hash，不等待确认");
            publisherService.publishExpression(transactionCode, featureCode, expression, version);
            return SyncResult.noPods(version);
        }

        publisherService.publishExpression(transactionCode, featureCode, expression, version);

        if (version == -1L) {
            return SyncResult.noPods(version);
        }

        logger.info("等待 Pod 确认: v{}, 预期 {} Pod: {}", version, expectedPods.size(), expectedPods);

        long startTime = System.currentTimeMillis();
        long deadlineMs = startTime + TimeUnit.SECONDS.toMillis(WAIT_TIMEOUT_SECONDS);

        while (System.currentTimeMillis() < deadlineMs) {
            Set<String> confirmed = getConfirmedPods(version);
            Set<String> aliveExpected = filterAlive(expectedPods);

            if (confirmed.containsAll(aliveExpected)) {
                logger.info("全量确认: v{}, confirmed={}/{}",
                        version, confirmed.size(), aliveExpected.size());
                return SyncResult.fullySynced(version, aliveExpected.size());
            }

            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return SyncResult.failure(version, "等待被中");
            }
        }

        Set<String> confirmed = getConfirmedPods(version);
        Set<String> aliveExpected = filterAlive(expectedPods);
        Set<String> unconfirmed = new HashSet<>(aliveExpected);
        unconfirmed.removeAll(confirmed);

        logger.warn("同步超时: v{}, 存活且有确认={}, 存活但未确认={}",
                version, aliveExpected.size() - unconfirmed.size(), unconfirmed);

        if (unconfirmed.isEmpty()) {
            return SyncResult.fullySynced(version, aliveExpected.size());
        }

        return SyncResult.partial(version, aliveExpected.size() - unconfirmed.size(),
                aliveExpected.size(), unconfirmed);
    }*/

/*    public PublishAndSyncResult publishDeleteAndWait(String transactionCode, String featureCode) {
        return new PublishAndSyncResult(publishAndWait(transactionCode, featureCode, "", -1L));
    }*/

    private Set<String> getConfirmedPods(Long version) {
        String pattern = ACK_KEY_PREFIX + version + ":*";
        Set<String> keys = stringRedisTemplate.keys(pattern);
        if (keys == null || keys.isEmpty()) {
            return new HashSet<>();
        }

        return keys.stream()
                .map(k -> {
                    String prefix = ACK_KEY_PREFIX + version + ":";
                    return k.substring(prefix.length());
                })
                .collect(Collectors.toSet());
    }

    private Set<String> filterAlive(Set<String> pods) {
        return pods.stream()
                .filter(pod -> Boolean.TRUE.equals(
                        stringRedisTemplate.hasKey("pod:heartbeat:" + pod)))
                .collect(Collectors.toSet());
    }

    public void sendAck(Long version) {
      /*  String podId = podRegistry.getPodId();
        String key = ACK_KEY_PREFIX + version + ":" + podId;
        stringRedisTemplate.opsForValue().set(key, ACK_VALUE, ACK_TTL_SECONDS, TimeUnit.SECONDS);
        logger.info("确认已发 v{}, pod={}", version, podId);*/
    }

    public static class SyncResult {
        private final boolean fullySynced;
        private final Long version;
        private final int confirmedCount;
        private final int expectedCount;
        private final Set<String> unconfirmedPods;
        private final String description;

        private SyncResult(boolean fullySynced, Long version, int confirmedCount,
                           int expectedCount, Set<String> unconfirmedPods, String description) {
            this.fullySynced = fullySynced;
            this.version = version;
            this.confirmedCount = confirmedCount;
            this.expectedCount = expectedCount;
            this.unconfirmedPods = unconfirmedPods;
            this.description = description;
        }

        static SyncResult noPods(Long version) {
            return new SyncResult(true, version, 0, 0, new HashSet<>(), "无Pod需确认");
        }

        static SyncResult fullySynced(Long version, int podCount) {
            return new SyncResult(true, version, podCount, podCount, new HashSet<>(),
                    "全部 Pod 已同(v" + version + ")");
        }

        static SyncResult partial(Long version, int confirmed, int expected,
                                  Set<String> unconfirmed) {
            return new SyncResult(false, version, confirmed, expected, unconfirmed,
                    String.format("部分同步: %d/%d, 未确 %s", confirmed, expected, unconfirmed));
        }

        static SyncResult failure(Long version, String reason) {
            return new SyncResult(false, version, 0, 0, new HashSet<>(), reason);
        }

        public boolean isFullySynced() { return fullySynced; }
        public Long getVersion() { return version; }
        public int getConfirmedCount() { return confirmedCount; }
        public int getExpectedCount() { return expectedCount; }
        public Set<String> getUnconfirmedPods() { return unconfirmedPods; }
        public String getDescription() { return description; }
    }

    public static class PublishAndSyncResult extends SyncResult {
        PublishAndSyncResult(SyncResult result) {
            super(result.isFullySynced(), result.getVersion(), result.getConfirmedCount(),
                    result.getExpectedCount(), result.getUnconfirmedPods(), result.getDescription());
        }
    }
}