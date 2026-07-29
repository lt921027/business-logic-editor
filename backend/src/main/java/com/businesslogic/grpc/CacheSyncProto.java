package com.businesslogic.grpc;

/**
 * gRPC 消息定义类（替代 Protobuf 自动生成 * 
 * 由于项目路径包含中文字符，protoc 编译器无法正常工作，
 * 因此手动实现等效POJO 消息类 * 
 * 包含以下消息类型 * 原有单阶段同步：
 * - CacheSyncRequest:  缓存同步请求（发起方 目标 Pod * - CacheSyncResponse: 缓存同步响应（目Pod 发起方）
 * 
 * 两阶段提交（2PC）：
 * - PrepareRequest:    Prepare 阶段请求
 * - PrepareResponse:   Prepare 阶段响应
 * - CommitRequest:     Commit 阶段请求
 * - CommitResponse:    Commit 阶段响应
 * - AbortRequest:      Abort 阶段请求
 * - AbortResponse:     Abort 阶段响应
 * 
 * 状态常量：
 * - SyncStatus: 同步状态常 * 
 * 设计说明 * - 使用 Builder 模式构建不可变消息对 * - 字段类型Protobuf 定义一一对应
 * - 通过 JsonMarshaller 序列化为 JSON gRPC 链路中传 */
public final class CacheSyncProto {

    // ======================== 原有单阶段同步消========================

    /**
     * 缓存同步请求消息
     * 
     * 由发起方（Service A）构建并发送给每个目标 Pod（Service B     * 
     * 字段说明     * - syncId:         本次广播的唯一标识，用于关联所Pod 的响     * - transactionCode: 交易码，标识业务交易类型
     * - featureCode:    功能码，标识具体功能     * - version:        表达式版本号1L 表示删除缓存
     * - expression:     Aviator 表达式源码字符串
     * - timestamp:      请求发起时间戳（毫秒），用于计算耗时
     */
    public static final class CacheSyncRequest {
        /** 本次广播的唯一标识 UUID */
        private String syncId;
        /** 交易码，"TXN_ORDER" */
        private String transactionCode;
        /** 功能码，"FEATURE_DISCOUNT" */
        private String featureCode;
        /** 表达式版本号1L 表示删除操作 */
        private long version;
        /** Aviator 表达式源*/
        private String expression;
        /** 请求发起时间戳（毫秒*/
        private long timestamp;

        public CacheSyncRequest() {}

        public String getSyncId() { return syncId; }
        public void setSyncId(String syncId) { this.syncId = syncId; }
        public String getTransactionCode() { return transactionCode; }
        public void setTransactionCode(String transactionCode) { this.transactionCode = transactionCode; }
        public String getFeatureCode() { return featureCode; }
        public void setFeatureCode(String featureCode) { this.featureCode = featureCode; }
        public long getVersion() { return version; }
        public void setVersion(long version) { this.version = version; }
        public String getExpression() { return expression; }
        public void setExpression(String expression) { this.expression = expression; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

        /** 获取 Builder 实例 */
        public static Builder newBuilder() { return new Builder(); }

        /**
         * Builder 模式构建请求
         * 链式调用，最终通过 build() 返回不可变对         */
        public static final class Builder {
            private final CacheSyncRequest request = new CacheSyncRequest();
            public Builder setSyncId(String v) { request.syncId = v; return this; }
            public Builder setTransactionCode(String v) { request.transactionCode = v; return this; }
            public Builder setFeatureCode(String v) { request.featureCode = v; return this; }
            public Builder setVersion(long v) { request.version = v; return this; }
            public Builder setExpression(String v) { request.expression = v; return this; }
            public Builder setTimestamp(long v) { request.timestamp = v; return this; }
            public CacheSyncRequest build() { return request; }
        }
    }

    /**
     * 缓存同步响应消息
     * 
     * 由目Pod（Service B）构建并返回给发起方（Service A     * 
     * 字段说明     * - syncId:   回显请求中的 syncId，用于发起方关联响应
     * - podId:    处理此请求的 Pod 标识 UUID 前缀     * - status:   同步结果状态（SUCCESS / FAILED / SKIPPED     * - message:  附加信息，失败时包含错误详情
     * - costMs:   缓存操作耗时（毫秒）
     */
    public static final class CacheSyncResponse {
        /** 回显请求中的 syncId */
        private String syncId;
        /** 处理此请求的 Pod 标识 */
        private String podId;
        /** 同步结果状*/
        private String status;
        /** 附加信息（失败时包含错误详情*/
        private String message;
        /** 缓存操作耗时（毫秒） */
        private long costMs;

