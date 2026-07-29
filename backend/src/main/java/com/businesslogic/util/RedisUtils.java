package com.businesslogic.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
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
