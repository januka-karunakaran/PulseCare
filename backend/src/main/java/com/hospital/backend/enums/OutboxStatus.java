package com.hospital.backend.enums;

public enum OutboxStatus {
    PENDING,
    PROCESSING,
    DELIVERED,
    RETRY,
    FAILED,
    CANCELLED
}
