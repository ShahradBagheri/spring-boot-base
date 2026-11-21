package com.notshahrad.spring_boot_base.common.response;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record ApiError(
        int status,
        String message,
        Instant timestamp,
        Map<String, String> fieldErrors
) {
    public ApiError {
        if (status < 400 || status > 599) {
            throw new IllegalArgumentException("status must represent an HTTP error");
        }

        Objects.requireNonNull(message, "message must not be null");
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }

        Objects.requireNonNull(timestamp, "timestamp must not be null");
        fieldErrors = Map.copyOf(Objects.requireNonNull(fieldErrors, "fieldErrors must not be null"));
    }

    public static ApiError of(int status, String message) {
        return new ApiError(status, message, Instant.now(), Map.of());
    }

    public static ApiError of(int status, String message, Map<String, String> fieldErrors) {
        return new ApiError(status, message, Instant.now(), fieldErrors);
    }
}
