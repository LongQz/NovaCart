package com.novacart.ai.service;

import com.novacart.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一 AI 接口的业务异常响应。
 */
@RestControllerAdvice(basePackages = "com.novacart.ai")
public class AiExceptionHandler {
    @ExceptionHandler(AiBusinessException.class)
    public ResponseEntity<ApiResponse<Void>> business(AiBusinessException exception) {
        return ResponseEntity.status(exception.getStatus()).body(ApiResponse.fail(exception.getMessage()));
    }
}
