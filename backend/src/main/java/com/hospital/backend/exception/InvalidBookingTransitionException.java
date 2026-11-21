package com.hospital.backend.exception;

/**
 * Thrown when an invalid status transition is requested for an appointment or bed booking.
 * Maps to HTTP 409 Conflict with INVALID_BOOKING_TRANSITION error code.
 */
public class InvalidBookingTransitionException extends RuntimeException {

    private final String errorCode;

    public InvalidBookingTransitionException(String message) {
        super(message);
        this.errorCode = "INVALID_BOOKING_TRANSITION";
    }

    public InvalidBookingTransitionException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
