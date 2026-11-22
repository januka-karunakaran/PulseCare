package com.hospital.backend.exception;

import lombok.Getter;

@Getter
public class InvalidVitalObservationException extends RuntimeException {

    private final String errorCode;

    public InvalidVitalObservationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
