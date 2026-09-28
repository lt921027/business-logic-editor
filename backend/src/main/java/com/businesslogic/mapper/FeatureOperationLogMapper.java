package com.businesslogic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.businesslogic.oplog.FeatureOperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 特征操作日志 Mapper。
 */
@Mapper
public interface FeatureOperationLogMapper extends BaseMapper<FeatureOperationLog> {
}