        public CacheSyncResponse() {}

        public String getSyncId() { return syncId; }
        public void setSyncId(String syncId) { this.syncId = syncId; }
        public String getPodId() { return podId; }
        public void setPodId(String podId) { this.podId = podId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public long getCostMs() { return costMs; }
        public void setCostMs(long costMs) { this.costMs = costMs; }

        /** 获取 Builder 实例 */
        public static Builder newBuilder() { return new Builder(); }

        /**
         * Builder 模式构建响应
         */
        public static final class Builder {
            private final CacheSyncResponse response = new CacheSyncResponse();
            public Builder setSyncId(String v) { response.syncId = v; return this; }
            public Builder setPodId(String v) { response.podId = v; return this; }
            public Builder setStatus(String v) { response.status = v; return this; }
            public Builder setMessage(String v) { response.message = v; return this; }
            public Builder setCostMs(long v) { response.costMs = v; return this; }
            public CacheSyncResponse build() { return response; }
        }
    }

    // ======================== 两阶段提交消========================

    /**
     * Prepare 阶段请求消息
     * 
     * 由发起方（Service A）构建并发送给每个目标 Pod（Service B     * 请求 Pod 编译表达式并存入待激活区（stagingCache），不修改主缓存
     * 
     * 字段说明     * - syncId:         本次广播的唯一标识，关Prepare Commit
     * - transactionCode: 交易码，标识业务交易类型
     * - featureCode:    功能码，标识具体功能     * - version:        表达式版本号1L 表示删除缓存
     * - expression:     Aviator 表达式源码字符串
     * - timestamp:      请求发起时间戳（毫秒），用于计算耗时
     */
    public static final class PrepareRequest {
        /** 本次广播的唯一标识 UUID */
        private String syncId;
        /** 交易码，"TXN_ORDER" */
        private String transactionCode;
        /** 功能码，"FEATURE_DISCOUNT" */
        private String featureCode;
        /** 表达式版本号1L 表示删除操作 */
        private long version;
        /** Aviator 表达式源*/
        private String expression;
        /** 请求发起时间戳（毫秒*/
        private long timestamp;

        public PrepareRequest() {}

        public String getSyncId() { return syncId; }
        public void setSyncId(String syncId) { this.syncId = syncId; }
        public String getTransactionCode() { return transactionCode; }
        public void setTransactionCode(String transactionCode) { this.transactionCode = transactionCode; }
        public String getFeatureCode() { return featureCode; }
        public void setFeatureCode(String featureCode) { this.featureCode = featureCode; }
        public long getVersion() { return version; }
        public void setVersion(long version) { this.version = version; }
        public String getExpression() { return expression; }
        public void setExpression(String expression) { this.expression = expression; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

        public static Builder newBuilder() { return new Builder(); }

        public static final class Builder {
            private final PrepareRequest request = new PrepareRequest();
            public Builder setSyncId(String v) { request.syncId = v; return this; }
            public Builder setTransactionCode(String v) { request.transactionCode = v; return this; }
            public Builder setFeatureCode(String v) { request.featureCode = v; return this; }
            public Builder setVersion(long v) { request.version = v; return this; }
            public Builder setExpression(String v) { request.expression = v; return this; }
            public Builder setTimestamp(long v) { request.timestamp = v; return this; }
            public PrepareRequest build() { return request; }
        }
    }

    /**
     * Prepare 阶段响应消息
     * 
     * 由目Pod（Service B）构建并返回给发起方（Service A     * 告知发起方该 Pod Prepare 是否成功
     * 
     * 字段说明     * - syncId:       回显请求中的 syncId
     * - podId:        处理此请求的 Pod 标识
     * - status:       Prepare 结果（PREPARE_OK / PREPARE_FAILED / ALREADY_EXISTS     * - message:      附加信息，失败时包含编译错误详情
     * - costMs:       编译耗时（毫秒）
     */
    public static final class PrepareResponse {
        /** 回显请求中的 syncId */
        private String syncId;
        /** 处理此请求的 Pod 标识 */
        private String podId;
        /** Prepare 结果状*/
        private String status;
        /** 附加信息（失败时包含编译错误详情*/
        private String message;
        /** 编译耗时（毫秒） */
        private long costMs;

