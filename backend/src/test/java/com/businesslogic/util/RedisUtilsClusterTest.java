package com.businesslogic.util;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RedisUtils} 事务方法的集群兼容性测试。
 *
 * <p>集群（RedisClusterConnection）：不使用 MULTI/EXEC，按顺序逐条执行；
 * 单机（普通 RedisConnection）：保持 MULTI/EXEC 语义。</p>
 */
class RedisUtilsClusterTest {

    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, String> template = mock(RedisTemplate.class);

    private final RedisUtils redisUtils = new RedisUtils();

    RedisUtilsClusterTest() {
        ReflectionTestUtils.setField(redisUtils, "redisTemplate", template);
    }

    @Test
    void clusterConnection_executeInTransaction_runsOperationsInOrderWithoutMultiExec() {
        RedisClusterConnection connection = mock(RedisClusterConnection.class);
        stubTemplate(connection);

        List<String> executed = new ArrayList<>();
        redisUtils.executeInTransaction(Arrays.asList(
                c -> executed.add("data"),
                c -> executed.add("skeleton"),
                c -> executed.add("version")));

        assertEquals(Arrays.asList("data", "skeleton", "version"), executed);
        verify(connection, never()).multi();
        verify(connection, never()).exec();
    }

    @Test
    void standaloneConnection_executeInTransaction_usesMultiExec() {
        RedisConnection connection = mock(RedisConnection.class);
        stubTemplate(connection);

        List<String> executed = new ArrayList<>();
        redisUtils.executeInTransaction(Collections.singletonList(c -> executed.add("data")));

        assertEquals(Collections.singletonList("data"), executed);
        InOrder inOrder = inOrder(connection);
        inOrder.verify(connection).multi();
        inOrder.verify(connection).exec();
    }

    @Test
    void clusterConnection_executeInTemplateTransaction_runsOperationsInOrderWithoutMultiExec() {
        RedisClusterConnection connection = mock(RedisClusterConnection.class);
        stubTemplate(connection);

        List<String> executed = new ArrayList<>();
        redisUtils.executeInTemplateTransaction(Arrays.asList(
                ops -> executed.add("script"),
                ops -> executed.add("versions"),
                ops -> executed.add("global-version")));

        assertEquals(Arrays.asList("script", "versions", "global-version"), executed);
        verify(template, never()).multi();
        verify(template, never()).exec();
    }

    @Test
    void standaloneConnection_executeInTemplateTransaction_usesMultiExec() {
        RedisConnection connection = mock(RedisConnection.class);
        stubTemplate(connection);

        List<String> executed = new ArrayList<>();
        redisUtils.executeInTemplateTransaction(Collections.singletonList(ops -> executed.add("script")));

        assertEquals(Collections.singletonList("script"), executed);
        InOrder inOrder = inOrder(template);
        inOrder.verify(template).multi();
        inOrder.verify(template).exec();
    }

    @Test
    void unlock_matchingValue_deletesLock() {
        stubValueOperations("token-1");
        when(template.delete("lock:key")).thenReturn(true);

        assertTrue(redisUtils.unlock("lock:key", "token-1"));
        verify(template).delete("lock:key");
    }

    @Test
    void unlock_valueNotMatching_doesNotDeleteLock() {
        stubValueOperations("token-2");

        assertFalse(redisUtils.unlock("lock:key", "token-1"));
        verify(template, never()).delete("lock:key");
    }

    @Test
    void unlock_lockAlreadyGone_returnsFalse() {
        stubValueOperations(null);

        assertFalse(redisUtils.unlock("lock:key", "token-1"));
        verify(template, never()).delete("lock:key");
    }

    @Test
    void unlock_neverUsesScriptsOrTransactions() {
        stubValueOperations("token-1");
        when(template.delete("lock:key")).thenReturn(true);

        assertTrue(redisUtils.unlock("lock:key", "token-1"));
        verify(template, never()).multi();
        verify(template, never()).exec();
    }

    @SuppressWarnings("unchecked")
    private void stubValueOperations(String currentValue) {
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(any(String.class))).thenReturn(currentValue);
    }

    /**
     * 让 mocked template 真正执行传入的回调，并把指定的连接交给回调。
     */
    private void stubTemplate(RedisConnection connection) {
        when(template.execute(any(RedisCallback.class))).thenAnswer(invocation -> {
            RedisCallback<?> callback = invocation.getArgument(0);
            return callback.doInRedis(connection);
        });
        when(template.execute(any(RedisCallback.class), eq(false))).thenAnswer(invocation -> {
            RedisCallback<?> callback = invocation.getArgument(0);
            return callback.doInRedis(connection);
        });
        when(template.execute(any(SessionCallback.class))).thenAnswer(invocation -> {
            SessionCallback<?> callback = invocation.getArgument(0);
            return callback.execute(template);
        });
    }
}
