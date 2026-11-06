package com.notshahrad.spring_boot_base.common.response;

import java.time.Instant;
import java.util.Map;

public record ValidationErrorResponse(
        boolean success,
        String message,
        Map<String, String> errors,
        Instant timestamp
) {
    public static ValidationErrorResponse of(Map<String, String> errors) {
        return new ValidationErrorResponse(
                false,
                "Validation failed",
                errors,
                Instant.now()
        );
    }
}
