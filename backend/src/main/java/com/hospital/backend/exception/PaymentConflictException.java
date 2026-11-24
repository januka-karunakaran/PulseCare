package com.hospital.backend.exception;

import lombok.Getter;

@Getter
public class PaymentConflictException extends RuntimeException {
    private final String errorCode;

    public PaymentConflictException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
