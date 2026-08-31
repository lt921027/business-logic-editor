package com.businesslogic.controller;

import com.businesslogic.common.Result;
import com.businesslogic.dto.BuildInputDataRequestDTO;
import com.businesslogic.dto.BusinessLogicSaveDTO;
import com.businesslogic.dto.JsonPathParamDTO;
import com.businesslogic.groovy.engine.GroovyExecutor;
import com.businesslogic.service.BusinessLogicService;
import com.businesslogic.util.InputDataBuilder;
import com.businesslogic.vo.BusinessLogicVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/business-logic")
@CrossOrigin(origins = "*", maxAge = 3600)
public class BusinessLogicController {

    private static final Logger logger = LoggerFactory.getLogger(BusinessLogicController.class);

    private final BusinessLogicService businessLogicService;
    private final ObjectMapper objectMapper;

    public BusinessLogicController(BusinessLogicService businessLogicService, ObjectMapper objectMapper) {
        this.businessLogicService = businessLogicService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public Result<BusinessLogicVO> save(@Valid @RequestBody BusinessLogicSaveDTO dto) {
        logger.info("保存业务逻辑请求: {}", dto.getName());
        BusinessLogicVO result = businessLogicService.save(dto);
        return Result.success("保存成功", result);
    }

    @PutMapping("/{id}")
    public Result<BusinessLogicVO> update(
            @PathVariable Long id,
            @Valid @RequestBody BusinessLogicSaveDTO dto) {
        logger.info("更新业务逻辑请求: {}", dto.getName());
        BusinessLogicVO result = businessLogicService.update(id, dto);
        return Result.success("更新成功", result);
    }

    @GetMapping("/{id}")
    public Result<BusinessLogicVO> getById(@PathVariable Long id) {
        logger.info("查询业务逻辑请求: {}", id);
        BusinessLogicVO result = businessLogicService.getById(id);
        return Result.success(result);
    }

    @GetMapping
    public Result<List<BusinessLogicVO>> listAll() {
        logger.info("查询所有业务逻辑请求");
        List<BusinessLogicVO> result = businessLogicService.listAll();
        return Result.success(result);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        logger.info("删除业务逻辑请求: {}", id);
        businessLogicService.delete(id);
        return Result.success("删除成功", null);
    }

    @PostMapping("/{id}/execute")
    public Result<Map<String, Object>> execute(
            @PathVariable Long id,
            @RequestBody Map<String, Object> inputData) {
        logger.info("执行业务逻辑请求: {}", id);
        String jsonInput;
        try {
            jsonInput = objectMapper.writeValueAsString(inputData);
        } catch (Exception e) {
            logger.error("转换输入数据为JSON失败", e);
            throw new RuntimeException("转换输入数据为JSON失败", e);
        }
        String result = businessLogicService.executeLogic(id, jsonInput);
        Map<String, Object> response = new HashMap<>();
        response.put("result", result);
        return Result.success("执行成功", response);
    }

    /**
     * 根据前端传回的表达式的参数列表构建 inputData JSON
     *
     * <p>前端传回参数列表，每个参数是一个 {@link JsonPathParamDTO} 对象，包含：</p>
     * <ul>
     *   <li>{@code jsonPath}：节点路径（如 "$.amount"、"user.name"、"items[0].price"）</li>
     *   <li>{@code value}：用户输入的值；当 jsonType 为 object/array 时，直接输入 JSON 字符串</li>
     *   <li>{@code jsonType}：节点类型（string、number、boolean、null、object、array）</li>
     * </ul>
     *
     * <p>请求示例：</p>
     * <pre>{@code
     * {
     *   "expression": "def name = JsonPathUtil.readString(inputData, '$.user.name'); return name;",
     *   "params": [
     *     {"jsonPath": "$.amount", "value": "500", "jsonType": "number"},
     *     {"jsonPath": "$.user", "value": "{\"name\":\"Tom\",\"age\":30}", "jsonType": "object"}
     *   ]
     * }
     * }</pre>
     *
     * @param request 请求体，包含 expression（可选）和 params（JsonPathParamDTO 参数列表）
     * @return 构建好的 inputData JSON 字符串，若传了 expression 则附带执行结果
     */
    @PostMapping("/build-input-data")
    public Result<Map<String, Object>> buildInputData(@RequestBody BuildInputDataRequestDTO request) {
        List<JsonPathParamDTO> params = request.getParams();
        if (params == null || params.isEmpty()) {
            return Result.error("params 参数不能为空，需要传入参数列表");
        }

        String inputData = InputDataBuilder.buildInputDataJson(params);
        logger.info("构建 inputData 完成：{}", inputData);

        Map<String, Object> response = new HashMap<>();
        response.put("inputData", inputData);

        // 如果前端同时传回了表达式，直接执行并返回结果，方便调试
        String expression = request.getExpression();
        if (expression != null && !expression.trim().isEmpty()) {
            try {
                Object result = GroovyExecutor.execute(expression, inputData);
                response.put("result", result);
            } catch (Exception e) {
                logger.error("执行表达式失败", e);
                return Result.error("表达式执行失败: " + e.getMessage());
            }
        }

        return Result.success("inputData 构建成功", response);
    }

    @PostMapping("/generate-expression")
    public Result<Map<String, String>> generateExpression(@RequestBody BusinessLogicSaveDTO dto) {
        logger.info("生成表达式请 {}", dto.getName());
        String aviatorExpression = businessLogicService.generateExpression(dto);
        Map<String, String> response = new HashMap<>();
        response.put("aviatorExpression", aviatorExpression);
        return Result.success("生成成功", response);
    }
}
