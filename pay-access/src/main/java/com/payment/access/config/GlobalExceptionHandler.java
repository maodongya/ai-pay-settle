package com.payment.access.config; // 接入层配置包

import com.payment.common.exception.BizException; // 业务异常
import com.payment.common.model.ApiResponse; // 统一 API 响应
import org.springframework.http.ResponseEntity; // HTTP 响应实体
import org.springframework.web.bind.annotation.ExceptionHandler; // 异常处理器注解
import org.springframework.web.bind.annotation.RestControllerAdvice; // 全局控制器增强

/**
 * 全局异常处理器，统一捕获并返回 API 错误响应。
 */
@RestControllerAdvice // 全局异常处理
public class GlobalExceptionHandler {

    /**
     * 处理业务异常。
     */
    @ExceptionHandler(BizException.class) // 捕获业务异常
    public ResponseEntity<ApiResponse<Void>> handleBiz(BizException e) {
        return ResponseEntity.ok(ApiResponse.fail(e.getCode(), e.getMessage())); // 返回业务错误码
    }

    /**
     * 处理其他未捕获异常。
     */
    @ExceptionHandler(Exception.class) // 捕获所有异常
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception e) {
        return ResponseEntity.internalServerError() // HTTP 500
                .body(ApiResponse.fail(50000, e.getMessage())); // 返回系统错误
    }
}
