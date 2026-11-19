package com.novacart.mall.controller;

import com.novacart.common.ApiResponse;
import com.novacart.mall.service.BizException;
import com.novacart.mall.service.UnauthorizedException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Converts domain errors into stable JSON responses. */
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> business(BizException exception) {
        return ApiResponse.fail(exception.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> unauthorized(UnauthorizedException exception) {
        return ApiResponse.fail(exception.getMessage());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> missingAuthorization(MissingRequestHeaderException exception) {
        return ApiResponse.fail("请先登录");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> unexpected(Exception exception) {
        return ApiResponse.fail("服务暂时不可用: " + exception.getMessage());
    }
}
