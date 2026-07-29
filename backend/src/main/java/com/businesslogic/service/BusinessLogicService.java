package com.businesslogic.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.businesslogic.cache.ExpressionCache;
import com.businesslogic.dto.*;
import com.businesslogic.entity.BusinessLogic;
import com.businesslogic.entity.LogicStep;
import com.businesslogic.executor.AviatorExecutor;
import com.businesslogic.generator.AviatorExpressionGenerator;
import com.businesslogic.mapper.BusinessLogicMapper;
import com.businesslogic.mapper.LogicStepMapper;
import com.businesslogic.vo.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.googlecode.aviator.Expression;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 业务逻辑服务 * 负责业务逻辑的保存、更新、删除和执行
 */
@Service
public class BusinessLogicService extends ServiceImpl<BusinessLogicMapper, BusinessLogic> {

    private static final Logger logger = LoggerFactory.getLogger(BusinessLogicService.class);

    private final LogicStepMapper logicStepMapper;
    private final AviatorExpressionGenerator expressionGenerator;
    private final ObjectMapper objectMapper;
    private final ExpressionCache expressionCache;

    public BusinessLogicService(LogicStepMapper logicStepMapper, AviatorExpressionGenerator expressionGenerator, ObjectMapper objectMapper, ExpressionCache expressionCache) {
        this.logicStepMapper = logicStepMapper;
        this.expressionGenerator = expressionGenerator;
        this.objectMapper = objectMapper;
        this.expressionCache = expressionCache;
    }

    /**
     * 保存业务逻辑
     * 保存时预编译表达式并缓存
     * 
     * @param dto 业务逻辑保存 DTO
     * @return 业务逻辑 VO
     */
    @Transactional(rollbackFor = Exception.class)
    public BusinessLogicVO save(BusinessLogicSaveDTO dto) {
        logger.info("保存业务逻辑：{}", dto.getName());

        BusinessLogic businessLogic = new BusinessLogic();
        businessLogic.setName(dto.getName());
        businessLogic.setDescription(dto.getDescription());
        businessLogic.setJsonInput(dto.getJsonInput());
        businessLogic.setStepCount(dto.getLogicSteps().size());

        //生成 Aviator 表达
        String aviatorExpression = expressionGenerator.generate(dto);
        businessLogic.setAviatorExpression(aviatorExpression);

        //插入数据
        baseMapper.insert(businessLogic);

        //预编译表达式并缓
        try {
            Expression compiledExpression = AviatorExecutor.compile(aviatorExpression);
            expressionCache.put(businessLogic.getId(), compiledExpression);
            logger.info("预编译表达式并缓存，ID: {}", businessLogic.getId());
        } catch (Exception e) {
            logger.error("预编译表达式失败，ID: {}", businessLogic.getId(), e);
        }

        // 保存逻辑步骤
        List<LogicStep> steps = convertToLogicSteps(dto.getLogicSteps(), businessLogic.getId());
        for (LogicStep step : steps) {
            logicStepMapper.insert(step);
        }

        return convertToVO(businessLogic, steps);
    }

