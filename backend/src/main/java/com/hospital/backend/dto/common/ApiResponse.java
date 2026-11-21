package com.hospital.backend.dto.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Standard enterprise API response wrapper.
 * Guarantees a predictable JSON structure across all endpoints.
 *
 * @param <T> Response payload type
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    @Builder.Default
    private boolean success = true;

    private String message;

    private T data;

    @Builder.Default
    private Instant timestamp = Instant.now();

    @Builder.Default
    private int statusCode = 200;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message("Operation completed successfully")
                .data(data)
                .statusCode(200)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .statusCode(200)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<T> error(String message, int statusCode) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .data(null)
                .statusCode(statusCode)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<T> error(String message, int statusCode, T data) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .data(data)
                .statusCode(statusCode)
                .timestamp(Instant.now())
                .build();
    }
}
