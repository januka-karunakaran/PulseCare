package com.hospital.backend.exception;

import lombok.Getter;

@Getter
public class OverpaymentException extends RuntimeException {
    private final String errorCode;

    public OverpaymentException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
