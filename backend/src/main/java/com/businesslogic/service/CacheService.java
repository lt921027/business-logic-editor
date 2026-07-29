package com.businesslogic.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.businesslogic.entity.BusinessLogicCache;
import com.businesslogic.entity.ExecutionResultCache;
import com.businesslogic.entity.FieldTreeCache;
import com.businesslogic.mapper.BusinessLogicCacheMapper;
import com.businesslogic.mapper.ExecutionResultCacheMapper;
import com.businesslogic.mapper.FieldTreeCacheMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.function.Function;
import java.util.function.Supplier;

@Service
public class CacheService extends ServiceImpl<BusinessLogicCacheMapper, BusinessLogicCache> {

    private static final Logger logger = LoggerFactory.getLogger(CacheService.class);

    private final FieldTreeCacheMapper fieldTreeCacheMapper;
    private final ExecutionResultCacheMapper executionResultCacheMapper;
    private final ObjectMapper objectMapper;

    public CacheService(FieldTreeCacheMapper fieldTreeCacheMapper, ExecutionResultCacheMapper executionResultCacheMapper, ObjectMapper objectMapper) {
        this.fieldTreeCacheMapper = fieldTreeCacheMapper;
        this.executionResultCacheMapper = executionResultCacheMapper;
        this.objectMapper = objectMapper;
    }

    private static final long DEFAULT_CACHE_EXPIRE_SECONDS = 3600;
    private static final long FIELD_TREE_CACHE_EXPIRE_SECONDS = 86400;
    private static final long EXECUTION_RESULT_CACHE_EXPIRE_SECONDS = 1800;

    public void cacheBusinessLogic(Long businessLogicId, String cacheKey, Object cacheValue, String cacheType) {
        LambdaQueryWrapper<BusinessLogicCache> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(BusinessLogicCache::getBusinessLogicId, businessLogicId)
                .eq(BusinessLogicCache::getCacheKey, cacheKey);
        
        cacheWithTemplate(
            "业务逻辑",
            queryWrapper,
            cacheValue,
            DEFAULT_CACHE_EXPIRE_SECONDS,
            baseMapper,
            () -> {
                BusinessLogicCache cache = new BusinessLogicCache();
                cache.setBusinessLogicId(businessLogicId);
                cache.setCacheKey(cacheKey);
                cache.setCacheType(cacheType);
                return cache;
            },
            (cache, value, expireTime) -> {
                cache.setCacheValue(value);
                cache.setCacheType(cacheType);
                cache.setExpireTime(expireTime);
            }
        );
    }

    public Object getCachedBusinessLogic(Long businessLogicId, String cacheKey) {
        LambdaQueryWrapper<BusinessLogicCache> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(BusinessLogicCache::getBusinessLogicId, businessLogicId)
                .eq(BusinessLogicCache::getCacheKey, cacheKey)
                .gt(BusinessLogicCache::getExpireTime, LocalDateTime.now());
        
        return getCachedWithTemplate("业务逻辑", queryWrapper, baseMapper, BusinessLogicCache::getCacheValue);
    }

    public void cacheFieldTree(String jsonInput, Object fieldTree) {
        String hash = generateHash(jsonInput);
        LambdaQueryWrapper<FieldTreeCache> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FieldTreeCache::getJsonInputHash, hash);
        
