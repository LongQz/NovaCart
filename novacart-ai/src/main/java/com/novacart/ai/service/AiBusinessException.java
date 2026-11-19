package com.novacart.ai.service;

import org.springframework.http.HttpStatus;

/**
 * AI 模块对外返回的可预期业务异常。
 */
public class AiBusinessException extends RuntimeException {
    private final HttpStatus status;

    public AiBusinessException(String message) {
        this(HttpStatus.BAD_REQUEST, message);
    }

    public AiBusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
