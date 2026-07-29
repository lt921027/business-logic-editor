package com.businesslogic.groovy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.businesslogic.dto.BusinessLogicSaveDTO;
import com.businesslogic.dto.CalculationStepDTO;
import com.businesslogic.dto.FilterItemDTO;
import com.businesslogic.dto.LogicStepDTO;
import com.businesslogic.dto.OperandDTO;
import com.businesslogic.entity.BusinessLogic;
import com.businesslogic.entity.LogicStep;
import com.businesslogic.groovy.cache.GroovyExpressionCache;
import com.businesslogic.groovy.engine.CompiledGroovyScript;
import com.businesslogic.groovy.engine.GroovyExecutor;
import com.businesslogic.groovy.generator.GroovyExpressionGenerator;
import com.businesslogic.mapper.BusinessLogicMapper;
import com.businesslogic.mapper.LogicStepMapper;
import com.businesslogic.vo.BusinessLogicVO;
import com.businesslogic.vo.CalculationStepVO;
import com.businesslogic.vo.FilterItemVO;
import com.businesslogic.vo.FilterLogicVO;
import com.businesslogic.vo.LogicStepVO;
import com.businesslogic.vo.OperandVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Groovy 业务逻辑服务
 *
 * <p>对应 Aviator 的 BusinessLogicService，业务逻辑保持一致。
 *
 * <p>差异点：
 * <ul>
 *   <li>使用 {@link GroovyExpressionGenerator} 生成 Groovy 脚本</li>
 *   <li>使用 {@link GroovyExecutor} 编译和执行</li>
 *   <li>使用 {@link GroovyExpressionCache} 缓存编译结果</li>
 *   <li>复用现有 BusinessLogic 实体和 LogicStep 实体（不修改现有文件）</li>
 *   <li>Groovy 脚本存储在 BusinessLogic.aviatorExpression 字段中（复用现有列）</li>
 * </ul>
 *
 * <p>注意：由于不修改现有文件，Groovy 业务逻辑与 Aviator 业务逻辑共享同一张表。
 * 调用方通过不同的 Controller 路径区分使用 Aviator 或 Groovy 引擎。
 *
 * <p>关联体系：
 * <ul>
 *   <li>继承 MyBatis Plus 的 ServiceImpl，复用 {@link BusinessLogicMapper} 进行 CRUD</li>
 *   <li>依赖 {@link LogicStepMapper} 操作步骤表</li>
 *   <li>依赖 {@link GroovyExpressionGenerator} 将 DTO 转 Groovy 脚本</li>
 *   <li>依赖 {@link GroovyExpressionCache} 缓存编译产物，避免每次执行重新编译</li>
 *   <li>被 {@link com.businesslogic.groovy.controller.GroovyBusinessLogicController} 调用</li>
 * </ul>
 */
@Service
public class GroovyBusinessLogicService extends ServiceImpl<BusinessLogicMapper, BusinessLogic> {

    private static final Logger logger = LoggerFactory.getLogger(GroovyBusinessLogicService.class);

    private final LogicStepMapper logicStepMapper;
    private final GroovyExpressionGenerator expressionGenerator;
    private final ObjectMapper objectMapper;
    private final GroovyExpressionCache expressionCache;

    /**
     * 构造器注入：所有依赖通过 Spring 注入，便于测试替换。
     *
     * @param logicStepMapper     步骤表 Mapper
     * @param expressionGenerator Groovy 表达式生成器
     * @param objectMapper        Jackson JSON 工具，用于序列化/反序列化步骤中的 JSON 字段
     * @param expressionCache     编译产物缓存
     */
    public GroovyBusinessLogicService(LogicStepMapper logicStepMapper,
                                       GroovyExpressionGenerator expressionGenerator,
                                       ObjectMapper objectMapper,
                                       GroovyExpressionCache expressionCache) {
        this.logicStepMapper = logicStepMapper;
        this.expressionGenerator = expressionGenerator;
        this.objectMapper = objectMapper;
        this.expressionCache = expressionCache;
    }

