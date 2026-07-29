package com.businesslogic.grpc;

import io.grpc.*;
import io.grpc.stub.*;

/**
 * gRPC 服务 Stub 定义类（替代 Protobuf 自动生成 * 
 * 定义CacheSyncService 的完gRPC 通信接口，包含：
 * - 服务名称和方法描述符
 * - 服务端基类（CacheSyncServiceImplBase * - 异步客户Stub（CacheSyncServiceStub * - 阻塞客户Stub（CacheSyncServiceBlockingStub * 
 * RPC 方法列表 * 原有 * - SyncCache: 单阶段缓存同步（旧接口，向后兼容 * 
 * 两阶段提交（2PC）：
 * - PrepareCache: Prepare 阶段（编译表达式并存入待激活区 * - CommitCache: Commit 阶段（原子替换主缓存 * - AbortCache:  Abort 阶段（清理待激活区 * 
 * 设计说明 * - 使用 JSON 作为传输格式（通过 JsonMarshaller 序列反序列化 * - UNARY 模式：单次请响应，无流式传输
 * - 手动实现等效protoc 生成的代码结 */
public final class CacheSyncServiceGrpc {

    private CacheSyncServiceGrpc() {}

    /** gRPC 服务全限定名，格式为 "包名.服务名" */
    public static final String SERVICE_NAME = "cachesync.CacheSyncService";

    // ======================== 方法描述符定========================

