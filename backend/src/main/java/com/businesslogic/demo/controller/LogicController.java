package com.businesslogic.demo.controller;

import com.businesslogic.demo.model.ArrayFilterRequest;
import com.businesslogic.demo.model.ArrayFilterResponse;
import com.businesslogic.demo.model.AviatorResponse;
import com.businesslogic.demo.model.BusinessLogicRequest;
import com.businesslogic.demo.model.JsonPathRequest;
import com.businesslogic.demo.model.JsonPathResponse;
import com.businesslogic.demo.service.AviatorExpressionService;
import com.businesslogic.demo.service.ArrayFilterService;
import com.businesslogic.demo.service.JsonPathService;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import com.alibaba.fastjson.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON 路径AviatorScript 表达式生成控制器
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class LogicController {
    
    @Autowired
    private JsonPathService jsonPathService;
    
    @Autowired
    private AviatorExpressionService aviatorExpressionService;
    
    @Autowired
    private ArrayFilterService arrayFilterService;
    
    /**
     * 解析 JSON 并生成字段树
     */
    @PostMapping("/json/parse")
    public ResponseEntity<JsonPathResponse> parseJson(@RequestBody JsonPathRequest request) {
        if (request.getJsonText() == null || request.getJsonText().trim().isEmpty()) {
            JsonPathResponse errorResponse = JsonPathResponse.builder()
                    .success(false)
                    .errorMessage("JSON 文本不能为空")
                    .build();
            return ResponseEntity.badRequest().body(errorResponse);
        }
        
        JsonPathResponse response = jsonPathService.parseJson(request.getJsonText());
        return ResponseEntity.ok(response);
    }
    
    /**
     * 根据业务逻辑生成 AviatorScript 表达     */
    @PostMapping("/aviator/generate")
    public ResponseEntity<AviatorResponse> generateAviatorExpression(
            @RequestBody BusinessLogicRequest request) {
        if (request.getSteps() == null || request.getSteps().isEmpty()) {
            AviatorResponse errorResponse = AviatorResponse.builder()
                    .success(false)
                    .errorMessage("业务逻辑步骤不能为空")
                    .build();
            return ResponseEntity.badRequest().body(errorResponse);
        }
        
        AviatorResponse response = aviatorExpressionService.generateExpression(request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 数组过滤
     */
    @PostMapping("/array/filter")
    public ResponseEntity<ArrayFilterResponse> filterArray(@RequestBody ArrayFilterRequest request) {
        ArrayFilterResponse response = arrayFilterService.filterArray(request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 根据 JSONPath 提取 JSON 数据中的     */
    @PostMapping("/json/extract")
    public ResponseEntity<Map<String, Object>> extractJsonValues(@RequestBody JsonPathExtractRequest request) {
        Map<String, Object> result = new HashMap<>();
        System.out.println("request:" + request.toString());
        System.out.println("-------------------------------");
        try {
            if (request.getJsonText() == null || request.getJsonText().trim().isEmpty()) {
                result.put("success", false);
                result.put("errorMessage", "JSON 文本不能为空");
                return ResponseEntity.badRequest().body(result);
            }
            
            if (request.getPaths() == null || request.getPaths().isEmpty()) {
                result.put("success", false);
                result.put("errorMessage", "JSONPath 列表不能为空");
                return ResponseEntity.badRequest().body(result);
            }
            
            // 解析 JSON
            ReadContext ctx = JsonPath.parse(request.getJsonText());
            //提取每个路径的
            List<Map<String, Object>> extractedValues = new ArrayList<>();
            for (String path : request.getPaths()) {
                Map<String, Object> valueInfo = new HashMap<>();
                valueInfo.put("path", path);
                System.out.println("path:" + path);
                System.out.println("-------------------------------");
                try {
                    Object value = ctx.read(path);
                    valueInfo.put("value", value);
                    valueInfo.put("success", true);
                    valueInfo.put("type", getTypeName(value));
                } catch (Exception e) {
                    valueInfo.put("value", null);
                    valueInfo.put("success", false);
                    valueInfo.put("errorMessage", "路径解析失败" + e.getMessage());
                }

                System.out.println("valueInfo:" + valueInfo.get("value"));
                System.out.println("-------------------------------");
                extractedValues.add(valueInfo);
            }
            
            result.put("success", true);
            result.put("extractedValues", extractedValues);
            result.put("totalPaths", request.getPaths().size());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("errorMessage", "解析失败" + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }
    
    /**
     * 获取值的类型名称
     */
    private String getTypeName(Object value) {
        if (value == null) return "null";
        if (value instanceof JSONArray) return "array";
        if (value instanceof List) return "array";
        if (value instanceof Map) return "object";
        if (value instanceof String) return "string";
        if (value instanceof Number) return "number";
        if (value instanceof Boolean) return "boolean";
        return "unknown";
    }
    
    /**
     * JSONPath 提取请求
     */
    public static class JsonPathExtractRequest {
        private String jsonText;
        private List<String> paths;

        public JsonPathExtractRequest() {
        }

        public JsonPathExtractRequest(String jsonText, List<String> paths) {
            this.jsonText = jsonText;
            this.paths = paths;
        }

        public String getJsonText() {
            return jsonText;
        }

        public void setJsonText(String jsonText) {
            this.jsonText = jsonText;
        }

        public List<String> getPaths() {
            return paths;
        }

        public void setPaths(List<String> paths) {
            this.paths = paths;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            JsonPathExtractRequest that = (JsonPathExtractRequest) o;

            if (jsonText != null ? !jsonText.equals(that.jsonText) : that.jsonText != null) return false;
            return paths != null ? paths.equals(that.paths) : that.paths == null;
        }

        @Override
        public int hashCode() {
            int result = jsonText != null ? jsonText.hashCode() : 0;
            result = 31 * result + (paths != null ? paths.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "JsonPathExtractRequest{" +
                    "jsonText='" + jsonText + '\'' +
                    ", paths=" + paths +
                    '}';
        }
    }
}
