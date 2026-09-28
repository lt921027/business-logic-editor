package com.businesslogic.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Component
public class RedisUtils {

    private static final Logger logger = LoggerFactory.getLogger(RedisUtils.class);

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    @Qualifier("bytesRedisTemplate")
    private RedisTemplate<String, byte[]> bytesRedisTemplate;

    /** 集群模式探测结果缓存（null 表示尚未探测过） */
    private volatile Boolean clusterModeCache;

    /** 集群降级告警是否已打印，避免刷日志 */
    private volatile boolean clusterFallbackWarned;

    // ==================== String 操作（字符    // ====================

    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public void set(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public Long incr(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    public void del(String key) {
        redisTemplate.delete(key);
    }

    public void del(List<String> keys) {
        redisTemplate.delete(keys);
    }

    // ==================== 字节操作（原始 byte[]） ====================

    public void setBytes(String key, byte[] value) {
        bytesRedisTemplate.opsForValue().set(key, value);
    }

    public byte[] getBytes(String key) {
        return bytesRedisTemplate.opsForValue().get(key);
    }

    public List<byte[]> multiGetBytes(List<String> keys) {
        return bytesRedisTemplate.opsForValue().multiGet(keys);
    }

    // ==================== Hash 操作（String Hash） ====================

    public void hSet(String key, String hashKey, String value) {
        redisTemplate.opsForHash().put(key, hashKey, value);
    }

    public String hGet(String key, String hashKey) {
        Object value = redisTemplate.opsForHash().get(key, hashKey);
        return value != null ? value.toString() : null;
    }

    public Map<Object, Object> hGetAll(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    public Long hIncrBy(String key, String hashKey, long delta) {
        return redisTemplate.opsForHash().increment(key, hashKey, delta);
    }

    public void hDel(String key, String... hashKeys) {
        redisTemplate.opsForHash().delete(key, (Object[]) hashKeys);
    }

    public Long hLen(String key) {
        return redisTemplate.opsForHash().size(key);
    }

    public Set<Object> hKeys(String key) {
        return redisTemplate.opsForHash().keys(key);
    }

    // ==================== Set 操作（兼容旧代码） ====================

    public Set<String> sMembers(String key) {
        return redisTemplate.opsForSet().members(key);
    }

    public void sAdd(String key, String... values) {
        redisTemplate.opsForSet().add(key, values);
    }

    public void sRem(String key, Object... values) {
        redisTemplate.opsForSet().remove(key, values);
    }


    // ==================== 分布式锁操作 ====================

    /**
     * 尝试获取 Redis 分布式锁（非阻塞，拿不到立即返回 false）。
     * <p>
     * 底层使用原子的 SET key value NX PX expire 命令；lockValue 建议使用 UUID 等全局唯一值，
     * 释放锁时需要传入同一个 lockValue 进行校验，避免锁过期后误删其他线程持有的锁。
     *
     * @param key        锁 key
     * @param lockValue  锁持有者标识（唯一值）
     * @param expireTime 锁过期时间
     * @param timeUnit   过期时间单位
     * @return true 表示获取锁成功
     */
    public boolean tryLock(String key, String lockValue, long expireTime, TimeUnit timeUnit) {
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(key, lockValue, expireTime, timeUnit);
        return Boolean.TRUE.equals(locked);
    }

    /**
     * 释放 Redis 分布式锁。
     * <p>
     * 实现方式：GET 取出锁的 value，与 lockValue 比较，一致才 DEL 删除。
     * <p>
     * 可用性说明：只使用 GET / DEL 两条基础命令，单机 / 主从 / 哨兵 / Redis 集群都可用，
     * 也不依赖服务端脚本（集群禁用 EVAL 时同样可用）。
     * <p>
     * 注意：GET 与 DEL 之间存在一个网络往返的时间窗，不是严格的 CAS。
     * 正常情况下锁持有者释放自己的锁没问题；极端情况下（GET 校验通过后锁刚好过期、
     * 且被其他线程重新获取）可能误删他人刚拿到的锁。若要彻底避免，
     * 需要保证锁的 TTL 大于业务最长执行时间，或改用带 fencing token 的方案。
     *
     * @param key       锁 key
     * @param lockValue 获取锁时使用的唯一值
     * @return true 表示本次成功释放锁；value 不匹配或锁已不存在时返回 false
     */
    public boolean unlock(String key, String lockValue) {
        if (key == null || lockValue == null) {
            return false;
        }
        String currentValue = redisTemplate.opsForValue().get(key);
        if (!lockValue.equals(currentValue)) {
            return false;
        }
        Boolean deleted = redisTemplate.delete(key);
        return Boolean.TRUE.equals(deleted);
    }


    // ==================== 事务 / 批量操作 ====================

    /**
     * 在 Redis 事务中执行多个操作（不自动递增版本号）。
     *
     * <p>单机 / 主从 / 哨兵：使用 MULTI/EXEC，多命令原子提交。</p>
     *
     * <p>Redis 集群：MULTI/EXEC/WATCH 不被支持（Spring Data Redis 的集群连接直接抛
     * {@code InvalidDataAccessApiUsageException("MULTI is currently not supported in cluster mode.")}），
     * 且集群下事务的多个 key 必须落在同一个 slot，跨 slot 本身也无法开启事务。
     * 因此检测到集群时，此处退化为“按传入顺序逐条执行”：单条命令依旧原子，
     * 但多条命令之间不再具备原子性。调用方必须保证顺序满足一致性要求
     * （例如先写数据、最后再递增版本号，保证版本号可见时数据一定已写好）。</p>
     *
     * <p>集群下若必须让多命令原子生效，只能让相关 key 落在同一个 slot（key 加 {@code {tag}}）
     * 并使用服务端事务/脚本；本环境集群禁用脚本，请改用“顺序写 + 版本号后置”的方式保证一致性。</p>
     */
    public void executeInTransaction(List<Consumer<RedisConnection>> operations) {
        boolean clusterMode = isClusterMode(redisTemplate);
        if (clusterMode) {
            warnClusterFallback("executeInTransaction");
        }
        redisTemplate.execute((connection) -> {
            if (!clusterMode) {
                connection.multi();
            }
            for (Consumer<RedisConnection> operation : operations) {
                operation.accept(connection);
            }
            if (!clusterMode) {
                connection.exec();
            }
            return null;
        }, false);
    }

    /**
     * 在 Redis 事务中执行多个 RedisTemplate 操作（{@link #executeInTransaction(List)} 的模板版）。
     *
     * <p>操作直接使用 RedisTemplate 的高层 API（opsForValue/opsForHash 等），key/value 自动走
     * 模板配置的序列化器，不手工拼 byte[]。集群模式下的行为与原子性说明见
     * {@link #executeInTransaction(List)}。</p>
     */
    public void executeInTemplateTransaction(List<Consumer<RedisOperations<String, String>>> operations) {
        executeInTemplateTransaction(redisTemplate, operations);
    }

    /**
     * 通用模板版本的事务执行入口：可用 {@code jdkRedisTemplate} 等其它模板执行同一批操作。
     *
     * @param template   目标 RedisTemplate（决定 key/value 序列化方式）
     * @param operations 待执行操作，按顺序执行
     * @param <V>        模板 value 类型
     */
    public <V> void executeInTemplateTransaction(RedisTemplate<String, V> template,
                                                 List<Consumer<RedisOperations<String, V>>> operations) {
        if (isClusterMode(template)) {
            warnClusterFallback("executeInTemplateTransaction");
            for (Consumer<RedisOperations<String, V>> operation : operations) {
                operation.accept(template);
            }
            return;
        }
        template.execute(new SessionCallback<List<Object>>() {
            @Override
            public <K, VV> List<Object> execute(RedisOperations<K, VV> ops) {
                ops.multi();
                @SuppressWarnings("unchecked")
                RedisOperations<String, V> stringOps = (RedisOperations<String, V>) ops;
                for (Consumer<RedisOperations<String, V>> operation : operations) {
                    operation.accept(stringOps);
                }
                return ops.exec();
            }
        });
    }

    /**
     * 当前连接是否为 Redis 集群（Redis Cluster）。
     * <p>按连接类型判断：集群连接实现 {@link RedisClusterConnection}，
     * 因此 Lettuce / Jedis 集群都适用，无需依赖具体客户端类型。</p>
     */
    public boolean isClusterMode() {
        return isClusterMode(redisTemplate);
    }

    private boolean isClusterMode(RedisTemplate<?, ?> template) {
        if (template == redisTemplate && clusterModeCache != null) {
            return clusterModeCache;
        }
        Boolean detected = template.execute(
                (RedisCallback<Boolean>) connection -> connection instanceof RedisClusterConnection);
        boolean cluster = Boolean.TRUE.equals(detected);
        if (template == redisTemplate) {
            clusterModeCache = cluster;
        }
        return cluster;
    }

    private void warnClusterFallback(String methodName) {
        if (clusterFallbackWarned) {
            return;
        }
        synchronized (this) {
            if (clusterFallbackWarned) {
                return;
            }
            clusterFallbackWarned = true;
        }
        logger.warn("[RedisUtils] 检测到 Redis 集群模式：集群不支持 MULTI/EXEC/WATCH，{} 退化为按顺序逐条执行"
                + "（单条命令原子，多条命令之间不保证原子性）。请保证写入顺序：先写数据，最后更新版本号。",
                methodName);
    }

    /**
     * 在 Redis 事务中执行多个操作（兼容旧接口，versionKey 参数被忽略）
     *
     * @deprecated 使用 {@link #executeInTransaction(List)} 代替
     */
    @Deprecated
    public void executeInTransaction(List<Consumer<RedisConnection>> operations, String versionKey) {
        executeInTransaction(operations);
    }

    public RedisTemplate<String, String> getRedisTemplate() {
        return redisTemplate;
    }

    public RedisTemplate<String, byte[]> getBytesRedisTemplate() {
        return bytesRedisTemplate;
    }
}
