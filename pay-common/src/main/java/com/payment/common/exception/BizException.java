package com.payment.common.exception; // 业务异常类所在包

/**
 * 业务异常，携带错误码的运行时异常
 */
public class BizException extends RuntimeException {

    private final int code; // 业务错误码

    /**
     * 构造业务异常
     *
     * @param code    错误码
     * @param message 错误信息
     */
    public BizException(int code, String message) {
        super(message); // 设置异常消息
        this.code = code; // 保存错误码
    }

    /**
     * 获取业务错误码
     *
     * @return 错误码
     */
    public int getCode() {
        return code; // 返回错误码
    }

    /**
     * 根据错误码枚举创建业务异常
     *
     * @param errorCode 错误码枚举
     * @return 业务异常实例
     */
    public static BizException of(ErrorCode errorCode) {
        return new BizException(errorCode.getCode(), errorCode.getMessage()); // 使用枚举中的码和消息
    }

    /**
     * 根据错误码枚举及附加详情创建业务异常
     *
     * @param errorCode 错误码枚举
     * @param detail    附加详情
     * @return 业务异常实例
     */
    public static BizException of(ErrorCode errorCode, String detail) {
        return new BizException(errorCode.getCode(), errorCode.getMessage() + ": " + detail); // 拼接详情到消息
    }
}
