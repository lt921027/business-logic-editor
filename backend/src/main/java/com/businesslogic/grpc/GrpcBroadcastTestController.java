package com.businesslogic.grpc;

import org.springframework.web.bind.annotation.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * gRPC 广播测试专用控制 * 
 * 核心职责 * - 提供丰富的测试接口验gRPC 广播功能
 * - 支持单阶段广播测 * - 支持两阶段提交（2PC）广播测试模 * - 提供实例列表查询功能
 * - 提供表达式编译测试功 * 
 * 测试接口说明 * - GET  /grpc/test/instances:         查询当前目标实例列表（详细信息）
 * - POST /grpc/test/broadcast:         单阶段广播测 * - POST /grpc/test/broadcast-simple:  简化版单阶段广播（返回成功/失败 * - POST /grpc/test/simulate-2pc:      模拟两阶段提交流 * - POST /grpc/test/expression-test:   测试表达式编译（本地验证 * 
 * 设计说明 * - 与生产接口分离，使用 /grpc/test 前缀
 * - 使用现有服务，不修改任何现有逻辑
 * - 返回统一格式：{ "code": 状态码, "message": "描述", "data": {...} }
 */
@RestController
@RequestMapping("/grpc/test")
public class GrpcBroadcastTestController {

    private static final Logger logger = LoggerFactory.getLogger(GrpcBroadcastTestController.class);

    /** gRPC 广播协调服务 */
    private final GrpcBroadcastService broadcastService;

    /** Consul 服务实例监听*/
    private final ConsulServiceWatcher serviceWatcher;

    /**
     * 构造函数（依赖注入     */
    public GrpcBroadcastTestController(GrpcBroadcastService broadcastService, 
                                       ConsulServiceWatcher serviceWatcher) {
        this.broadcastService = broadcastService;
        this.serviceWatcher = serviceWatcher;
    }

    /**
     * 查询当前目标实例列表（详细信息）
     * 
     * 响应格式     * {
     *   "code": 200,
     *   "message": "success",
     *   "data": {
     *     "count": 3,
     *     "instances": [
     *       {"instanceId": "...", "host": "127.0.0.1", "port": 9090, "healthy": true},
     *       ...
     *     ]
     *   }
     * }
     */
    @GetMapping("/instances")
    public Map<String, Object> getInstances() {
        List<ConsulServiceWatcher.ServiceInstance> instances = serviceWatcher.getInstances();
        
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", "success");
        
        Map<String, Object> data = new HashMap<>();
        data.put("count", instances.size());
        data.put("instances", instances);
        response.put("data", data);
        
        logger.info("查询实例列表: count={}", instances.size());
        return response;
    }

    /**
     * 单阶段广播测试（详细结果     * 
     * 请求参数     * - transactionCode: 交易码（必填     * - featureCode: 功能码（必填     * - expression: Aviator表达式（可选，删除时不传）
     * - version: 版本号（必填1表示删除     * 
     * 响应格式     * {
     *   "code": 200,
     *   "message": "broadcast completed",
     *   "data": {
     *     "syncId": "uuid",
     *     "totalInstances": 3,
     *     "successCount": 3,
     *     "failedCount": 0,
     *     "timeoutCount": 0,
     *     "overallStatus": "ALL_SUCCESS",
     *     "successInstances": [...],
     *     "failedInstances": [...],
     *     "timeoutInstances": [...]
     *   }
     * }
     */
    @PostMapping("/broadcast")
    public Map<String, Object> testBroadcast(
            @RequestParam String transactionCode,
            @RequestParam String featureCode,
            @RequestParam(required = false) String expression,
            @RequestParam Long version) {
        
        logger.info("开始测试广 txn={}, feature={}, version={}", 
                transactionCode, featureCode, version);
        
        GrpcBroadcastService.BroadcastResult result =
                broadcastService.broadcastSync(transactionCode, featureCode, expression, version);
        
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", "broadcast completed");
        
        Map<String, Object> data = new HashMap<>();
        data.put("syncId", result.getSyncId());
        data.put("totalInstances", result.getTotalInstances());
        data.put("successCount", result.getSuccessCount());
        data.put("failedCount", result.getFailedCount());
        data.put("timeoutCount", result.getTimeoutCount());
        data.put("overallStatus", result.getOverallStatus());
        data.put("successInstances", result.getSuccessInstances());
        data.put("failedInstances", result.getFailedInstances());
        data.put("timeoutInstances", result.getTimeoutInstances());
        response.put("data", data);
        
        logger.info("广播测试完成: syncId={}, status={}, success={}/{}", 
                result.getSyncId(), result.getOverallStatus(), 
                result.getSuccessCount(), result.getTotalInstances());
        
        return response;
    }

