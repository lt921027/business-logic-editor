package com.businesslogic.oplog;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.businesslogic.mapper.OperationLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 操作日志落库与查询。
 */
@Service
public class OperationLogService {

    /** 列表查询默认返回条数 */
    private static final int DEFAULT_LIMIT = 200;

    /** 列表查询最大返回条数，防止一次拉爆内存 */
    private static final int MAX_LIMIT = 1000;

    private final OperationLogMapper operationLogMapper;

    public OperationLogService(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    /**
     * 落库。
     *
     * <p>用 REQUIRES_NEW 开启独立事务：业务方法回滚时，已经写入的日志不会被一起回滚，
     * 这样"操作失败"的记录才能留下来。异常由切面捕获，不会影响业务返回。</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void save(OperationLog operationLog) {
        operationLogMapper.insert(operationLog);
    }

    /**
     * 按对象查询操作记录，展示端用它渲染"这个对象的历史操作"。
     *
     * @param bizType 对象类型，为 null 或空表示不限
     * @param bizId   对象ID，为 null 或空表示不限
     * @param limit   返回条数，为 null 时取默认值
     * @return 按时间倒序的操作记录
     */
    public List<OperationLog> list(String bizType, String bizId, Integer limit) {
        QueryWrapper<OperationLog> wrapper = new QueryWrapper<>();
        wrapper.eq(isNotBlank(bizType), "biz_type", bizType)
                .eq(isNotBlank(bizId), "biz_id", bizId)
                .orderByDesc("id");
        wrapper.last("LIMIT " + normalizeLimit(limit));
        return operationLogMapper.selectList(wrapper);
    }

    /**
     * 查询最近的操作记录。
     *
     * @param limit 返回条数，为 null 时取默认值
     */
    public List<OperationLog> listRecent(Integer limit) {
        return list(null, null, limit);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
