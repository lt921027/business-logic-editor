package com.businesslogic.grpc;

import org.springframework.web.bind.annotation.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * gRPC 广播 REST 接口
 * 
 * 核心职责 * - 提供 HTTP 接口触发 gRPC 广播同步
 * - 提供 HTTP 接口查询当前目标实例数量
 * - gRPC 广播结果转换HTTP 响应格式
 * 
 * 接口说明 * - POST /grpc/broadcast: 触发缓存同步广播
 * - GET  /grpc/instances:  查询当前目标实例数量
 * 
 * 设计说明 * - 使用 @RestController 注册REST 控制 * - 使用 @RequestMapping("/grpc") 统一前缀
 * - 通过构造函数注GrpcBroadcastService
 * - 返回统一格式：{ "code": 200, "data": {...} }
 * 
 * 使用场景 * - 业务服务在更新表达式后调/grpc/broadcast 触发同步
 * - 运维人员通过 /grpc/instances 查看目标实例状 * - 可集成到管理后台或自动化脚本 */
@RestController
@RequestMapping("/grpc")
public class GrpcBroadcastController {

    private static final Logger logger = LoggerFactory.getLogger(GrpcBroadcastController.class);

    /** gRPC 广播协调服务 */
    private final GrpcBroadcastService broadcastService;

    /**
     * 构造函数（依赖注入     * 
     * @param broadcastService gRPC 广播协调服务
     */
    public GrpcBroadcastController(GrpcBroadcastService broadcastService) {
        this.broadcastService = broadcastService;
    }

    /**
     * 触发缓存同步广播
     * 
     * 请求参数     * - transactionCode (必填): 交易码，"TXN_ORDER"
     * - featureCode (必填):     功能码，"FEATURE_DISCOUNT"
     * - expression (可:      Aviator 表达式源码，删除时可不传
     * - version (必填):         版本号，-1L 表示删除缓存
     * 
     * 响应格式     * {
     *   "code": 200,
     *   "data": {
     *     "syncId": "uuid",
     *     "totalInstances": 3,
     *     "successInstances": [...],
     *     "failedInstances": [...],
     *     "timeoutInstances": [...],
     *     "overallStatus": "ALL_SUCCESS"
     *   }
     * }
     * 
     * 使用示例     * POST /grpc/broadcast?transactionCode=TXN_ORDER&featureCode=FEATURE_DISCOUNT&expression=a+b&version=1
     * 
     * @param transactionCode 交易     * @param featureCode     功能     * @param expression      表达式源码（可选）
     * @param version         版本     * @return 统一响应格式
     */
        @PostMapping("/broadcast")
    public Map<String, Object> broadcast(
            @RequestParam String transactionCode,
            @RequestParam String featureCode,
            @RequestParam(required = false) String expression,
            @RequestParam Long version) {

        GrpcBroadcastService.BroadcastResult result =
                broadcastService.broadcastSync(transactionCode, featureCode, expression, version);

        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("data", result);
        return response;
    }

    /**
     * 查询当前目标实例数量
     * 
     * 响应格式     * {
     *   "code": 200,
     *   "data": {
     *     "instanceCount": 3
     *   }
     * }
     * 
     * 使用示例     * GET /grpc/instances
     * 
     * @return 统一响应格式
     */
    @GetMapping("/instances")
    public Map<String, Object> instances() {
        int count = broadcastService.getInstanceCount();
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        Map<String, Object> data = new HashMap<>();
        data.put("instanceCount", count);
        response.put("data", data);
        return response;
    }
}