    /**
     * 保存业务逻辑
     *
     * <p>保存时预编译 Groovy 脚本并缓存，下次执行时直接命中缓存。
     *
     * <p>为何预编译：避免首次执行时的编译延迟，同时尽早发现语法错误。
     * 即使预编译失败也不阻断保存流程（catch 后仅记录日志），保证业务数据可正常落库，
     * 用户可在后续修正脚本后通过 update 接口刷新缓存。
     *
     * <p>关联：调用 {@link GroovyExpressionGenerator#generate} 生成脚本；
     * 通过 {@link GroovyExecutor#compile} 编译；通过 {@link GroovyExpressionCache#put} 缓存。
     *
     * @param dto 业务逻辑保存 DTO
     * @return 保存后的业务逻辑 VO
     */
    @Transactional(rollbackFor = Exception.class)
    public BusinessLogicVO save(BusinessLogicSaveDTO dto) {
        logger.info("[Groovy] 保存业务逻辑：{}", dto.getName());

        BusinessLogic businessLogic = new BusinessLogic();
        businessLogic.setName(dto.getName());
        businessLogic.setDescription(dto.getDescription());
        businessLogic.setJsonInput(dto.getJsonInput());
        businessLogic.setStepCount(dto.getLogicSteps().size());

        // 生成 Groovy 脚本（存储在 groovyExpression 字段中）
        String groovyScript = expressionGenerator.generate(dto);
        businessLogic.setGroovyExpression(groovyScript);

        // 插入数据
        baseMapper.insert(businessLogic);

        // 预编译 Groovy 脚本并缓存
        try {
            CompiledGroovyScript compiled = GroovyExecutor.compile(groovyScript);
            expressionCache.put(businessLogic.getId(), compiled);
            logger.info("[Groovy] 预编译表达式并缓存，ID: {}", businessLogic.getId());
        } catch (Exception e) {
            logger.error("[Groovy] 预编译表达式失败，ID: {}", businessLogic.getId(), e);
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
     *
     * <p>更新时重新编译 Groovy 脚本并刷新缓存，确保下次执行使用最新脚本。
     *
     * <p>为何先删后插步骤：步骤列表为整体替换语义，避免复杂的 diff 逻辑。
     * 在事务内执行，保证数据一致性。
     *
     * <p>关联：调用 {@link GroovyExpressionCache#refresh} 替换缓存中的旧编译产物。
     *
     * @param id  业务逻辑 ID
     * @param dto 新的业务逻辑数据
     * @return 更新后的业务逻辑 VO
     */
    @Transactional(rollbackFor = Exception.class)
    public BusinessLogicVO update(Long id, BusinessLogicSaveDTO dto) {
        logger.info("[Groovy] 更新业务逻辑：{}", dto.getName());

        BusinessLogic businessLogic = baseMapper.selectById(id);
        if (businessLogic == null) {
            throw new RuntimeException("业务逻辑不存在：" + id);
        }

        businessLogic.setName(dto.getName());
        businessLogic.setDescription(dto.getDescription());
        businessLogic.setJsonInput(dto.getJsonInput());
        businessLogic.setStepCount(dto.getLogicSteps().size());

        // 生成新的 Groovy 脚本
        String groovyScript = expressionGenerator.generate(dto);
        businessLogic.setGroovyExpression(groovyScript);

        // 更新数据
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

        // 刷新缓存中的 Groovy 脚本
        try {
            CompiledGroovyScript compiled = GroovyExecutor.compile(groovyScript);
            expressionCache.refresh(id, compiled);
            logger.info("[Groovy] 刷新表达式缓存，ID: {}", id);
        } catch (Exception e) {
            logger.error("[Groovy] 刷新表达式缓存失败，ID: {}", id, e);
        }

        return convertToVO(businessLogic, steps);
    }

    /**
     * 删除业务逻辑
     *
     * <p>删除时同时清除缓存，避免缓存中残留已删除业务逻辑的编译产物。
     *
     * <p>关联：调用 {@link GroovyExpressionCache#remove} 清理缓存。
     *
     * @param id 业务逻辑 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        logger.info("[Groovy] 删除业务逻辑，ID: {}", id);

        // 删除逻辑步骤
        LambdaQueryWrapper<LogicStep> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(LogicStep::getBusinessLogicId, id);
        logicStepMapper.delete(deleteWrapper);

        // 删除业务逻辑
        baseMapper.deleteById(id);

        // 清除缓存
        expressionCache.remove(id);
        logger.info("[Groovy] 清除表达式缓存，ID: {}", id);
    }

    /**
     * 执行业务逻辑
     *
     * <p>优先使用缓存的预编译 Groovy 脚本，如果缓存不存在则重新编译。
     *
     * <p>为何需要缓存回退：服务重启后缓存为空，需从数据库加载脚本重新编译；
     * 同时兼容缓存被意外清空的场景。
     *
     * <p>关联：通过 {@link GroovyExpressionCache#get} 获取缓存；
     * 通过 {@link GroovyExecutor#execute} 执行脚本。
     *
     * @param id        业务逻辑 ID
     * @param inputData 输入数据（JSON 字符串）
     * @return 执行结果（JSON 字符串）
     */
    public String executeLogic(Long id, String inputData) {
        logger.info("[Groovy] 执行业务逻辑，ID: {}, 输入数据：{}", id, inputData);

        BusinessLogic businessLogic = baseMapper.selectById(id);
        if (businessLogic == null) {
            throw new RuntimeException("业务逻辑不存在：" + id);
        }

        try {
            // 尝试从缓存获取预编译脚本
            CompiledGroovyScript compiled = expressionCache.get(id);

            Object result;
            if (compiled != null) {
                // 使用缓存的预编译脚本执行
                logger.debug("[Groovy] 使用缓存的预编译表达式执行，ID: {}", id);
                result = GroovyExecutor.execute(compiled, inputData);
            } else {
                // 缓存不存在，重新编译并缓存
                logger.warn("[Groovy] 缓存未命中，重新编译表达式，ID: {}", id);
                compiled = GroovyExecutor.compile(businessLogic.getGroovyExpression());
                expressionCache.put(id, compiled);
                result = GroovyExecutor.execute(compiled, inputData);
            }

            logger.info("[Groovy] 执行结果：{}", result);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            logger.error("[Groovy] 执行 Groovy 表达式失败", e);
            throw new RuntimeException("执行业务逻辑失败：" + e.getMessage(), e);
        }
    }

    /**
     * 获取业务逻辑详情
     *
     * <p>关联：通过 {@link LogicStepMapper} 查询步骤列表，按 stepOrder 升序排列。
     *
     * @param id 业务逻辑 ID
     * @return 业务逻辑 VO（含步骤列表）
     */
    public BusinessLogicVO getDetail(Long id) {
        logger.info("[Groovy] 获取业务逻辑详情，ID: {}", id);

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
     * 根据 ID 获取业务逻辑（别名方法）
     *
     * <p>为何提供别名：与 Aviator 版本的 BusinessLogicService 接口保持一致，
     * 方便调用方使用更直观的方法名。
     *
     * @param id 业务逻辑 ID
     * @return 业务逻辑 VO
     */
    public BusinessLogicVO getById(Long id) {
        return getDetail(id);
    }

    /**
     * 获取所有业务逻辑
     *
     * <p>关联：调用 {@link #convertToVOWithSteps} 为每条业务逻辑附加步骤列表。
     * 注意 N+1 查询问题，数据量大时建议改为批量查询。
     *
     * @return 业务逻辑 VO 列表
     */
    public List<BusinessLogicVO> listAll() {
        logger.info("[Groovy] 获取所有业务逻辑");
        LambdaQueryWrapper<BusinessLogic> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(BusinessLogic::getCreatedAt);
        List<BusinessLogic> businessLogics = baseMapper.selectList(queryWrapper);
        return businessLogics.stream()
                .map(this::convertToVOWithSteps)
                .collect(Collectors.toList());
    }

    /**
     * 生成 Groovy 表达式（不保存）
     *
     * <p>供前端预览生成的 Groovy 脚本，确认无误后再调用 save 接口。
     *
     * <p>关联：直接委托 {@link GroovyExpressionGenerator#generate}。
     *
     * @param dto 业务逻辑 DTO
     * @return Groovy 脚本字符串
     */
    public String generateExpression(BusinessLogicSaveDTO dto) {
        logger.info("[Groovy] 生成 Groovy 表达式：{}", dto.getName());
        return expressionGenerator.generate(dto);
    }

    // ==================== 实体转换 ====================

    /**
     * DTO 转换为 LogicStep 实体列表
     *
     * <p>为何需要转换：前端传入的 DTO 结构与数据库实体不一致，DTO 中嵌套对象列表
     * （calculationSteps、filterItems 等）需序列化为 JSON 字符串存入实体的对应字段。
     *
     * <p>关联：调用 {@link #convertCalculationStepsToVO} / {@link #convertFilterItemsToVO}
     * 将 DTO 转为 VO 后再序列化，VO 结构与前端展示一致。
     *
     * @param dtoSteps        DTO 步骤列表
     * @param businessLogicId 所属业务逻辑 ID
     * @return 实体步骤列表
     */
    private List<LogicStep> convertToLogicSteps(List<LogicStepDTO> dtoSteps, Long businessLogicId) {
        return dtoSteps.stream().map(dto -> {
            LogicStep step = new LogicStep();
            step.setBusinessLogicId(businessLogicId);
            step.setStepOrder(dto.getStepOrder());
            step.setFunctionCategory(dto.getFunctionCategory() == null ? "" : dto.getFunctionCategory()[0]);
            step.setField(dto.getField());
            step.setFunctionName(dto.getFunctionName());
            step.setParams(dto.getParams() != null ? String.join(",", dto.getParams()) : null);
            step.setCustomExpression(dto.getCustomExpression());
            step.setOutputVar(dto.getOutputVar());
            step.setComment(dto.getComment());
            step.setFilterScope(dto.getFilterScope());
            step.setMappedField(dto.getMappedField());
            try {
                List<CalculationStepVO> calcStepsForStorage = convertCalculationStepsToVO(dto.getCalculationSteps());
                List<FilterItemVO> filterItemsForStorage = convertFilterItemsToVO(dto.getFilterItems());
                step.setCalculationSteps(calcStepsForStorage != null ? objectMapper.writeValueAsString(calcStepsForStorage) : null);
                step.setFilterItems(filterItemsForStorage != null ? objectMapper.writeValueAsString(filterItemsForStorage) : null);
                step.setFilterLogic(dto.getFilterLogic() != null ? objectMapper.writeValueAsString(dto.getFilterLogic()) : null);
                step.setReverseLogic(dto.getReverseLogic() != null ? objectMapper.writeValueAsString(dto.getReverseLogic()) : null);
            } catch (Exception e) {
                logger.error("[Groovy] 序列化逻辑步骤 JSON 失败", e);
            }
            return step;
        }).collect(Collectors.toList());
    }

    /**
     * CalculationStepDTO 列表转换为 CalculationStepVO 列表
     *
     * <p>为何 DTO 转 VO 再入库：VO 结构与前端展示对齐，便于查询时直接反序列化返回前端，
     * 避免查询时再做转换。同时 VO 中 functionCategory 从数组降为单值字符串，简化存储。
     *
     * <p>关联：调用 {@link #convertOperandsToVO} 转换操作数列表。
     */
    private List<CalculationStepVO> convertCalculationStepsToVO(List<CalculationStepDTO> steps) {
        if (steps == null || steps.isEmpty()) return null;

        return steps.stream().map(step -> {
            CalculationStepVO vo = new CalculationStepVO();
            vo.setId(step.getId());
            vo.setLogicOperator(step.getLogicOperator());
            vo.setFunctionCategory(step.getFunctionCategory() != null && step.getFunctionCategory().length > 0
                    ? step.getFunctionCategory()[0] : "");
            vo.setFilterFunction(step.getFilterFunction());
            vo.setOperands(convertOperandsToVO(step.getOperands()));
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * FilterItemDTO 列表转换为 FilterItemVO 列表
     *
     * <p>为何递归调用自身：FilterItem 支持嵌套（items 字段），形成树形结构，
     * 需递归处理子项。
     *
     * <p>关联：调用 {@link #convertOperandsToVO} 转换操作数；
     * 递归调用自身转换子项 items。
     */
    private List<FilterItemVO> convertFilterItemsToVO(List<FilterItemDTO> items) {
        if (items == null || items.isEmpty()) return null;

        return items.stream().map(item -> {
            FilterItemVO vo = new FilterItemVO();
            vo.setId(item.getId());
            vo.setType(item.getType());
            vo.setLogicOperator(item.getLogicOperator());
            vo.setFunctionCategory(item.getFunctionCategory() != null && item.getFunctionCategory().length > 0
                    ? item.getFunctionCategory()[0] : "");
            vo.setFilterFunction(item.getFilterFunction());
            vo.setOperands(convertOperandsToVO(item.getOperands()));
            vo.setLevel(item.getLevel());
            vo.setItems(convertFilterItemsToVO(item.getItems()));
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * OperandDTO 列表转换为 OperandVO 列表
     *
     * <p>叶子节点转换，无嵌套结构。
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
     *
     * <p>反向转换：将实体中 JSON 字符串字段反序列化为 VO 嵌套对象列表。
     * Groovy 脚本使用 groovyExpression 字段存储。
     *
     * <p>关联：被 {@link #getDetail} / {@link #convertToVOWithSteps} 调用。
     *
     * @param businessLogic 业务逻辑实体
     * @param steps          步骤实体列表
     * @return 业务逻辑 VO
     */
    private BusinessLogicVO convertToVO(BusinessLogic businessLogic, List<LogicStep> steps) {
        BusinessLogicVO vo = new BusinessLogicVO();
        vo.setId(businessLogic.getId());
        vo.setName(businessLogic.getName());
        vo.setDescription(businessLogic.getDescription());
        vo.setJsonInput(businessLogic.getJsonInput());
        // Groovy 脚本存储在 groovyExpression 字段中
        vo.setGroovyExpression(businessLogic.getGroovyExpression());
        vo.setStepCount(businessLogic.getStepCount());
        vo.setCreatedAt(businessLogic.getCreatedAt());
        vo.setUpdatedAt(businessLogic.getUpdatedAt());

        List<LogicStepVO> stepVOs = steps.stream().map(step -> {
            LogicStepVO stepVO = new LogicStepVO();
            stepVO.setId(step.getId());
            stepVO.setStepOrder(step.getStepOrder());
            stepVO.setFunctionCategory(step.getFunctionCategory());
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
                logger.error("[Groovy] 解析逻辑步骤 JSON 失败", e);
            }
            return stepVO;
        }).collect(Collectors.toList());
        vo.setLogicSteps(stepVOs);

        return vo;
    }

    /**
     * 将实体转换为 VO（带步骤，用于 listAll）
     *
     * <p>为何单独提供：listAll 场景下仅有业务逻辑实体，需额外查询步骤列表，
     * 此方法封装"查询步骤 + 转换 VO"两步操作。
     *
     * <p>关联：被 {@link #listAll} 调用；内部调用 {@link #convertToVO}。
     *
     * @param businessLogic 业务逻辑实体
     * @return 含步骤的业务逻辑 VO
     */
    private BusinessLogicVO convertToVOWithSteps(BusinessLogic businessLogic) {
        LambdaQueryWrapper<LogicStep> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(LogicStep::getBusinessLogicId, businessLogic.getId());
        queryWrapper.orderByAsc(LogicStep::getStepOrder);
        List<LogicStep> steps = logicStepMapper.selectList(queryWrapper);

        return convertToVO(businessLogic, steps);
    }
}