    /**
     * 方法描述符：SyncCache（原有单阶段同步     * 
     * 全限定方法名: "cachesync.CacheSyncService/SyncCache"
     */
    private static final MethodDescriptor<CacheSyncProto.CacheSyncRequest, CacheSyncProto.CacheSyncResponse> SYNC_CACHE_METHOD =
            MethodDescriptor.<CacheSyncProto.CacheSyncRequest, CacheSyncProto.CacheSyncResponse>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, "SyncCache"))
                    .setRequestMarshaller(new JsonMarshaller<>(CacheSyncProto.CacheSyncRequest.class))
                    .setResponseMarshaller(new JsonMarshaller<>(CacheSyncProto.CacheSyncResponse.class))
                    .build();

    /**
     * 方法描述符：PrepareCache（Prepare 阶段     * 
     * 全限定方法名: "cachesync.CacheSyncService/PrepareCache"
     * 请求：PrepareRequest（syncId + 表达式源码）
     * 响应：PrepareResponse（PREPARE_OK / PREPARE_FAILED     */
    private static final MethodDescriptor<CacheSyncProto.PrepareRequest, CacheSyncProto.PrepareResponse> PREPARE_CACHE_METHOD =
            MethodDescriptor.<CacheSyncProto.PrepareRequest, CacheSyncProto.PrepareResponse>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, "PrepareCache"))
                    .setRequestMarshaller(new JsonMarshaller<>(CacheSyncProto.PrepareRequest.class))
                    .setResponseMarshaller(new JsonMarshaller<>(CacheSyncProto.PrepareResponse.class))
                    .build();

    /**
     * 方法描述符：CommitCache（Commit 阶段     * 
     * 全限定方法名: "cachesync.CacheSyncService/CommitCache"
     * 请求：CommitRequest（syncId     * 响应：CommitResponse（COMMIT_OK / COMMIT_NOT_FOUND     */
    private static final MethodDescriptor<CacheSyncProto.CommitRequest, CacheSyncProto.CommitResponse> COMMIT_CACHE_METHOD =
            MethodDescriptor.<CacheSyncProto.CommitRequest, CacheSyncProto.CommitResponse>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, "CommitCache"))
                    .setRequestMarshaller(new JsonMarshaller<>(CacheSyncProto.CommitRequest.class))
                    .setResponseMarshaller(new JsonMarshaller<>(CacheSyncProto.CommitResponse.class))
                    .build();

    /**
     * 方法描述符：AbortCache（Abort 阶段     * 
     * 全限定方法名: "cachesync.CacheSyncService/AbortCache"
     * 请求：AbortRequest（syncId     * 响应：AbortResponse
     */
    private static final MethodDescriptor<CacheSyncProto.AbortRequest, CacheSyncProto.AbortResponse> ABORT_CACHE_METHOD =
            MethodDescriptor.<CacheSyncProto.AbortRequest, CacheSyncProto.AbortResponse>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, "AbortCache"))
                    .setRequestMarshaller(new JsonMarshaller<>(CacheSyncProto.AbortRequest.class))
                    .setResponseMarshaller(new JsonMarshaller<>(CacheSyncProto.AbortResponse.class))
                    .build();

    // ======================== 工厂方法 ========================

    /** 创建异步客户Stub */
    public static CacheSyncServiceStub newStub(Channel channel) {
        return new CacheSyncServiceStub(channel);
    }

    /** 创建阻塞客户Stub */
    public static CacheSyncServiceBlockingStub newBlockingStub(Channel channel) {
        return new CacheSyncServiceBlockingStub(channel);
    }

    // ======================== 服务端基========================

    /**
     * 服务端基     * 
     * 用户需继承此类并实现对应方法来提供具体业务逻辑     * 默认实现返回 UNIMPLEMENTED 错误     * 
     * 可覆写的方法     * - syncCache():    原有单阶段同     * - prepareCache(): Prepare 阶段
     * - commitCache():  Commit 阶段
     * - abortCache():   Abort 阶段
     * 
     * bindService() 方法将所RPC 方法注册gRPC Server     * Server 能够路由 incoming 请求到正确的处理方法     */
    public static abstract class CacheSyncServiceImplBase implements BindableService {

        /**
         * 同步缓存方法（原单阶段同步，需子类实现         * 
         * @param request          客户端发送的缓存同步请求
         * @param responseObserver 响应观察者，用于向客户端发送响         */
        public void syncCache(CacheSyncProto.CacheSyncRequest request,
                              StreamObserver<CacheSyncProto.CacheSyncResponse> responseObserver) {
            responseObserver.onError(Status.UNIMPLEMENTED.asException());
        }

        /**
         * Prepare 阶段方法（需子类实现         * 
         * 接收 PrepareRequest，编译表达式并存入待激活区         * 返回 PREPARE_OK PREPARE_FAILED         * 
         * @param request          Prepare 请求（syncId + 表达式源码）
         * @param responseObserver 响应观察         */
        public void prepareCache(CacheSyncProto.PrepareRequest request,
                                 StreamObserver<CacheSyncProto.PrepareResponse> responseObserver)  {
            responseObserver.onError(Status.UNIMPLEMENTED.asException());
        }

        /**
         * Commit 阶段方法（需子类实现         * 
         * 接收 CommitRequest，从待激活区取预编译对象原子写入主缓存         * 
         * @param request          Commit 请求（syncId         * @param responseObserver 响应观察         */
        public void commitCache(CacheSyncProto.CommitRequest request,
                                StreamObserver<CacheSyncProto.CommitResponse> responseObserver) {
            responseObserver.onError(Status.UNIMPLEMENTED.asException());
        }

        /**
         * Abort 阶段方法（需子类实现         * 
         * 接收 AbortRequest，清理待激活区对应条目         * 
         * @param request          Abort 请求（syncId         * @param responseObserver 响应观察         */
        public void abortCache(CacheSyncProto.AbortRequest request,
                               StreamObserver<CacheSyncProto.AbortResponse> responseObserver) {
            responseObserver.onError(Status.UNIMPLEMENTED.asException());
        }

        /**
         * 将服务绑定到 gRPC Server
         * 
         * 注册所4 RPC 方法（SyncCache、PrepareCache、CommitCache、AbortCache）         * 使用 asyncUnaryCall 包装器处理异步请响应调用         */
        @Override
        public ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder(SERVICE_NAME)
                    // 注册 SyncCache 方法（原有单阶段同步）
                    .addMethod(SYNC_CACHE_METHOD,
                            ServerCalls.asyncUnaryCall(
                                    new ServerCalls.UnaryMethod<CacheSyncProto.CacheSyncRequest, CacheSyncProto.CacheSyncResponse>() {
                                        @Override
                                        public void invoke(CacheSyncProto.CacheSyncRequest request,
                                                           StreamObserver<CacheSyncProto.CacheSyncResponse> responseObserver) {
                                            CacheSyncServiceImplBase.this.syncCache(request, responseObserver);
                                        }
                                    }))
                    // 注册 PrepareCache 方法（两阶段提交 - 阶段一）
                    .addMethod(PREPARE_CACHE_METHOD,
                            ServerCalls.asyncUnaryCall(
                                    new ServerCalls.UnaryMethod<CacheSyncProto.PrepareRequest, CacheSyncProto.PrepareResponse>() {
                                        @Override
                                        public void invoke(CacheSyncProto.PrepareRequest request,
                                                           StreamObserver<CacheSyncProto.PrepareResponse> responseObserver) {
                                            CacheSyncServiceImplBase.this.prepareCache(request, responseObserver);
                                        }
                                    }))
                    // 注册 CommitCache 方法（两阶段提交 - 阶段二）
                    .addMethod(COMMIT_CACHE_METHOD,
                            ServerCalls.asyncUnaryCall(
                                    new ServerCalls.UnaryMethod<CacheSyncProto.CommitRequest, CacheSyncProto.CommitResponse>() {
                                        @Override
                                        public void invoke(CacheSyncProto.CommitRequest request,
                                                           StreamObserver<CacheSyncProto.CommitResponse> responseObserver) {
                                            CacheSyncServiceImplBase.this.commitCache(request, responseObserver);
                                        }
                                    }))
                    // 注册 AbortCache 方法（两阶段提交 - 回滚）
                    .addMethod(ABORT_CACHE_METHOD,
                            ServerCalls.asyncUnaryCall(
                                    new ServerCalls.UnaryMethod<CacheSyncProto.AbortRequest, CacheSyncProto.AbortResponse>() {
                                        @Override
                                        public void invoke(CacheSyncProto.AbortRequest request,
                                                           StreamObserver<CacheSyncProto.AbortResponse> responseObserver) {
                                            CacheSyncServiceImplBase.this.abortCache(request, responseObserver);
                                        }
                                    }))
                    .build();
        }
    }

    // ======================== 异步客户Stub ========================

    /**
     * 异步客户Stub
     * 
     * 用于非阻塞调用，通过 StreamObserver 回调处理响应     * 包含所4 RPC 方法的异步调用接口     */
    public static final class CacheSyncServiceStub extends AbstractStub<CacheSyncServiceStub> {
        private CacheSyncServiceStub(Channel channel) { super(channel); }
        private CacheSyncServiceStub(Channel channel, CallOptions callOptions) { super(channel, callOptions); }

        @Override
        protected CacheSyncServiceStub build(Channel channel, CallOptions callOptions) {
            return new CacheSyncServiceStub(channel, callOptions);
        }

        /**
         * 异步调用 syncCache
         */
        public void syncCache(CacheSyncProto.CacheSyncRequest request,
                              StreamObserver<CacheSyncProto.CacheSyncResponse> responseObserver) {
            ClientCalls.asyncUnaryCall(getChannel().newCall(SYNC_CACHE_METHOD, getCallOptions()), request, responseObserver);
        }

        /**
         * 异步调用 prepareCache（Prepare 阶段         * 
         * 向目Pod 发PrepareRequest，通过 responseObserver 回调获取响应         * Near-Zero 场景（无目标 Pod）仍需调用以触responseObserver 回调         * 
         * @param request          Prepare 请求
         * @param responseObserver 响应回调
         */
        public void prepareCache(CacheSyncProto.PrepareRequest request,
                                 StreamObserver<CacheSyncProto.PrepareResponse> responseObserver) {
            ClientCalls.asyncUnaryCall(getChannel().newCall(PREPARE_CACHE_METHOD, getCallOptions()), request, responseObserver);
        }

        /**
         * 异步调用 commitCache（Commit 阶段         * 
         * 向目Pod 发CommitRequest，请求原子替换主缓存         * 
         * @param request          Commit 请求
         * @param responseObserver 响应回调
         */
        public void commitCache(CacheSyncProto.CommitRequest request,
                                StreamObserver<CacheSyncProto.CommitResponse> responseObserver) {
            ClientCalls.asyncUnaryCall(getChannel().newCall(COMMIT_CACHE_METHOD, getCallOptions()), request, responseObserver);
        }

        /**
         * 异步调用 abortCache（Abort 阶段         * 
         * 向目Pod 发AbortRequest，请求清理待激活区         * 
         * @param request          Abort 请求
         * @param responseObserver 响应回调
         */
        public void abortCache(CacheSyncProto.AbortRequest request,
                               StreamObserver<CacheSyncProto.AbortResponse> responseObserver) {
            ClientCalls.asyncUnaryCall(getChannel().newCall(ABORT_CACHE_METHOD, getCallOptions()), request, responseObserver);
        }
    }

    // ======================== 阻塞客户Stub ========================

    /**
     * 阻塞客户Stub
     * 
     * 用于同步调用，调用线程会阻塞直到收到响应或超时     * 适用于广播场景中的并发调用（每个调用在独立线程中执行）     * 包含所4 RPC 方法的阻塞调用接口     */
    public static final class CacheSyncServiceBlockingStub extends AbstractStub<CacheSyncServiceBlockingStub> {
        private CacheSyncServiceBlockingStub(Channel channel) { super(channel); }
        private CacheSyncServiceBlockingStub(Channel channel, CallOptions callOptions) { super(channel, callOptions); }

        @Override
        protected CacheSyncServiceBlockingStub build(Channel channel, CallOptions callOptions) {
            return new CacheSyncServiceBlockingStub(channel, callOptions);
        }

        /**
         * 同步调用 syncCache
         * 
         * @param request 请求消息
         * @return 响应消息
         * @throws StatusRuntimeException 当调用失败时抛出
         */
        public CacheSyncProto.CacheSyncResponse syncCache(CacheSyncProto.CacheSyncRequest request) {
            return ClientCalls.blockingUnaryCall(getChannel().newCall(SYNC_CACHE_METHOD, getCallOptions()), request);
        }

        /**
         * 同步调用 prepareCache（Prepare 阶段         * 
         * 调用线程阻塞直到收到 PREPARE_OK/PREPARE_FAILED 响应或超时         * 
         * @param request Prepare 请求
         * @return PrepareResponse（status = PREPARE_OK / PREPARE_FAILED / ALREADY_EXISTS         * @throws StatusRuntimeException 当调用失败时抛出（UNAVAILABLE 视为 Pod 下线 = 成功         */
        public CacheSyncProto.PrepareResponse prepareCache(CacheSyncProto.PrepareRequest request) {
            return ClientCalls.blockingUnaryCall(getChannel().newCall(PREPARE_CACHE_METHOD, getCallOptions()), request);
        }

        /**
         * 同步调用 commitCache（Commit 阶段         * 
         * 调用线程阻塞直到收到 COMMIT_OK 响应或超时         * 
         * @param request Commit 请求
         * @return CommitResponse（status = COMMIT_OK / COMMIT_NOT_FOUND         * @throws StatusRuntimeException 当调用失败时抛出
         */
        public CacheSyncProto.CommitResponse commitCache(CacheSyncProto.CommitRequest request) {
            return ClientCalls.blockingUnaryCall(getChannel().newCall(COMMIT_CACHE_METHOD, getCallOptions()), request);
        }

        /**
         * 同步调用 abortCache（Abort 阶段         * 
         * 调用线程阻塞直到收到 Abort 响应或超时         * Abort 通常不需要严格等待结果，但阻Stub 仍需支持同步调用         * 
         * @param request Abort 请求
         * @return AbortResponse
         * @throws StatusRuntimeException 当调用失败时抛出
         */
        public CacheSyncProto.AbortResponse abortCache(CacheSyncProto.AbortRequest request) {
            return ClientCalls.blockingUnaryCall(getChannel().newCall(ABORT_CACHE_METHOD, getCallOptions()), request);
        }
    }
}