    /**
     * 简化版单阶段广播（仅返回成失败     * 
     * 请求参数     * - transactionCode: 交易码（必填     * - featureCode: 功能码（必填     * - expression: Aviator表达式（可选）
     * - version: 版本号（必填     * 
     * 响应格式     * {
     *   "code": 200,
     *   "message": "ALL_SUCCESS" "PARTIAL_SUCCESS" "ALL_FAILED"
     * }
     */
    @PostMapping("/broadcast-simple")
    public Map<String, Object> testBroadcastSimple(
            @RequestParam String transactionCode,
            @RequestParam String featureCode,
            @RequestParam(required = false) String expression,
            @RequestParam Long version) {
        
        GrpcBroadcastService.BroadcastResult result =
                broadcastService.broadcastSync(transactionCode, featureCode, expression, version);
        
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", result.getOverallStatus());
        
        return response;
    }

    /**
     * 模拟两阶段提交流程（2PC     * 
     * 执行流程     * 1. 第一阶段：广播准备请求（所有实例编译表达式     * 2. 检查是否全部准备成     * 3. 如果全部成功，执行第二阶段：广播提交请求
     * 4. 如果有失败，执行回滚
     * 
     * 请求参数     * - transactionCode: 交易码（必填     * - featureCode: 功能码（必填     * - expression: Aviator表达式（必填     * - version: 版本号（必填     * 
     * 响应格式     * {
     *   "code": 200,
     *   "message": "2PC completed",
     *   "data": {
     *     "syncId": "uuid",
     *     "phase1": {
     *       "status": "ALL_SUCCESS",
     *       "successCount": 3,
     *       "failedCount": 0
     *     },
     *     "phase2": {
     *       "status": "ALL_SUCCESS",
     *       "successCount": 3,
     *       "failedCount": 0
     *     }
     *   }
     * }
     */
    @PostMapping("/simulate-2pc")
    public Map<String, Object> simulateTwoPhaseCommit(
            @RequestParam String transactionCode,
            @RequestParam String featureCode,
            @RequestParam String expression,
            @RequestParam Long version) {
        
        logger.info("开始模拟两阶段提交: txn={}, feature={}, version={}", 
                transactionCode, featureCode, version);
        
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        
        // 阶段一：准备阶段（使用单阶段广播模拟）
        logger.info("=== 阶段一：Prepare ===");
        GrpcBroadcastService.BroadcastResult prepareResult =
                broadcastService.broadcastSync(transactionCode + "_PREPARE", featureCode, expression, version);
        
        Map<String, Object> phase1 = new HashMap<>();
        phase1.put("status", prepareResult.getOverallStatus());
        phase1.put("successCount", prepareResult.getSuccessCount());
        phase1.put("failedCount", prepareResult.getFailedCount());
        phase1.put("syncId", prepareResult.getSyncId());
        data.put("phase1", phase1);
        
        boolean allPrepared = "ALL_SUCCESS".equals(prepareResult.getOverallStatus());
        
        if (!allPrepared) {
            //准备阶段失败，执行回
            logger.warn("=== Prepare 阶段失败，执行回===");
            Map<String, Object> rollback = new HashMap<>();
            rollback.put("status", "ABORTED");
            rollback.put("reason", "Prepare failed");
            data.put("phase2", rollback);
            
            response.put("code", 200);
            response.put("message", "2PC aborted - prepare failed");
            response.put("data", data);
            return response;
        }
        
        // 阶段二：提交阶段
        logger.info("=== 阶段二：Commit ===");
        GrpcBroadcastService.BroadcastResult commitResult =
                broadcastService.broadcastSync(transactionCode + "_COMMIT", featureCode, expression, version);
        
        Map<String, Object> phase2 = new HashMap<>();
        phase2.put("status", commitResult.getOverallStatus());
        phase2.put("successCount", commitResult.getSuccessCount());
        phase2.put("failedCount", commitResult.getFailedCount());
        phase2.put("syncId", commitResult.getSyncId());
        data.put("phase2", phase2);
        
        response.put("code", 200);
        response.put("message", "2PC completed");
        response.put("data", data);
        
        logger.info("两阶段提交完 phase1={}, phase2={}", 
                phase1.get("status"), phase2.get("status"));
        
        return response;
    }

