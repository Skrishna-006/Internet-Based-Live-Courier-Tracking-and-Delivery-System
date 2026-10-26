package com.courier.tracking.dto;

import java.time.Instant;

/**
 * Standardized API response envelope for all REST endpoints.
 *
 * @param success Indicates whether the operation succeeded
 * @param message Human-readable message or error description
 * @param data Payload returned to the client (or null on error)
 * @param timestamp UTC timestamp when the response was generated
 */
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        Instant timestamp
) {
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, Instant.now());
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "Operation successful", data, Instant.now());
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(String message, T errorDetails) {
        return new ApiResponse<>(false, message, errorDetails, Instant.now());
    }
}
