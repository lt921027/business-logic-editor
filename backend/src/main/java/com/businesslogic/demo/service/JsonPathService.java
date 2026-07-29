package com.businesslogic.demo.service;

import com.businesslogic.demo.model.JsonPathResponse;
import com.businesslogic.demo.model.JsonPathResponse.FieldNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * JSON 路径解析服务
 */
@Service
public class JsonPathService {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 解析 JSON 并生成字段树
     */
    public JsonPathResponse parseJson(String jsonText) {
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonText);
            
            List<FieldNode> fieldTree = buildFieldTree(jsonNode, "", "");
            List<String> allFields = extractAllFields(fieldTree);
            
            return JsonPathResponse.builder()
                    .success(true)
                    .fieldTree(fieldTree)
                    .allFields(allFields)
                    .totalFields(allFields.size())
                    .maxDepth(calculateMaxDepth(fieldTree))
                    .build();
                    
        } catch (Exception e) {
            return JsonPathResponse.builder()
                    .success(false)
                    .errorMessage("JSON 解析失败" + e.getMessage())
                    .build();
        }
    }
    
    /**
     * 构建字段     */
    private List<FieldNode> buildFieldTree(JsonNode node, String keyPrefix, String pathPrefix) {
        List<FieldNode> tree = new ArrayList<>();
        
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = field.getKey();
                JsonNode value = field.getValue();
                String currentPath = pathPrefix.isEmpty() ? key : pathPrefix + "." + key;
                String currentKey = keyPrefix.isEmpty() ? key : keyPrefix + "." + key;
                
                FieldNode fieldNode = FieldNode.builder()
                        .key(currentKey)
                        .label(key)
                        .path(currentPath)
                        .type(getType(value))
                        .value(isLeaf(value) ? extractValue(value) : null)
                        .build();
                
                if (!isLeaf(value)) {
                    fieldNode.setChildren(buildFieldTree(value, currentKey, currentPath));
                }
                
                tree.add(fieldNode);
            }
        } else if (node.isArray() && node.size() > 0) {
            JsonNode firstItem = node.get(0);
            String currentPath = pathPrefix.isEmpty() ? "[0]" : pathPrefix + "[0]";
            String currentKey = keyPrefix.isEmpty() ? "[0]" : keyPrefix + "[0]";
            
            FieldNode arrayItemNode = FieldNode.builder()
                    .key(currentKey)
                    .label("[0]")
                    .path(currentPath)
                    .type("object")
                    .build();
            
            if (!isLeaf(firstItem)) {
                arrayItemNode.setChildren(buildFieldTree(firstItem, currentKey, currentPath));
            }
            
            tree.add(arrayItemNode);
        }
        
        return tree;
    }
    
    /**
     * 提取所有字段路     */
    private List<String> extractAllFields(List<FieldNode> nodes) {
        List<String> fields = new ArrayList<>();
        for (FieldNode node : nodes) {
            fields.add(node.getPath());
            if (node.getChildren() != null) {
                fields.addAll(extractAllFields(node.getChildren()));
            }
        }
        return fields;
    }
    
    /**
     * 计算最大深     */
    private int calculateMaxDepth(List<FieldNode> nodes) {
        int maxDepth = 0;
        for (FieldNode node : nodes) {
            int depth = calculateDepth(node);
            maxDepth = Math.max(maxDepth, depth);
        }
        return maxDepth;
    }
    
    private int calculateDepth(FieldNode node) {
        if (node.getChildren() == null || node.getChildren().isEmpty()) {
            return 1;
        }
        int maxChildDepth = 0;
        for (FieldNode child : node.getChildren()) {
            maxChildDepth = Math.max(maxChildDepth, calculateDepth(child));
        }
        return 1 + maxChildDepth;
    }
    
    /**
     * 获取类型
     */
    private String getType(JsonNode node) {
        if (node.isNull()) {
            return "null";
        } else if (node.isArray()) {
            return "array";
        } else if (node.isObject()) {
            return "object";
        } else if (node.isBoolean()) {
            return "boolean";
        } else if (node.isNumber()) {
            return "number";
        } else if (node.isTextual()) {
            return "string";
        }
        return "unknown";
    }
    
    /**
     * 判断是否为叶子节     */
    private boolean isLeaf(JsonNode node) {
        return !node.isObject() && !(node.isArray() && node.size() > 0);
    }
    
    /**
     * 提取     */
    private Object extractValue(JsonNode node) {
        if (node.isBoolean()) {
            return node.asBoolean();
        } else if (node.isNumber()) {
            if (node.isInt() || node.isLong()) {
                return node.asLong();
            }
            return node.asDouble();
        } else if (node.isTextual()) {
            return node.asText();
        } else if (node.isNull()) {
            return null;
        }
        return node.asText();
    }
}
