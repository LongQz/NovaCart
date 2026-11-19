package com.novacart.mall.service;

/** Authentication or role validation failure. */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
