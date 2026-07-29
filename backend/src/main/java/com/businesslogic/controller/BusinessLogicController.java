package com.businesslogic.controller;

import com.businesslogic.common.Result;
import com.businesslogic.dto.BusinessLogicSaveDTO;
import com.businesslogic.service.BusinessLogicService;
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

    @PostMapping("/generate-expression")
    public Result<Map<String, String>> generateExpression(@RequestBody BusinessLogicSaveDTO dto) {
        logger.info("生成表达式请 {}", dto.getName());
        String aviatorExpression = businessLogicService.generateExpression(dto);
        Map<String, String> response = new HashMap<>();
        response.put("aviatorExpression", aviatorExpression);
        return Result.success("生成成功", response);
    }
}
