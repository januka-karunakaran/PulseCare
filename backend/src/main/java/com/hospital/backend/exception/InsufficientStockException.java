package com.hospital.backend.exception;

import lombok.Getter;

@Getter
public class InsufficientStockException extends RuntimeException {

    private final String errorCode;

    public InsufficientStockException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
