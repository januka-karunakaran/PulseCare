package com.hospital.backend.exception;

/**
 * Thrown when a requested appointment slot has already been reserved.
 * Maps to HTTP 409 Conflict with APPOINTMENT_SLOT_CONFLICT error code.
 */
public class AppointmentSlotConflictException extends RuntimeException {

    private final String errorCode;

    public AppointmentSlotConflictException(String message) {
        super(message);
        this.errorCode = "APPOINTMENT_SLOT_CONFLICT";
    }

    public AppointmentSlotConflictException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
