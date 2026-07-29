package com.businesslogic.grpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.MethodDescriptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * JSON 序列化器（gRPC Marshaller 实现 * 
 * 实现 gRPC MethodDescriptor.Marshaller 接口 * gRPC 能够使用 JSON 格式传输消息（替Protobuf 二进制格式） * 
 * 设计说明 * - 使用 Jackson ObjectMapper 进行 JSON 序列反序列化
 * - stream(): Java 对象序列化为 JSON 字节流（发送方向）
 * - parse():  JSON 字节流反序列化为 Java 对象（接收方向）
 * 
 * 为什么使JSON 而不Protobuf * - 项目路径包含中文字符，protoc 编译器无法正常工 * - JSON 格式便于调试和日志查 * - 性能损失在缓存同步场景下可接受（非高频调用）
 * 
 * @param <T> 消息类型（CacheSyncRequest CacheSyncResponse */
public class JsonMarshaller<T> implements MethodDescriptor.Marshaller<T> {

    /** Jackson JSON 序列化器，线程安全，全局复用 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 目标消息类型Class 对象，用于反序列*/
    private final Class<T> clazz;

    /**
     * 构造函     * 
     * @param clazz 消息类型Class 对象，如 CacheSyncRequest.class
     */
    public JsonMarshaller(Class<T> clazz) {
        this.clazz = clazz;
    }

    /**
     * 序列化：Java 对象转换InputStream
     * 
     * 调用流程     * 1. 使用 Jackson 将对象序列化JSON 字节数组
     * 2. 包装ByteArrayInputStream 返回
     * 
     * @param value 待序列化Java 对象
     * @return 包含 JSON 数据InputStream
     * @throws RuntimeException 序列化失败时抛出
     */
    @Override
    public InputStream stream(T value) {
        try {
            byte[] bytes = MAPPER.writeValueAsBytes(value);
            return new ByteArrayInputStream(bytes);
        } catch (Exception e) {
            throw new RuntimeException("序列化失", e);
        }
    }

    /**
     * 反序列化：将 InputStream 转换Java 对象
     * 
     * 调用流程     * 1. InputStream 读取 JSON 数据
     * 2. 使用 Jackson 反序列化为目标类型对     * 
     * @param stream 包含 JSON 数据InputStream
     * @return 反序列化后的 Java 对象
     * @throws RuntimeException 反序列化失败时抛     */
    @Override
    public T parse(InputStream stream) {
        try {
            return MAPPER.readValue(stream, clazz);
        } catch (IOException e) {
            throw new RuntimeException("反序列化失败", e);
        }
    }
}