        cacheWithTemplate(
            "字段树",
            queryWrapper,
            fieldTree,
            FIELD_TREE_CACHE_EXPIRE_SECONDS,
            fieldTreeCacheMapper,
            () -> {
                FieldTreeCache cache = new FieldTreeCache();
                cache.setJsonInputHash(hash);
                return cache;
            },
            (cache, value, expireTime) -> {
                cache.setFieldTreeJson(value);
                cache.setExpireTime(expireTime);
            }
        );
    }

    public Object getCachedFieldTree(String jsonInput) {
        String hash = generateHash(jsonInput);
        LambdaQueryWrapper<FieldTreeCache> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FieldTreeCache::getJsonInputHash, hash)
                .gt(FieldTreeCache::getExpireTime, LocalDateTime.now());
        
        return getCachedWithTemplate("字段树", queryWrapper, fieldTreeCacheMapper, FieldTreeCache::getFieldTreeJson);
    }

    public void cacheExecutionResult(Long businessLogicId, String inputData, Object resultData, 
                                   Integer executionTimeMs, Boolean success, String errorMessage) {
        String hash = generateHash(inputData);
        LambdaQueryWrapper<ExecutionResultCache> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ExecutionResultCache::getBusinessLogicId, businessLogicId)
                .eq(ExecutionResultCache::getInputDataHash, hash);
        
        cacheWithTemplate(
            "执行结果",
            queryWrapper,
            resultData,
            EXECUTION_RESULT_CACHE_EXPIRE_SECONDS,
            executionResultCacheMapper,
            () -> {
                ExecutionResultCache cache = new ExecutionResultCache();
                cache.setBusinessLogicId(businessLogicId);
                cache.setInputDataHash(hash);
                cache.setExecutionTimeMs(executionTimeMs);
                cache.setSuccess(success);
                cache.setErrorMessage(errorMessage);
                return cache;
            },
            (cache, value, expireTime) -> {
                cache.setResultData(value);
                cache.setExecutionTimeMs(executionTimeMs);
                cache.setSuccess(success);
                cache.setErrorMessage(errorMessage);
                cache.setExpireTime(expireTime);
            }
        );
    }

    public Object getCachedExecutionResult(Long businessLogicId, String inputData) {
        String hash = generateHash(inputData);
        LambdaQueryWrapper<ExecutionResultCache> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ExecutionResultCache::getBusinessLogicId, businessLogicId)
                .eq(ExecutionResultCache::getInputDataHash, hash)
                .gt(ExecutionResultCache::getExpireTime, LocalDateTime.now());
        
        return getCachedWithTemplate("执行结果", queryWrapper, executionResultCacheMapper, ExecutionResultCache::getResultData);
    }

    public void clearExpiredCache() {
        LocalDateTime now = LocalDateTime.now();
        
        LambdaQueryWrapper<BusinessLogicCache> blQueryWrapper = new LambdaQueryWrapper<>();
        blQueryWrapper.lt(BusinessLogicCache::getExpireTime, now);
        baseMapper.delete(blQueryWrapper);
        
        LambdaQueryWrapper<FieldTreeCache> ftQueryWrapper = new LambdaQueryWrapper<>();
        ftQueryWrapper.lt(FieldTreeCache::getExpireTime, now);
        fieldTreeCacheMapper.delete(ftQueryWrapper);
        
        LambdaQueryWrapper<ExecutionResultCache> erQueryWrapper = new LambdaQueryWrapper<>();
        erQueryWrapper.lt(ExecutionResultCache::getExpireTime, now);
        executionResultCacheMapper.delete(erQueryWrapper);
        
        logger.info("清理过期缓存完成");
    }

    public void clearCacheByBusinessLogicId(Long businessLogicId) {
        LambdaQueryWrapper<BusinessLogicCache> blQueryWrapper = new LambdaQueryWrapper<>();
        blQueryWrapper.eq(BusinessLogicCache::getBusinessLogicId, businessLogicId);
        baseMapper.delete(blQueryWrapper);
        
        LambdaQueryWrapper<ExecutionResultCache> erQueryWrapper = new LambdaQueryWrapper<>();
        erQueryWrapper.eq(ExecutionResultCache::getBusinessLogicId, businessLogicId);
        executionResultCacheMapper.delete(erQueryWrapper);
        
        logger.info("清理业务逻辑缓存: businessLogicId={}", businessLogicId);
    }

    /**
     * 通用缓存存储模板方法
     *
     * @param cacheName 缓存名称（用于日志）
     * @param queryWrapper 查询条件
     * @param valueToCache 要缓存的对象
     * @param expireSeconds 过期时间（秒）
     * @param mapper 数据访问层
     * @param newEntitySupplier 新实体对象创建函数
     * @param cacheOperation 缓存数据设置操作，接收 (实体, 序列化后的JSON字符串, 过期时间)
     * @param <T> 缓存实体类型
     */
    private <T> void cacheWithTemplate(
            String cacheName,
            LambdaQueryWrapper<T> queryWrapper,
            Object valueToCache,
            long expireSeconds,
            BaseMapper<T> mapper,
            Supplier<T> newEntitySupplier,
            CacheSetter<T> cacheOperation) {
        try {
            String value = objectMapper.writeValueAsString(valueToCache);
            T existingCache = mapper.selectOne(queryWrapper);

            LocalDateTime expireTime = LocalDateTime.now().plusSeconds(expireSeconds);

            if (existingCache == null) {
                T newCache = newEntitySupplier.get();
                cacheOperation.setFields(newCache, value, expireTime);
                mapper.insert(newCache);
            } else {
                cacheOperation.setFields(existingCache, value, expireTime);
                mapper.updateById(existingCache);
            }

            logger.debug("缓存{}成功", cacheName);
        } catch (Exception e) {
            logger.error("缓存{}失败", cacheName, e);
        }
    }

    /**
     * 缓存字段设置函数式接口
     */
    @FunctionalInterface
    private interface CacheSetter<T> {
        void setFields(T entity, String serializedValue, LocalDateTime expireTime);
    }

    /**
     * 通用缓存获取模板方法
     * 
     * @param cacheName 缓存名称（用于日志）
     * @param queryWrapper 查询条件
     * @param mapper 数据访问层
     * @param valueExtractor 值提取函数
     * @param <T> 缓存实体类型
     * @return 反序列化后的缓存对象，如果不存在或已过期则返回null
     */
    private <T> Object getCachedWithTemplate(
            String cacheName,
            LambdaQueryWrapper<T> queryWrapper,
            BaseMapper<T> mapper,
            Function<T, String> valueExtractor) {
        T cache = mapper.selectOne(queryWrapper);
        
        if (cache != null) {
            try {
                String cacheValue = valueExtractor.apply(cache);
                return objectMapper.readValue(cacheValue, Object.class);
            } catch (Exception e) {
                logger.error("反序列化{}缓存失败", cacheName, e);
                return null;
            }
        }
        
        return null;
    }

    private String generateHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            logger.error("生成哈希失败", e);
            return String.valueOf(input.hashCode());
        }
    }
}
