package com.businesslogic.oplog;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.businesslogic.mapper.FeatureOperationLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 特征操作日志落库与查询。
 */
@Service
public class FeatureOperationLogService {

    /** 列表查询默认返回条数 */
    private static final int DEFAULT_LIMIT = 200;

    /** 列表查询最大返回条数 */
    private static final int MAX_LIMIT = 1000;

    private final FeatureOperationLogMapper featureOperationLogMapper;

    public FeatureOperationLogService(FeatureOperationLogMapper featureOperationLogMapper) {
        this.featureOperationLogMapper = featureOperationLogMapper;
    }

    /**
     * 落库。
     *
     * <p>用 REQUIRES_NEW 开启独立事务：业务方法回滚时日志不会被一起回滚，
     * "操作失败"的记录才能留下来。异常由切面捕获，不影响业务返回。</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void save(FeatureOperationLog operationLog) {
        featureOperationLogMapper.insert(operationLog);
    }

    /**
     * 查询某个特征的操作历史。
     *
     * @param featureId 特征ID，为 null 表示不限
     * @param limit     返回条数，为 null 时取默认值
     */
    public List<FeatureOperationLog> listByFeatureId(Long featureId, Integer limit) {
        QueryWrapper<FeatureOperationLog> wrapper = new QueryWrapper<>();
        wrapper.eq(featureId != null, "feature_id", featureId).orderByDesc("id");
        wrapper.last("LIMIT " + normalizeLimit(limit));
        return featureOperationLogMapper.selectList(wrapper);
    }

    /**
     * 查询最近的操作记录。
     */
    public List<FeatureOperationLog> listRecent(Integer limit) {
        return listByFeatureId(null, limit);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
