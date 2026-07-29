package com.businesslogic.groovy.controller;

import com.businesslogic.common.Result;
import com.businesslogic.dto.BusinessLogicSaveDTO;
import com.businesslogic.groovy.service.GroovyBusinessLogicService;
import com.businesslogic.vo.BusinessLogicVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Groovy 业务逻辑控制器
 *
 * <p>对应 Aviator 的 BusinessLogicController，业务逻辑保持一致。
 *
 * <p>使用独立的 URL 前缀 /groovy-business-logic/ 与 Aviator 控制器隔离，
 * 调用方可根据需要选择使用 Aviator 或 Groovy 引擎。
 *
 * <p>关联体系：
 * <ul>
 *   <li>所有方法委托给 {@link GroovyBusinessLogicService} 处理</li>
 *   <li>使用 {@link ObjectMapper} 将 execute 接口的 Map 入参序列化为 JSON 字符串
 *       传给 service.executeLogic</li>
 *   <li>使用 {@link Result} 包装统一响应格式</li>
 *   <li>与 Aviator 版 BusinessLogicController 路径不同，但接口签名保持一致，便于前端切换</li>
 * </ul>
 */
@RestController
@RequestMapping("/groovy-business-logic")
@CrossOrigin(origins = "*", maxAge = 3600)
public class GroovyBusinessLogicController {

    private static final Logger logger = LoggerFactory.getLogger(GroovyBusinessLogicController.class);

    private final GroovyBusinessLogicService businessLogicService;
    private final ObjectMapper objectMapper;

    /**
     * 构造器注入。
     *
     * @param businessLogicService Groovy 业务逻辑服务
     * @param objectMapper         Jackson JSON 工具，用于 execute 接口的入参序列化
     */
    public GroovyBusinessLogicController(GroovyBusinessLogicService businessLogicService,
                                          ObjectMapper objectMapper) {
        this.businessLogicService = businessLogicService;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存业务逻辑
     *
     * <p>关联：委托 {@link GroovyBusinessLogicService#save}。
     *
     * @param dto 业务逻辑保存 DTO
     * @return 保存后的业务逻辑 VO
     */
    @PostMapping
    public Result<BusinessLogicVO> save(@Valid @RequestBody BusinessLogicSaveDTO dto) {
        logger.info("[Groovy] 保存业务逻辑请求: {}", dto.getName());
        BusinessLogicVO result = businessLogicService.save(dto);
        return Result.success("保存成功", result);
    }

    /**
     * 更新业务逻辑
     *
     * <p>关联：委托 {@link GroovyBusinessLogicService#update}。
     *
     * @param id  业务逻辑 ID
     * @param dto 新的业务逻辑数据
     * @return 更新后的业务逻辑 VO
     */
    @PutMapping("/{id}")
    public Result<BusinessLogicVO> update(
            @PathVariable Long id,
            @Valid @RequestBody BusinessLogicSaveDTO dto) {
        logger.info("[Groovy] 更新业务逻辑请求: {}", dto.getName());
        BusinessLogicVO result = businessLogicService.update(id, dto);
        return Result.success("更新成功", result);
    }

    /**
     * 根据 ID 查询业务逻辑详情
     *
     * <p>关联：委托 {@link GroovyBusinessLogicService#getById}。
     *
     * @param id 业务逻辑 ID
     * @return 业务逻辑 VO
     */
    @GetMapping("/{id}")
    public Result<BusinessLogicVO> getById(@PathVariable Long id) {
        logger.info("[Groovy] 查询业务逻辑请求: {}", id);
        BusinessLogicVO result = businessLogicService.getById(id);
        return Result.success(result);
    }

    /**
     * 查询所有业务逻辑
     *
     * <p>关联：委托 {@link GroovyBusinessLogicService#listAll}。
     *
     * @return 业务逻辑 VO 列表
     */
    @GetMapping
    public Result<List<BusinessLogicVO>> listAll() {
        logger.info("[Groovy] 查询所有业务逻辑请求");
        List<BusinessLogicVO> result = businessLogicService.listAll();
        return Result.success(result);
    }

    /**
     * 删除业务逻辑
     *
     * <p>关联：委托 {@link GroovyBusinessLogicService#delete}。
     *
     * @param id 业务逻辑 ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        logger.info("[Groovy] 删除业务逻辑请求: {}", id);
        businessLogicService.delete(id);
        return Result.success("删除成功", null);
    }

    /**
     * 执行业务逻辑
     *
     * <p>为何前端传 Map 后端转 JSON：前端以 JSON 对象形式传输入入参数据更直观，
     * service 层接受 JSON 字符串以便 GroovyExecutor 通过 JsonPathUtil 解析字段。
     *
     * <p>关联：委托 {@link GroovyBusinessLogicService#executeLogic}。
     *
     * @param id        业务逻辑 ID
     * @param inputData 输入数据（Map 形式）
     * @return 执行结果包装在 Map 中
     */
    @PostMapping("/{id}/execute")
    public Result<Map<String, Object>> execute(
            @PathVariable Long id,
            @RequestBody Map<String, Object> inputData) {
        logger.info("[Groovy] 执行业务逻辑请求: {}", id);
        String jsonInput;
        try {
            jsonInput = objectMapper.writeValueAsString(inputData);
        } catch (Exception e) {
            logger.error("[Groovy] 转换输入数据为JSON失败", e);
            throw new RuntimeException("转换输入数据为JSON失败", e);
        }
        String result = businessLogicService.executeLogic(id, jsonInput);
        Map<String, Object> response = new HashMap<>();
        response.put("result", result);
        return Result.success("执行成功", response);
    }

    /**
     * 生成 Groovy 表达式（不保存）
     *
     * <p>供前端预览生成的 Groovy 脚本。响应中使用 groovyExpression 字段名。
     *
     * <p>关联：委托 {@link GroovyBusinessLogicService#generateExpression}。
     *
     * @param dto 业务逻辑 DTO
     * @return 含 Groovy 脚本的响应
     */
    @PostMapping("/generate-expression")
    public Result<Map<String, String>> generateExpression(@RequestBody BusinessLogicSaveDTO dto) {
        logger.info("[Groovy] 生成表达式请求: {}", dto.getName());
        String groovyExpression = businessLogicService.generateExpression(dto);
        Map<String, String> response = new HashMap<>();
        response.put("groovyExpression", groovyExpression);
        return Result.success("生成成功", response);
    }
}
