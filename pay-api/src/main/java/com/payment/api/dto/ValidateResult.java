package com.payment.api.dto; // API 数据传输对象所在包

/**
 * 校验结果 DTO，封装账单或商户校验结果
 */
public class ValidateResult {
    public boolean valid; // 是否校验通过
    public int errorCode; // 错误码
    public String message; // 校验消息

    /**
     * 构建校验通过结果
     *
     * @return 校验通过的结果对象
     */
    public static ValidateResult ok() {
        ValidateResult r = new ValidateResult(); // 创建结果实例
        r.valid = true; // 标记为通过
        r.errorCode = 0; // 设置成功码
        r.message = "ok"; // 设置成功消息
        return r; // 返回结果
    }

    /**
     * 构建校验失败结果
     *
     * @param code    错误码
     * @param message 错误消息
     * @return 校验失败的结果对象
     */
    public static ValidateResult fail(int code, String message) {
        ValidateResult r = new ValidateResult(); // 创建结果实例
        r.valid = false; // 标记为失败
        r.errorCode = code; // 设置错误码
        r.message = message; // 设置错误消息
        return r; // 返回结果
    }
}
