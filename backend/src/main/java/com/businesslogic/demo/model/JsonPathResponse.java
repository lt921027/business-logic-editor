package com.businesslogic.demo.model;

import java.util.List;

/**
 * JSON 路径解析响应
 */
public class JsonPathResponse {
    
    /**
     * 是否成功
     */
    private boolean success;
    
    /**
     * 字段     */
    private List<FieldNode> fieldTree;
    
    /**
     * 所有字段路径列     */
    private List<String> allFields;
    
    /**
     * 总字段数
     */
    private int totalFields;
    
    /**
     * 最大深     */
    private int maxDepth;
    
    /**
     * 错误信息
     */
    private String errorMessage;

    public JsonPathResponse() {
    }

    public JsonPathResponse(boolean success, List<FieldNode> fieldTree, List<String> allFields, int totalFields, int maxDepth, String errorMessage) {
        this.success = success;
        this.fieldTree = fieldTree;
        this.allFields = allFields;
        this.totalFields = totalFields;
        this.maxDepth = maxDepth;
        this.errorMessage = errorMessage;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public List<FieldNode> getFieldTree() {
        return fieldTree;
    }

    public void setFieldTree(List<FieldNode> fieldTree) {
        this.fieldTree = fieldTree;
    }

    public List<String> getAllFields() {
        return allFields;
    }

    public void setAllFields(List<String> allFields) {
        this.allFields = allFields;
    }

    public int getTotalFields() {
        return totalFields;
    }

    public void setTotalFields(int totalFields) {
        this.totalFields = totalFields;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        JsonPathResponse that = (JsonPathResponse) o;

        if (success != that.success) return false;
        if (totalFields != that.totalFields) return false;
        if (maxDepth != that.maxDepth) return false;
        if (fieldTree != null ? !fieldTree.equals(that.fieldTree) : that.fieldTree != null) return false;
        if (allFields != null ? !allFields.equals(that.allFields) : that.allFields != null) return false;
        return errorMessage != null ? errorMessage.equals(that.errorMessage) : that.errorMessage == null;
    }

    @Override
    public int hashCode() {
        int result = (success ? 1 : 0);
        result = 31 * result + (fieldTree != null ? fieldTree.hashCode() : 0);
        result = 31 * result + (allFields != null ? allFields.hashCode() : 0);
        result = 31 * result + totalFields;
        result = 31 * result + maxDepth;
        result = 31 * result + (errorMessage != null ? errorMessage.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "JsonPathResponse{" +
                "success=" + success +
                ", fieldTree=" + fieldTree +
                ", allFields=" + allFields +
                ", totalFields=" + totalFields +
                ", maxDepth=" + maxDepth +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }

    public static JsonPathResponseBuilder builder() {
        return new JsonPathResponseBuilder();
    }

    public static class JsonPathResponseBuilder {
        private boolean success;
        private List<FieldNode> fieldTree;
        private List<String> allFields;
        private int totalFields;
        private int maxDepth;
        private String errorMessage;

        JsonPathResponseBuilder() {
        }

        public JsonPathResponseBuilder success(boolean success) {
            this.success = success;
            return this;
        }

        public JsonPathResponseBuilder fieldTree(List<FieldNode> fieldTree) {
            this.fieldTree = fieldTree;
            return this;
        }

        public JsonPathResponseBuilder allFields(List<String> allFields) {
            this.allFields = allFields;
            return this;
        }

        public JsonPathResponseBuilder totalFields(int totalFields) {
            this.totalFields = totalFields;
            return this;
        }

        public JsonPathResponseBuilder maxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
            return this;
        }

        public JsonPathResponseBuilder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public JsonPathResponse build() {
            return new JsonPathResponse(success, fieldTree, allFields, totalFields, maxDepth, errorMessage);
        }

        @Override
        public String toString() {
            return "JsonPathResponse.JsonPathResponseBuilder{" +
                    "success=" + success +
                    ", fieldTree=" + fieldTree +
                    ", allFields=" + allFields +
                    ", totalFields=" + totalFields +
                    ", maxDepth=" + maxDepth +
                    ", errorMessage='" + errorMessage + '\'' +
                    '}';
        }
    }
    
    /**
     * 字段节点
     */
    public static class FieldNode {
        private String key;
        private String label;
        private String path;
        private String type;
        private Object value;
        private List<FieldNode> children;

        public FieldNode() {
        }

        public FieldNode(String key, String label, String path, String type, Object value, List<FieldNode> children) {
            this.key = key;
            this.label = label;
            this.path = path;
            this.type = type;
            this.value = value;
            this.children = children;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Object getValue() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
        }

        public List<FieldNode> getChildren() {
            return children;
        }

        public void setChildren(List<FieldNode> children) {
            this.children = children;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            FieldNode fieldNode = (FieldNode) o;

            if (key != null ? !key.equals(fieldNode.key) : fieldNode.key != null) return false;
            if (label != null ? !label.equals(fieldNode.label) : fieldNode.label != null) return false;
            if (path != null ? !path.equals(fieldNode.path) : fieldNode.path != null) return false;
            if (type != null ? !type.equals(fieldNode.type) : fieldNode.type != null) return false;
            if (value != null ? !value.equals(fieldNode.value) : fieldNode.value != null) return false;
            return children != null ? children.equals(fieldNode.children) : fieldNode.children == null;
        }

        @Override
        public int hashCode() {
            int result = key != null ? key.hashCode() : 0;
            result = 31 * result + (label != null ? label.hashCode() : 0);
            result = 31 * result + (path != null ? path.hashCode() : 0);
            result = 31 * result + (type != null ? type.hashCode() : 0);
            result = 31 * result + (value != null ? value.hashCode() : 0);
            result = 31 * result + (children != null ? children.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "FieldNode{" +
                    "key='" + key + '\'' +
                    ", label='" + label + '\'' +
                    ", path='" + path + '\'' +
                    ", type='" + type + '\'' +
                    ", value=" + value +
                    ", children=" + children +
                    '}';
        }

        public static FieldNodeBuilder builder() {
            return new FieldNodeBuilder();
        }

        public static class FieldNodeBuilder {
            private String key;
            private String label;
            private String path;
            private String type;
            private Object value;
            private List<FieldNode> children;

            FieldNodeBuilder() {
            }

            public FieldNodeBuilder key(String key) {
                this.key = key;
                return this;
            }

            public FieldNodeBuilder label(String label) {
                this.label = label;
                return this;
            }

            public FieldNodeBuilder path(String path) {
                this.path = path;
                return this;
            }

            public FieldNodeBuilder type(String type) {
                this.type = type;
                return this;
            }

            public FieldNodeBuilder value(Object value) {
                this.value = value;
                return this;
            }

            public FieldNodeBuilder children(List<FieldNode> children) {
                this.children = children;
                return this;
            }

            public FieldNode build() {
                return new FieldNode(key, label, path, type, value, children);
            }

            @Override
            public String toString() {
                return "FieldNode.FieldNodeBuilder{" +
                        "key='" + key + '\'' +
                        ", label='" + label + '\'' +
                        ", path='" + path + '\'' +
                        ", type='" + type + '\'' +
                        ", value=" + value +
                        ", children=" + children +
                        '}';
            }
        }
    }
}
