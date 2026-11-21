package com.hospital.backend.exception;

/**
 * Thrown when attempting to book a bed that is already occupied or unavailable.
 * Maps to HTTP 409 Conflict with BED_ALREADY_OCCUPIED error code.
 */
public class BedAlreadyOccupiedException extends RuntimeException {

    private final String errorCode;

    public BedAlreadyOccupiedException(String message) {
        super(message);
        this.errorCode = "BED_ALREADY_OCCUPIED";
    }

    public BedAlreadyOccupiedException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