        public PrepareResponse() {}

        public String getSyncId() { return syncId; }
        public void setSyncId(String syncId) { this.syncId = syncId; }
        public String getPodId() { return podId; }
        public void setPodId(String podId) { this.podId = podId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public long getCostMs() { return costMs; }
        public void setCostMs(long costMs) { this.costMs = costMs; }

        public static Builder newBuilder() { return new Builder(); }

        public static final class Builder {
            private final PrepareResponse response = new PrepareResponse();
            public Builder setSyncId(String v) { response.syncId = v; return this; }
            public Builder setPodId(String v) { response.podId = v; return this; }
            public Builder setStatus(String v) { response.status = v; return this; }
            public Builder setMessage(String v) { response.message = v; return this; }
            public Builder setCostMs(long v) { response.costMs = v; return this; }
            public PrepareResponse build() { return response; }
        }
    }

    /**
     * Commit 阶段请求消息
     * 
     * 由发起方在全Pod 返回 PREPARE_OK 后构建并发     * 请求 Pod 将待激活区中的预编译对象原子替换到主缓     * 
     * 字段说明     * - syncId:         广播标识（与 Prepare 时一致）
     * - transactionCode: 交易     * - featureCode:    功能     * - timestamp:      请求发起时间     */
    public static final class CommitRequest {
        /** 广播标识（与 Prepare 时一致） */
        private String syncId;
        /** 交易*/
        private String transactionCode;
        /** 功能*/
        private String featureCode;
        /** 请求发起时间戳（毫秒*/
        private long timestamp;

        public CommitRequest() {}

        public String getSyncId() { return syncId; }
        public void setSyncId(String syncId) { this.syncId = syncId; }
        public String getTransactionCode() { return transactionCode; }
        public void setTransactionCode(String transactionCode) { this.transactionCode = transactionCode; }
        public String getFeatureCode() { return featureCode; }
        public void setFeatureCode(String featureCode) { this.featureCode = featureCode; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

        public static Builder newBuilder() { return new Builder(); }

        public static final class Builder {
            private final CommitRequest request = new CommitRequest();
            public Builder setSyncId(String v) { request.syncId = v; return this; }
            public Builder setTransactionCode(String v) { request.transactionCode = v; return this; }
            public Builder setFeatureCode(String v) { request.featureCode = v; return this; }
            public Builder setTimestamp(long v) { request.timestamp = v; return this; }
            public CommitRequest build() { return request; }
        }
    }

    /**
     * Commit 阶段响应消息
     * 
     * 由目Pod 构建并返回给发起     * 告知发起方该 Pod Commit 是否成功
     * 
     * 字段说明     * - syncId:   回显请求中的 syncId
     * - podId:    处理此请求的 Pod 标识
     * - status:   Commit 结果（COMMIT_OK / COMMIT_NOT_FOUND     * - message:  附加信息
     * - costMs:   提交耗时（毫秒）
     */
    public static final class CommitResponse {
        /** 回显请求中的 syncId */
        private String syncId;
        /** 处理此请求的 Pod 标识 */
        private String podId;
        /** Commit 结果状*/
        private String status;
        /** 附加信息 */
        private String message;
        /** 提交耗时（毫秒） */
        private long costMs;

        public CommitResponse() {}

        public String getSyncId() { return syncId; }
        public void setSyncId(String syncId) { this.syncId = syncId; }
        public String getPodId() { return podId; }
        public void setPodId(String podId) { this.podId = podId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public long getCostMs() { return costMs; }
        public void setCostMs(long costMs) { this.costMs = costMs; }

        public static Builder newBuilder() { return new Builder(); }

        public static final class Builder {
            private final CommitResponse response = new CommitResponse();
            public Builder setSyncId(String v) { response.syncId = v; return this; }
            public Builder setPodId(String v) { response.podId = v; return this; }
            public Builder setStatus(String v) { response.status = v; return this; }
            public Builder setMessage(String v) { response.message = v; return this; }
            public Builder setCostMs(long v) { response.costMs = v; return this; }
            public CommitResponse build() { return response; }
        }
    }

