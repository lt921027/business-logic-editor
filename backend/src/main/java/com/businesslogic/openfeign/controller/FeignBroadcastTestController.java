package com.businesslogic.openfeign.controller;

import com.businesslogic.grpc.ConsulServiceWatcher;
import com.businesslogic.openfeign.service.FeignBroadcastService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenFeign 广播测试专用控制 *
 * 接口说明 * - GET  /openfeign/test/instances:         查询实例列表
 * - POST /openfeign/test/broadcast:         单阶段广 * - POST /openfeign/test/broadcast-simple:  简化版广播
 * - POST /openfeign/test/simulate-2pc:      2PC 完整流程模拟
 * - POST /openfeign/test/prepare:           Prepare 阶段
 * - POST /openfeign/test/commit:            Commit 阶段
 * - POST /openfeign/test/abort:             Abort 阶段
 */
@RestController
@RequestMapping("/openfeign/test")
public class FeignBroadcastTestController {

    private static final Logger logger = LoggerFactory.getLogger(FeignBroadcastTestController.class);

    private final FeignBroadcastService broadcastService;
    private final ConsulServiceWatcher serviceWatcher;

    public FeignBroadcastTestController(FeignBroadcastService broadcastService,
                                        ConsulServiceWatcher serviceWatcher) {
        this.broadcastService = broadcastService;
        this.serviceWatcher = serviceWatcher;
    }

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

    @PostMapping("/broadcast")
    public Map<String, Object> testBroadcast(
            @RequestParam String transactionCode,
            @RequestParam String featureCode,
            @RequestParam(required = false) String expression,
            @RequestParam Long version) {

        logger.info("开始测试广 txn={}, feature={}, version={}",
                transactionCode, featureCode, version);

        FeignBroadcastService.BroadcastResult result =
                broadcastService.broadcastSync(transactionCode, featureCode, expression, version);

        return buildResponse("broadcast completed", result);
    }

    @PostMapping("/broadcast-simple")
    public Map<String, Object> testBroadcastSimple(
            @RequestParam String transactionCode,
            @RequestParam String featureCode,
            @RequestParam(required = false) String expression,
            @RequestParam Long version) {

        FeignBroadcastService.BroadcastResult result =
                broadcastService.broadcastSync(transactionCode, featureCode, expression, version);

        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", result.getOverallStatus());
        return response;
    }

    /**
     * 模拟 2PC 完整流程
     *
     * 执行流程     * 1. Prepare 阶段广播：所Pod 编译并暂     * 2. 收集 Prepare 结果：若全成进入 Commit；若任一失败 Abort
     * 3. Commit 阶段广播：所Pod 原子切换主缓     * 4. 异常分支：任一 Commit 失败 记录日志（不自动回滚，主缓存已切无法回退     *
     * 响应字段     * - phase1: Prepare 阶段汇     * - phase2: Commit 阶段汇总（仅在 Prepare 全成功时有值）
     * - finalStatus: 最终状态（2PC_SUCCESS / 2PC_ABORTED     */
    @PostMapping("/simulate-2pc")
    public Map<String, Object> simulate2pc(
            @RequestParam String transactionCode,
            @RequestParam String featureCode,
            @RequestParam String expression,
            @RequestParam Long version) {

        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", "2PC completed");

        Map<String, Object> data = new HashMap<>();

        // ===== 阶段 1：Prepare =====
        logger.info("[2PC-Phase1] 开Prepare: txn={}, feature={}", transactionCode, featureCode);
        FeignBroadcastService.BroadcastResult prepareResult =
                broadcastService.broadcastPrepare(transactionCode, featureCode, expression, version);

        data.put("phase1", prepareResult);

        // 校验 Prepare 结果
        if (!"ALL_SUCCESS".equals(prepareResult.getOverallStatus())) {
            logger.warn("[2PC-Phase1] Prepare 未全部成 触发 Abort: failed={}",
                    prepareResult.getFailedCount());

            //触发 Abort 回滚（仅通知已成功的 Pod 清理
            String syncId = prepareResult.getSyncId();
            FeignBroadcastService.BroadcastResult abortResult =
                    broadcastService.broadcastAbort(syncId, transactionCode, featureCode,
                            "Prepare 阶段未全部成");

            data.put("abort", abortResult);
            data.put("finalStatus", "2PC_ABORTED");
            response.put("data", data);
            return response;
        }

        // ===== 阶段 2：Commit =====
        logger.info("[2PC-Phase2] Prepare 全部成功, 开Commit: syncId={}", prepareResult.getSyncId());
        FeignBroadcastService.BroadcastResult commitResult =
                broadcastService.broadcastCommit(prepareResult.getSyncId(), transactionCode, featureCode);

        data.put("phase2", commitResult);

        if (!"ALL_SUCCESS".equals(commitResult.getOverallStatus())) {
            logger.error("[2PC-Phase2] Commit 部分失败: failed={}", commitResult.getFailedCount());
            data.put("finalStatus", "2PC_PARTIAL_COMMITTED");
            // 注：主缓存已切换，无法回滚；需运维介入手动同步
        } else {
            logger.info("[2PC-Phase2] Commit 全部成功: syncId={}", prepareResult.getSyncId());
            data.put("finalStatus", "2PC_SUCCESS");
        }

        response.put("data", data);
        return response;
    }

    @PostMapping("/prepare")
    public Map<String, Object> testPrepare(
            @RequestParam String transactionCode,
            @RequestParam String featureCode,
            @RequestParam(required = false) String expression,
            @RequestParam Long version) {

        FeignBroadcastService.BroadcastResult result =
                broadcastService.broadcastPrepare(transactionCode, featureCode, expression, version);
        return buildResponse("prepare completed", result);
    }

    @PostMapping("/commit")
    public Map<String, Object> testCommit(
            @RequestParam String syncId,
            @RequestParam String transactionCode,
            @RequestParam String featureCode) {

        FeignBroadcastService.BroadcastResult result =
                broadcastService.broadcastCommit(syncId, transactionCode, featureCode);
        return buildResponse("commit completed", result);
    }

    @PostMapping("/abort")
    public Map<String, Object> testAbort(
            @RequestParam String syncId,
            @RequestParam String transactionCode,
            @RequestParam String featureCode,
            @RequestParam(required = false, defaultValue = "manual abort") String reason) {

        FeignBroadcastService.BroadcastResult result =
                broadcastService.broadcastAbort(syncId, transactionCode, featureCode, reason);
        return buildResponse("abort completed", result);
    }

    private Map<String, Object> buildResponse(String message, FeignBroadcastService.BroadcastResult result) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", message);

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
        return response;
    }
}