    /**
     * 更新业务逻辑
     * 更新时重新编译表达式并刷新缓     * 
     * @param id 业务逻辑 ID
     * @param dto 业务逻辑保存 DTO
     * @return 业务逻辑 VO
     */
    @Transactional(rollbackFor = Exception.class)
    public BusinessLogicVO update(Long id, BusinessLogicSaveDTO dto) {
        logger.info("更新业务逻辑：{}", dto.getName());

        BusinessLogic businessLogic = baseMapper.selectById(id);
        if (businessLogic == null) {
            throw new RuntimeException("业务逻辑不存在：" + id);
        }

        businessLogic.setName(dto.getName());
        businessLogic.setDescription(dto.getDescription());
        businessLogic.setJsonInput(dto.getJsonInput());
        businessLogic.setStepCount(dto.getLogicSteps().size());

        //生成新的 Aviator 表达
        String aviatorExpression = expressionGenerator.generate(dto);
        businessLogic.setAviatorExpression(aviatorExpression);

        //更新数据
        baseMapper.updateById(businessLogic);

        // 删除旧的逻辑步骤
        LambdaQueryWrapper<LogicStep> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(LogicStep::getBusinessLogicId, id);
        logicStepMapper.delete(deleteWrapper);

        // 保存新的逻辑步骤
        List<LogicStep> steps = convertToLogicSteps(dto.getLogicSteps(), id);
        for (LogicStep step : steps) {
            logicStepMapper.insert(step);
        }

        //刷新缓存中的表达
        try {
            Expression compiledExpression = AviatorExecutor.compile(aviatorExpression);
            expressionCache.refresh(id, compiledExpression);
            logger.info("刷新表达式缓存，ID: {}", id);
        } catch (Exception e) {
            logger.error("刷新表达式缓存失败，ID: {}", id, e);
        }

        return convertToVO(businessLogic, steps);
    }

    /**
     * 删除业务逻辑
     * 删除时同时清除缓     * 
     * @param id 业务逻辑 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        logger.info("删除业务逻辑，ID: {}", id);

        // 删除逻辑步骤
        LambdaQueryWrapper<LogicStep> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(LogicStep::getBusinessLogicId, id);
        logicStepMapper.delete(deleteWrapper);

        // 删除业务逻辑
        baseMapper.deleteById(id);

        // 清除缓存
        expressionCache.remove(id);
        logger.info("清除表达式缓存，ID: {}", id);
    }

    /**
     * 执行业务逻辑
     * 优先使用缓存的预编译表达式，如果缓存不存在则重新编译
     * 
     * @param id 业务逻辑 ID
     * @param inputData 输入数据（JSON 字符串）
     * @return 执行结果（JSON 字符串）
     */
    public String executeLogic(Long id, String inputData) {
        logger.info("执行业务逻辑，ID: {}, 输入数据：{}", id, inputData);

        BusinessLogic businessLogic = baseMapper.selectById(id);
        if (businessLogic == null) {
            throw new RuntimeException("业务逻辑不存在：" + id);
        }

        try {
            //尝试从缓存获取预编译表达
            Expression compiledExpression = expressionCache.get(id);
            
            Object result;
            if (compiledExpression != null) {
                //使用缓存的预编译表达式执
                logger.debug("使用缓存的预编译表达式执行，ID: {}", id);
                result = AviatorExecutor.execute(compiledExpression, inputData);
            } else {
                //缓存不存在，重新编译并缓
                logger.warn("缓存未命中，重新编译表达式，ID: {}", id);
                compiledExpression = AviatorExecutor.compile(businessLogic.getAviatorExpression());
                expressionCache.put(id, compiledExpression);
                result = AviatorExecutor.execute(compiledExpression, inputData);
            }

            logger.info("执行结果：{}", result);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            logger.error("执行 Aviator 表达式失", e);
            throw new RuntimeException("执行业务逻辑失败" + e.getMessage(), e);
        }
    }

    /**
     * 获取业务逻辑详情
     * 
     * @param id 业务逻辑 ID
     * @return 业务逻辑 VO
     */
    public BusinessLogicVO getDetail(Long id) {
        logger.info("获取业务逻辑详情，ID: {}", id);

        BusinessLogic businessLogic = baseMapper.selectById(id);
        if (businessLogic == null) {
            throw new RuntimeException("业务逻辑不存在：" + id);
        }

        LambdaQueryWrapper<LogicStep> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(LogicStep::getBusinessLogicId, id);
        queryWrapper.orderByAsc(LogicStep::getStepOrder);
        List<LogicStep> steps = logicStepMapper.selectList(queryWrapper);

        return convertToVO(businessLogic, steps);
    }

    /**
     * 根据 ID 获取业务逻辑（别名方法，兼容 Controller 调用     * 
     * @param id 业务逻辑 ID
     * @return 业务逻辑 VO
     */
    public BusinessLogicVO getById(Long id) {
        return getDetail(id);
    }

