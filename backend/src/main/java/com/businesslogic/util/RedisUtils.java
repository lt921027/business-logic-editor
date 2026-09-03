package com.businesslogic.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
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

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    @Qualifier("bytesRedisTemplate")
    private RedisTemplate<String, byte[]> bytesRedisTemplate;

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
     * 使用 WATCH + MULTI/EXEC 乐观事务实现“比较后删除”，不依赖 Lua 脚本：
     * 先 WATCH 锁 key，读取并校验当前 value 与 lockValue 一致后，再在事务中删除该 key；
     * 若校验后、事务提交前锁 key 已被其他线程修改（例如锁过期后被他人重新获取），
     * WATCH 会使 EXEC 失败，从而不会误删他人的锁。
     *
     * @param key       锁 key
     * @param lockValue 获取锁时使用的唯一值
     * @return true 表示本次成功释放锁；value 不匹配或锁已不存在时返回 false
     */
    public boolean unlock(String key, String lockValue) {
        List<Object> results = redisTemplate.execute(new SessionCallback<List<Object>>() {
            @Override
            public <K, V> List<Object> execute(RedisOperations<K, V> operations) {
                @SuppressWarnings("unchecked")
                RedisOperations<String, String> stringOps = (RedisOperations<String, String>) operations;
                stringOps.watch(key);
                String currentValue = stringOps.opsForValue().get(key);
                if (lockValue.equals(currentValue)) {
                    stringOps.multi();
                    stringOps.delete(key);
                    return stringOps.exec();
                }
                stringOps.unwatch();
                return null;
            }
        });
        return results != null && !results.isEmpty();
    }


    // ==================== 事务操作 ====================

    /**
     * 在 Redis 事务中执行多个操作（不自动递增版本号）
     */
    public void executeInTransaction(List<Consumer<RedisConnection>> operations) {
        redisTemplate.execute((connection) -> {
            connection.multi();
            for (Consumer<RedisConnection> operation : operations) {
                operation.accept(connection);
            }
            connection.exec();
            return null;
        }, false);
    }

    /**
     * 在 Redis 事务中执行多个 RedisTemplate 操作。
     *
     * <p>与 {@link #executeInTransaction(List)} 等价，但操作直接使用 RedisTemplate 的高层 API
     * （opsForValue/opsForHash 等），key/value 自动走模板配置的序列化器，不再手工拼 byte[]。</p>
     */
    public void executeInTemplateTransaction(List<Consumer<RedisOperations<String, String>>> operations) {
        redisTemplate.execute(new SessionCallback<List<Object>>() {
            @Override
            public <K, V> List<Object> execute(RedisOperations<K, V> ops) {
                ops.multi();
                @SuppressWarnings("unchecked")
                RedisOperations<String, String> stringOps = (RedisOperations<String, String>) ops;
                for (Consumer<RedisOperations<String, String>> operation : operations) {
                    operation.accept(stringOps);
                }
                return ops.exec();
            }
        });
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
