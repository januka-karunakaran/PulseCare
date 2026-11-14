package com.hospital.backend.constant;

/**
 * Standardized enterprise error code registry.
 */
public final class ErrorCodes {

    private ErrorCodes() {
    }

    // Authentication & Authorization (AUTH_xxx)
    public static final String AUTH_INVALID_CREDENTIALS = "AUTH_001";
    public static final String AUTH_TOKEN_EXPIRED = "AUTH_002";
    public static final String AUTH_TOKEN_INVALID = "AUTH_003";
    public static final String AUTH_ACCESS_DENIED = "AUTH_004";
    public static final String AUTH_USER_NOT_FOUND = "AUTH_005";

    // Resource Management (RES_xxx)
    public static final String RES_NOT_FOUND = "RES_001";
    public static final String RES_DUPLICATE = "RES_002";
    public static final String RES_BAD_REQUEST = "RES_003";

    // Business Logic (BIZ_xxx)
    public static final String BIZ_APPOINTMENT_SLOT_TAKEN = "BIZ_001";
    public static final String BIZ_BED_UNAVAILABLE = "BIZ_002";
    public static final String BIZ_DOCTOR_UNAVAILABLE = "BIZ_003";
    public static final String BIZ_INSUFFICIENT_INVENTORY = "BIZ_004";

    // System (SYS_xxx)
    public static final String SYS_INTERNAL_ERROR = "SYS_001";
    public static final String SYS_RATE_LIMIT_EXCEEDED = "SYS_002";
}