    /**
     * 获取所有业务逻辑
     * 
     * @return 业务逻辑 VO 列表
     */
    public List<BusinessLogicVO> listAll() {
        logger.info("获取所有业务逻辑");
        LambdaQueryWrapper<BusinessLogic> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(BusinessLogic::getCreatedAt);
        List<BusinessLogic> businessLogics = baseMapper.selectList(queryWrapper);
        return businessLogics.stream()
                .map(this::convertToVOWithSteps)
                .collect(Collectors.toList());
    }

    /**
     * 生成 Aviator 表达式（不保存）
     * 
     * @param dto 业务逻辑保存 DTO
     * @return Aviator 表达     */
    public String generateExpression(BusinessLogicSaveDTO dto) {
        logger.info("生成 Aviator 表达式：{}", dto.getName());
        return expressionGenerator.generate(dto);
    }

    /**
     * DTO 转换LogicStep 实体列表
     */
    private List<LogicStep> convertToLogicSteps(List<LogicStepDTO> dtoSteps, Long businessLogicId) {
        return dtoSteps.stream().map(dto -> {
            LogicStep step = new LogicStep();
            step.setBusinessLogicId(businessLogicId);
            step.setStepOrder(dto.getStepOrder());
            step.setFunctionCategory(dto.getFunctionCategory()==null?"":dto.getFunctionCategory()[0]);
            step.setField(dto.getField());
            step.setFunctionName(dto.getFunctionName());
            step.setParams(dto.getParams() != null ? String.join(",", dto.getParams()) : null);
            step.setCustomExpression(dto.getCustomExpression());
            step.setOutputVar(dto.getOutputVar());
            step.setComment(dto.getComment());
            step.setFilterScope(dto.getFilterScope());
            step.setMappedField(dto.getMappedField());
            try {
                //序列化前DTO 转换VO（VO functionCategory String 类型
                List<CalculationStepVO> calcStepsForStorage = convertCalculationStepsToVO(dto.getCalculationSteps());
                List<FilterItemVO> filterItemsForStorage = convertFilterItemsToVO(dto.getFilterItems());
                step.setCalculationSteps(calcStepsForStorage != null ? objectMapper.writeValueAsString(calcStepsForStorage) : null);
                step.setFilterItems(filterItemsForStorage != null ? objectMapper.writeValueAsString(filterItemsForStorage) : null);
                step.setFilterLogic(dto.getFilterLogic() != null ? objectMapper.writeValueAsString(dto.getFilterLogic()) : null);
                step.setReverseLogic(dto.getReverseLogic() != null ? objectMapper.writeValueAsString(dto.getReverseLogic()) : null);
            } catch (Exception e) {
                logger.error("序列化逻辑步骤 JSON 失败", e);
            }
            return step;
        }).collect(Collectors.toList());
    }

