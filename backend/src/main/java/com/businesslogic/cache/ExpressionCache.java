package com.businesslogic.cache;

import com.googlecode.aviator.Expression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aviator 表达式缓存服 * 用于缓存预编译的 Aviator 表达式，提高执行效率
 */
@Component
public class ExpressionCache {

    private static final Logger logger = LoggerFactory.getLogger(ExpressionCache.class);

    /**
     * 表达式缓存容     * Key: 业务逻辑 ID
     * Value: 预编译的表达式对     */
    private final Map<Long, Expression> expressionCache = new ConcurrentHashMap<>();

    /**
     * 缓存表达     * 
     * @param logicId 业务逻辑 ID
     * @param expression 预编译的表达     */
    public void put(Long logicId, Expression expression) {
        logger.debug("缓存表达式，ID: {}", logicId);
        expressionCache.put(logicId, expression);
    }

    /**
     * 获取缓存的表达式
     * 
     * @param logicId 业务逻辑 ID
     * @return 预编译的表达式，如果不存在则返回 null
     */
    public Expression get(Long logicId) {
        logger.debug("获取缓存的表达式，ID: {}", logicId);
        return expressionCache.get(logicId);
    }

    /**
     * 检查是否包含指ID 的表达式
     * 
     * @param logicId 业务逻辑 ID
     * @return true-存在，false-不存     */
    public boolean containsKey(Long logicId) {
        return expressionCache.containsKey(logicId);
    }

    /**
     * 移除缓存的表达式
     * 
     * @param logicId 业务逻辑 ID
     * @return 被移除的表达式，如果不存在则返回 null
     */
    public Expression remove(Long logicId) {
        logger.debug("移除缓存的表达式，ID: {}", logicId);
        return expressionCache.remove(logicId);
    }

    /**
     * 清除所有缓     */
    public void clear() {
        logger.info("清除所有表达式缓存，当前缓存数量：{}", expressionCache.size());
        expressionCache.clear();
    }

    /**
     * 获取缓存数量
     * 
     * @return 缓存的表达式数量
     */
    public int size() {
        return expressionCache.size();
    }

    /**
     * 刷新缓存（先移除再添加）
     * 
     * @param logicId 业务逻辑 ID
     * @param expression 新的预编译表达式
     */
    public void refresh(Long logicId, Expression expression) {
        logger.debug("刷新表达式缓存，ID: {}", logicId);
        expressionCache.put(logicId, expression);
    }
}
