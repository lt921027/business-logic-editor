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
 * OpenFeign 广播 REST 接口（替GrpcBroadcastController *
 * 接口路径openfeign/broadcast, /openfeign/instances
 * gRPC 实现路径不同，便于灰度切 */
@RestController
@RequestMapping("/openfeign")
public class FeignBroadcastController {

    private static final Logger logger = LoggerFactory.getLogger(FeignBroadcastController.class);

    private final FeignBroadcastService broadcastService;

    public FeignBroadcastController(FeignBroadcastService broadcastService) {
        this.broadcastService = broadcastService;
    }

    /**
     * 触发单阶段广播同     */
    @PostMapping("/broadcast")
    public Map<String, Object> broadcast(
            @RequestParam String transactionCode,
            @RequestParam String featureCode,
            @RequestParam(required = false) String expression,
            @RequestParam Long version) {

        FeignBroadcastService.BroadcastResult result =
                broadcastService.broadcastSync(transactionCode, featureCode, expression, version);

        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("data", result);
        return response;
    }

    /**
     * 查询当前目标实例数量
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
