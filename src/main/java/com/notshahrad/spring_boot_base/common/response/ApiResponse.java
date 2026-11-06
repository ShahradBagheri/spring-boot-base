package com.notshahrad.spring_boot_base.common.response;

import java.time.Instant;

public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        Instant timestamp
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(
                true,
                "Request completed successfully",
                data,
                Instant.now()
        );
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(
                true,
                message,
                data,
                Instant.now()
        );
    }

    public static ApiResponse<Void> successMessage(String message) {
        return new ApiResponse<>(
                true,
                message,
                null,
                Instant.now()
        );
    }
}