    /**
     * CalculationStepDTO 列表转换CalculationStepVO 列表（用于存储，functionCategory 为字符串     */
    private List<CalculationStepVO> convertCalculationStepsToVO(List<CalculationStepDTO> steps) {
        if (steps == null || steps.isEmpty()) return null;
        
        return steps.stream().map(step -> {
            CalculationStepVO vo = new CalculationStepVO();
            vo.setId(step.getId());
            vo.setLogicOperator(step.getLogicOperator());
            // String[] 转为 String
            vo.setFunctionCategory(step.getFunctionCategory() != null && step.getFunctionCategory().length > 0 
                ? step.getFunctionCategory()[0] : "");
            vo.setFilterFunction(step.getFilterFunction());
            vo.setOperands(convertOperandsToVO(step.getOperands()));
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * FilterItemDTO 列表转换FilterItemVO 列表（用于存储，functionCategory 为字符串     */
    private List<FilterItemVO> convertFilterItemsToVO(List<FilterItemDTO> items) {
        if (items == null || items.isEmpty()) return null;
        
        return items.stream().map(item -> {
            FilterItemVO vo = new FilterItemVO();
            vo.setId(item.getId());
            vo.setType(item.getType());
            vo.setLogicOperator(item.getLogicOperator());
            // String[] 转为 String
            vo.setFunctionCategory(item.getFunctionCategory() != null && item.getFunctionCategory().length > 0 
                ? item.getFunctionCategory()[0] : "");
            vo.setFilterFunction(item.getFilterFunction());
            vo.setOperands(convertOperandsToVO(item.getOperands()));
            vo.setLevel(item.getLevel());
            // 递归处理子项
            vo.setItems(convertFilterItemsToVO(item.getItems()));
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * OperandDTO 列表转换OperandVO 列表
     */
    private List<OperandVO> convertOperandsToVO(List<OperandDTO> operands) {
        if (operands == null || operands.isEmpty()) return null;

        return operands.stream().map(operand -> {
            OperandVO vo = new OperandVO();
            vo.setId(operand.getId());
            vo.setType(operand.getType());
            vo.setTypeValue(operand.getTypeValue());
            vo.setTip(operand.getTip());
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 将实体转换为 VO
     */
    private BusinessLogicVO convertToVO(BusinessLogic businessLogic, List<LogicStep> steps) {
        BusinessLogicVO vo = new BusinessLogicVO();
        vo.setId(businessLogic.getId());
        vo.setName(businessLogic.getName());
        vo.setDescription(businessLogic.getDescription());
        vo.setJsonInput(businessLogic.getJsonInput());
        vo.setAviatorExpression(businessLogic.getAviatorExpression());
        vo.setStepCount(businessLogic.getStepCount());
        vo.setCreatedAt(businessLogic.getCreatedAt());
        vo.setUpdatedAt(businessLogic.getUpdatedAt());

        List<LogicStepVO> stepVOs = steps.stream().map(step -> {
            LogicStepVO stepVO = new LogicStepVO();
            stepVO.setId(step.getId());
            stepVO.setStepOrder(step.getStepOrder());
            stepVO.setFunctionCategory(step.getFunctionCategory());  // 直接传递字符串
            stepVO.setField(step.getField());
            stepVO.setFunctionName(step.getFunctionName());
            stepVO.setParams(step.getParams() != null ? Arrays.asList(step.getParams().split(",")) : null);
            stepVO.setCustomExpression(step.getCustomExpression());
            stepVO.setOutputVar(step.getOutputVar());
            stepVO.setComment(step.getComment());
            stepVO.setFilterScope(step.getFilterScope());
            stepVO.setMappedField(step.getMappedField());
            try {
                stepVO.setCalculationSteps(step.getCalculationSteps() != null ? objectMapper.readValue(step.getCalculationSteps(), new TypeReference<List<CalculationStepVO>>() {}) : null);
                stepVO.setFilterItems(step.getFilterItems() != null ? objectMapper.readValue(step.getFilterItems(), new TypeReference<List<FilterItemVO>>() {}) : null);
                stepVO.setFilterLogic(step.getFilterLogic() != null ? objectMapper.readValue(step.getFilterLogic(), new TypeReference<List<FilterLogicVO>>() {}) : null);
                stepVO.setReverseLogic(step.getReverseLogic() != null ? objectMapper.readValue(step.getReverseLogic(), new TypeReference<List<FilterLogicVO>>() {}) : null);
            } catch (Exception e) {
                logger.error("解析逻辑步骤 JSON 失败", e);
            }
            return stepVO;
        }).collect(Collectors.toList());
        vo.setLogicSteps(stepVOs);

        return vo;
    }

    /**
     * 将实体转换为 VO（带步骤，用listAll     */
    private BusinessLogicVO convertToVOWithSteps(BusinessLogic businessLogic) {
        LambdaQueryWrapper<LogicStep> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(LogicStep::getBusinessLogicId, businessLogic.getId());
        queryWrapper.orderByAsc(LogicStep::getStepOrder);
        List<LogicStep> steps = logicStepMapper.selectList(queryWrapper);

        return convertToVO(businessLogic, steps);
    }
}
