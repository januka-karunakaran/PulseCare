package com.hospital.backend.exception;

import lombok.Getter;

@Getter
public class InvoiceImmutableException extends RuntimeException {
    private final String errorCode;

    public InvoiceImmutableException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