    /**
     * 测试表达式编译（本地验证     * 
     * 请求参数     * - expression: Aviator表达式（必填     * 
     * 响应格式     * {
     *   "code": 200,
     *   "message": "success",
     *   "data": {
     *     "expression": "a + b",
     *     "valid": true,
     *     "compilationTimeMs": 15
     *   }
     * }
     */
    @PostMapping("/expression-test")
    public Map<String, Object> testExpression(@RequestParam String expression) {
        logger.info("测试表达式编 {}", expression);
        
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        
        data.put("expression", expression);
        
        long start = System.currentTimeMillis();
        boolean valid = true;
        String errorMessage = null;
        
        try {
            // 尝试编译表达式（使用 Aviator
            com.googlecode.aviator.AviatorEvaluator.compile(expression);
        } catch (Exception e) {
            valid = false;
            errorMessage = e.getMessage();
            logger.warn("表达式编译失 {}", e.getMessage());
        }
        
        data.put("valid", valid);
        data.put("compilationTimeMs", System.currentTimeMillis() - start);
        data.put("errorMessage", errorMessage);
        
        response.put("code", valid ? 200 : 400);
        response.put("message", valid ? "success" : "compilation failed");
        response.put("data", data);
        
        return response;
    }

    /**
     * 批量测试多个表达     * 
     * 请求参数（JSON）：
     * [
     *   {"transactionCode": "T001", "featureCode": "F001", "expression": "a > 1", "version": 1},
     *   {"transactionCode": "T002", "featureCode": "F002", "expression": "b < 100", "version": 2}
     * ]
     * 
     * 响应格式     * {
     *   "code": 200,
     *   "message": "batch test completed",
     *   "data": [
     *     {"transactionCode": "T001", "featureCode": "F001", "status": "ALL_SUCCESS"},
     *     {"transactionCode": "T002", "featureCode": "F002", "status": "ALL_SUCCESS"}
     *   ]
     * }
     */
    @PostMapping("/batch")
    public Map<String, Object> batchTest(@RequestBody List<Map<String, Object>> requests) {
        logger.info("开始批量测 count={}", requests.size());
        
        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> results = new java.util.ArrayList<>();
        
        for (Map<String, Object> req : requests) {
            String txn = (String) req.get("transactionCode");
            String feature = (String) req.get("featureCode");
            String expr = (String) req.get("expression");
            Long version = req.get("version") instanceof Number 
                    ? ((Number) req.get("version")).longValue() 
                    : 1L;
            
            GrpcBroadcastService.BroadcastResult result =
                    broadcastService.broadcastSync(txn, feature, expr, version);
            
            Map<String, Object> resultItem = new HashMap<>();
            resultItem.put("transactionCode", txn);
            resultItem.put("featureCode", feature);
            resultItem.put("status", result.getOverallStatus());
            resultItem.put("successCount", result.getSuccessCount());
            resultItem.put("failedCount", result.getFailedCount());
            results.add(resultItem);
        }
        
        response.put("code", 200);
        response.put("message", "batch test completed");
        response.put("data", results);
        
        return response;
    }

    /**
     * 健康检查接     * 
     * 响应格式     * {
     *   "code": 200,
     *   "message": "OK",
     *   "data": {
     *     "service": "grpc-broadcast-test",
     *     "instanceCount": 3,
     *     "timestamp": 1234567890
     *   }
     * }
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        
        data.put("service", "grpc-broadcast-test");
        data.put("instanceCount", serviceWatcher.getInstanceCount());
        data.put("timestamp", System.currentTimeMillis());
        
        response.put("code", 200);
        response.put("message", "OK");
        response.put("data", data);
        
        return response;
    }
}