    /**
     * Abort 阶段请求消息
     * 
     * 由发起方Prepare 阶段失败时构建并发     * 请求 Pod 清理待激活区中对syncId 的条     * 
     * 字段说明     * - syncId:         广播标识（与 Prepare 时一致）
     * - transactionCode: 交易     * - featureCode:    功能     * - timestamp:      请求发起时间     */
    public static final class AbortRequest {
        /** 广播标识（与 Prepare 时一致） */
        private String syncId;
        /** 交易*/
        private String transactionCode;
        /** 功能*/
        private String featureCode;
        /** 请求发起时间戳（毫秒*/
        private long timestamp;

        public AbortRequest() {}

        public String getSyncId() { return syncId; }
        public void setSyncId(String syncId) { this.syncId = syncId; }
        public String getTransactionCode() { return transactionCode; }
        public void setTransactionCode(String transactionCode) { this.transactionCode = transactionCode; }
        public String getFeatureCode() { return featureCode; }
        public void setFeatureCode(String featureCode) { this.featureCode = featureCode; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

        public static Builder newBuilder() { return new Builder(); }

        public static final class Builder {
            private final AbortRequest request = new AbortRequest();
            public Builder setSyncId(String v) { request.syncId = v; return this; }
            public Builder setTransactionCode(String v) { request.transactionCode = v; return this; }
            public Builder setFeatureCode(String v) { request.featureCode = v; return this; }
            public Builder setTimestamp(long v) { request.timestamp = v; return this; }
            public AbortRequest build() { return request; }
        }
    }

    /**
     * Abort 阶段响应消息
     * 
     * 由目Pod 构建并返回给发起     * 告知发起方该 Pod Abort 是否成功
     * 
     * 字段说明     * - syncId:   回显请求中的 syncId
     * - podId:    处理此请求的 Pod 标识
     * - status:   Abort 结果
     * - message:  附加信息
     * - costMs:   清理耗时（毫秒）
     */
    public static final class AbortResponse {
        /** 回显请求中的 syncId */
        private String syncId;
        /** 处理此请求的 Pod 标识 */
        private String podId;
        /** Abort 结果状*/
        private String status;
        /** 附加信息 */
        private String message;
        /** 清理耗时（毫秒） */
        private long costMs;

        public AbortResponse() {}

        public String getSyncId() { return syncId; }
        public void setSyncId(String syncId) { this.syncId = syncId; }
        public String getPodId() { return podId; }
        public void setPodId(String podId) { this.podId = podId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public long getCostMs() { return costMs; }
        public void setCostMs(long costMs) { this.costMs = costMs; }

        public static Builder newBuilder() { return new Builder(); }

        public static final class Builder {
            private final AbortResponse response = new AbortResponse();
            public Builder setSyncId(String v) { response.syncId = v; return this; }
            public Builder setPodId(String v) { response.podId = v; return this; }
            public Builder setStatus(String v) { response.status = v; return this; }
            public Builder setMessage(String v) { response.message = v; return this; }
            public Builder setCostMs(long v) { response.costMs = v; return this; }
            public AbortResponse build() { return response; }
        }
    }

    // ======================== 状态常========================

    /**
     * 同步状态常     * 
     * 原有单阶段同步：
     * - SUCCESS: 缓存操作成功（含 Pod 下线视为成功     * - FAILED:  缓存操作失败（编译错误、运行时异常等）
     * - SKIPPED: 跳过操作（版本号未变化，无需更新     * 
     * 两阶段提交：
     * - PREPARE_OK:        Prepare 成功（编译完成，已存入待激活区     * - PREPARE_FAILED:    Prepare 失败（编译失败）
     * - ALREADY_EXISTS:    Prepare 幂等（同一 syncId 已在待激活区     * - COMMIT_OK:         Commit 成功（已原子替换主缓存）
     * - COMMIT_NOT_FOUND:  Commit 失败（待激活区无对应数据）
     */
    public static final class SyncStatus {
        public static final String SUCCESS = "SUCCESS";
        public static final String FAILED = "FAILED";
        public static final String SKIPPED = "SKIPPED";
        public static final String PREPARE_OK = "PREPARE_OK";
        public static final String PREPARE_FAILED = "PREPARE_FAILED";
        public static final String ALREADY_EXISTS = "ALREADY_EXISTS";
        public static final String COMMIT_OK = "COMMIT_OK";
        public static final String COMMIT_NOT_FOUND = "COMMIT_NOT_FOUND";
        private SyncStatus() {}
    }
}