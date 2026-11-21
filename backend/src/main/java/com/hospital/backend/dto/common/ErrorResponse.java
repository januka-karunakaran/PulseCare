package com.hospital.backend.dto.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Standard enterprise error response payload.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    @Builder.Default
    private boolean success = false;

    private int status;
    private String error;
    private String code;
    private String message;
    private String path;

    @Builder.Default
    private Instant timestamp = Instant.now();

    private Map<String, String> validationErrors;
}
