package com.novacart.mall.service;

/** Business validation exception. */
public class BizException extends RuntimeException {
    public BizException(String message) {
        super(message);
    }
}
