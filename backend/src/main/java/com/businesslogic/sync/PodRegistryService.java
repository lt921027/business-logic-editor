package com.businesslogic.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class PodRegistryService {

    private static final Logger logger = LoggerFactory.getLogger(PodRegistryService.class);

   /* private static final String POD_SET_KEY = "pod:heartbeat";
    private static final String POD_TTL_PREFIX = "pod:heartbeat:";
    private static final int HEARTBEAT_TTL_SECONDS = 30;
    private static final int CLEANUP_INTERVAL_SECONDS = 60;

    private final StringRedisTemplate stringRedisTemplate;
    private final String podId;

    public PodRegistryService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.podId = UUID.randomUUID().toString();
    }

    @PostConstruct
    public void init() {
        sendHeartbeat();
        logger.info("Pod 注册完成: podId={}", podId);
    }

    @Scheduled(fixedRate = 10000)
    public void sendHeartbeat() {
        stringRedisTemplate.opsForValue().set(POD_TTL_PREFIX + podId, podId, HEARTBEAT_TTL_SECONDS, TimeUnit.SECONDS);
        stringRedisTemplate.opsForSet().add(POD_SET_KEY, podId);
    }

    @Scheduled(fixedRate = 60000)
    public void cleanupExpired() {
        Set<String> allPods = stringRedisTemplate.opsForSet().members(POD_SET_KEY);
        if (allPods == null || allPods.isEmpty()) {
            return;
        }

        int removed = 0;
        for (String pod : allPods) {
            if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(POD_TTL_PREFIX + pod))) {
                stringRedisTemplate.opsForSet().remove(POD_SET_KEY, pod);
                removed++;
            }
        }
        if (removed > 0) {
            logger.info("清理过期 Pod: {}  当前存活: {}", removed, allPods.size() - removed);
        }
    }

    public String getPodId() {
        return podId;
    }

    public Set<String> getActivePods() {
        Set<String> all = stringRedisTemplate.opsForSet().members(POD_SET_KEY);
        if (all == null || all.isEmpty()) {
            return new HashSet<>();
        }
        return all.stream()
                .filter(pod -> Boolean.TRUE.equals(stringRedisTemplate.hasKey(POD_TTL_PREFIX + pod)))
                .collect(Collectors.toSet());
    }

    public int countActivePods() {
        return getActivePods().size();
    }*/
}