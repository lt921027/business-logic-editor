package com.businesslogic.groovy.cache;

import com.businesslogic.groovy.engine.CompiledGroovyScript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Groovy 表达式缓存服务
 *
 * <p>对应 Aviator 的 ExpressionCache。
 * 缓存预编译的 Groovy 脚本，按业务逻辑 ID 索引。
 *
 * <p>关联体系：
 * <ul>
 *   <li>被 {@link com.businesslogic.groovy.service.GroovyBusinessLogicService} 注入并使用，
 *       在 save/update 时写入缓存，在 executeLogic 时读取缓存，在 delete 时移除缓存</li>
 *   <li>缓存的 {@link CompiledGroovyScript} 由 {@link com.businesslogic.groovy.engine.GroovyExecutor#compile} 产出</li>
 *   <li>与 {@link GroovyFeatureExpressionCache} 不同：本缓存按业务逻辑 ID 索引（单条规则），
 *       GroovyFeatureExpressionCache 按交易码+特征码索引（多维规则矩阵）</li>
 * </ul>
 *
 * <p>线程安全：使用 {@link ConcurrentHashMap}，所有方法无锁，适合读多写少场景。
 */
@Component
public class GroovyExpressionCache {

    private static final Logger logger = LoggerFactory.getLogger(GroovyExpressionCache.class);

    /**
     * 表达式缓存：业务逻辑 ID → 编译后的脚本。
     *
     * <p>为何用 ConcurrentHashMap：业务侧 save/update/delete 与 executeLogic 可能并发执行，
     * ConcurrentHashMap 保证线程安全的同时提供良好的读并发性能。
     */
    private final Map<Long, CompiledGroovyScript> expressionCache = new ConcurrentHashMap<>();

    /**
     * 缓存表达式。
     *
     * <p>关联：被 {@link com.businesslogic.groovy.service.GroovyBusinessLogicService#save} 调用
     * （保存业务逻辑后预编译并缓存）。
     *
     * @param logicId    业务逻辑 ID（数据库主键）
     * @param expression 编译后的脚本
     */
    public void put(Long logicId, CompiledGroovyScript expression) {
        logger.debug("缓存 Groovy 表达式，ID: {}", logicId);
        expressionCache.put(logicId, expression);
    }

    /**
     * 获取缓存的表达式。
     *
     * <p>关联：被 {@link com.businesslogic.groovy.service.GroovyBusinessLogicService#executeLogic} 调用；
     * 返回 null 时业务侧会回退到重新编译路径。
     *
     * @param logicId 业务逻辑 ID
     * @return 编译后的脚本；未缓存返回 null
     */
    public CompiledGroovyScript get(Long logicId) {
        logger.debug("获取缓存的 Groovy 表达式，ID: {}", logicId);
        return expressionCache.get(logicId);
    }

    /**
     * 检查是否包含指定 ID 的表达式。
     *
     * <p>关联：主要用于测试与状态查询，业务流程中不直接依赖。
     */
    public boolean containsKey(Long logicId) {
        return expressionCache.containsKey(logicId);
    }

    /**
     * 移除缓存的表达式。
     *
     * <p>关联：被 {@link com.businesslogic.groovy.service.GroovyBusinessLogicService#delete} 调用，
     * 删除业务逻辑时同步清理缓存，避免脏读。
     */
    public CompiledGroovyScript remove(Long logicId) {
        logger.debug("移除缓存的 Groovy 表达式，ID: {}", logicId);
        return expressionCache.remove(logicId);
    }

    /**
     * 清除所有缓存。
     *
     * <p>关联：用于运维/测试场景的全量重置；业务流程不直接调用。
     */
    public void clear() {
        logger.info("清除所有 Groovy 表达式缓存，当前缓存数量：{}", expressionCache.size());
        expressionCache.clear();
    }

    /**
     * 获取缓存数量。
     *
     * <p>关联：用于监控/状态接口。
     */
    public int size() {
        return expressionCache.size();
    }

    /**
     * 刷新缓存：与 {@link #put} 实现一致，语义上表示"已有缓存需更新"。
     *
     * <p>关联：被 {@link com.businesslogic.groovy.service.GroovyBusinessLogicService#update} 调用，
     * 更新业务逻辑后刷新对应缓存条目。
     */
    public void refresh(Long logicId, CompiledGroovyScript expression) {
        logger.debug("刷新 Groovy 表达式缓存，ID: {}", logicId);
        expressionCache.put(logicId, expression);
    }
}
