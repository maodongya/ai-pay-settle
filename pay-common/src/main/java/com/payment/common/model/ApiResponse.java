package com.payment.common.model; // 通用模型类所在包

import java.time.Instant; // 时间点类型，用于记录响应时间戳
import java.util.UUID; // UUID 工具，用于生成链路追踪 ID

/**
 * 统一 API 响应封装，包含状态码、消息、数据及追踪信息
 *
 * @param <T> 响应数据类型
 */
public class ApiResponse<T> {

    private int code; // 响应状态码，0 表示成功
    private String message; // 响应消息
    private T data; // 响应业务数据
    private String traceId; // 链路追踪 ID
    private String timestamp; // 响应时间戳

    /**
     * 构建成功响应
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return 成功响应对象
     */
    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>(); // 创建响应实例
        r.code = 0; // 设置成功码
        r.message = "success"; // 设置成功消息
        r.data = data; // 设置业务数据
        r.traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 12); // 生成 12 位追踪 ID
        r.timestamp = Instant.now().toString(); // 设置当前时间戳
        return r; // 返回成功响应
    }

    /**
     * 构建失败响应
     *
     * @param code    错误码
     * @param message 错误消息
     * @param <T>     数据类型
     * @return 失败响应对象
     */
    public static <T> ApiResponse<T> fail(int code, String message) {
        ApiResponse<T> r = new ApiResponse<>(); // 创建响应实例
        r.code = code; // 设置错误码
        r.message = message; // 设置错误消息
        r.traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 12); // 生成 12 位追踪 ID
        r.timestamp = Instant.now().toString(); // 设置当前时间戳
        return r; // 返回失败响应
    }

    /**
     * 获取响应状态码
     *
     * @return 状态码
     */
    public int getCode() {
        return code; // 返回状态码
    }

    /**
     * 获取响应消息
     *
     * @return 消息内容
     */
    public String getMessage() {
        return message; // 返回消息
    }

    /**
     * 获取响应数据
     *
     * @return 业务数据
     */
    public T getData() {
        return data; // 返回业务数据
    }

    /**
     * 获取链路追踪 ID
     *
     * @return 追踪 ID
     */
    public String getTraceId() {
        return traceId; // 返回追踪 ID
    }

    /**
     * 获取响应时间戳
     *
     * @return 时间戳字符串
     */
    public String getTimestamp() {
        return timestamp; // 返回时间戳
    }
}
