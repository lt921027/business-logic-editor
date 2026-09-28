package com.businesslogic.groovy.controller;

import com.businesslogic.common.Result;
import com.businesslogic.dto.BusinessLogicSaveDTO;
import com.businesslogic.groovy.service.GroovyBusinessLogicService;
import com.businesslogic.oplog.OpLog;
import com.businesslogic.oplog.OpLogConsts;
import com.businesslogic.vo.BusinessLogicVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Groovy 业务逻辑控制器
 *
 * <p>使用独立的 URL 前缀 /groovy-business-logic/ 提供业务逻辑的保存、
 * 更新、查询、删除与执行接口。
 *
 * <p>关联体系：
 * <ul>
 *   <li>所有方法委托给 {@link GroovyBusinessLogicService} 处理</li>
 *   <li>使用 {@link ObjectMapper} 将 execute 接口的 Map 入参序列化为 JSON 字符串
 *       传给 service.executeLogic</li>
 *   <li>使用 {@link Result} 包装统一响应格式</li>
 *   <li>接口签名与业务逻辑服务保持对应，便于前端调用</li>
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
     * 保存特征（新增与修改合并）
     *
     * <p>请求体带 id 走修改，不带（null 或 0）走新增。判断放在这里而不是 Service，
     * 是为了让两个 Service 方法保持原有的单一职责与事务边界。</p>
     *
     * <p>操作日志由 {@link OpLog} 注解加切面自动记录，业务代码不需要登记：
     * 切面靠入参里的 id 区分新增与修改，并在方法执行前后各查一次库，
     * 得到修改前后的表达式与步骤快照。</p>
     *
     * @param dto 特征保存 DTO，修改时携带 id
     * @return 保存后的特征 VO
     */
    @OpLog(featureIdProperty = "id")
    @PostMapping
    public Result<BusinessLogicVO> save(@Valid @RequestBody BusinessLogicSaveDTO dto) {
        Long id = dto.getId();
        boolean update = id != null && id > 0;
        logger.info("[Groovy] {}特征请求: id={}, name={}", update ? "修改" : "新增", id, dto.getName());

        BusinessLogicVO result = update
                ? businessLogicService.update(id, dto)
                : businessLogicService.save(dto);
        return Result.success(update ? "更新成功" : "保存成功", result);
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
     * 删除特征（支持批量）
     *
     * <p>请求体是特征ID 数组，单个删除就是长度为 1 的数组。逐条委托
     * {@link GroovyBusinessLogicService#delete}，任意一条抛异常即中断并向上抛出，
     * 此时前面已删除的条目保持删除状态（部分成功）。</p>
     *
     * <p>操作日志由切面按批处理：每个特征一行，分别记录删除前的表达式与步骤快照；
     * 部分成功时，已删除的条目记为成功，其余记为失败并带上异常原因。</p>
     *
     * @param ids 特征ID 列表
     * @return 删除结果统计
     */
    @OpLog(operation = OpLogConsts.OP_DELETE, featureIdArg = 0)
    @DeleteMapping
    public Result<Map<String, Object>> delete(@RequestBody List<Long> ids) {
        logger.info("[Groovy] 删除特征请求: ids={}", ids);
        if (ids == null || ids.isEmpty()) {
            return Result.error("ids 不能为空");
        }

        List<Long> deleted = new ArrayList<>();
        for (Long id : ids) {
            if (id == null || id <= 0) {
                continue;
            }
            businessLogicService.delete(id);
            deleted.add(id);
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("requested", ids.size());
        resp.put("deleted", deleted.size());
        resp.put("ids", deleted);
        return Result.success("删除成功", resp);
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
