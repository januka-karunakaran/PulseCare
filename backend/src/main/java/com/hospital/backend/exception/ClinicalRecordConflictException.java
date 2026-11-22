package com.hospital.backend.exception;

import lombok.Getter;

@Getter
public class ClinicalRecordConflictException extends RuntimeException {

    private final String errorCode;

    public ClinicalRecordConflictException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
