package com.notshahrad.spring_boot_base.common.response;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiErrorTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void serializesErrorWithConsistentPublicFields() {
        Instant timestamp = Instant.parse("2026-01-02T03:04:05Z");
        ApiError error = new ApiError(404, "Resource not found", timestamp, Map.of());

        var json = jsonMapper.readTree(jsonMapper.writeValueAsString(error));

        assertEquals(4, json.size());
        assertEquals(404, json.get("status").intValue());
        assertEquals("Resource not found", json.get("message").asText());
        assertEquals(timestamp.toString(), json.get("timestamp").asText());
        assertTrue(json.get("fieldErrors").isObject());
        assertEquals(0, json.get("fieldErrors").size());
        assertNull(json.get("stackTrace"));
        assertNull(json.get("exception"));
        assertNull(json.get("cause"));
    }

    @Test
    void serializesFieldErrorsForValidationResponses() {
        ApiError error = new ApiError(
                400,
                "Validation failed",
                Instant.parse("2026-01-02T03:04:05Z"),
                Map.of("email", "Email must be valid", "name", "Name is required")
        );

        var json = jsonMapper.readTree(jsonMapper.writeValueAsString(error));

        assertEquals(400, json.get("status").intValue());
        assertEquals("Email must be valid", json.get("fieldErrors").get("email").asText());
        assertEquals("Name is required", json.get("fieldErrors").get("name").asText());
        assertEquals(2, json.get("fieldErrors").size());
    }

    @Test
    void factoriesGenerateTimestampsAndEmptyOrSuppliedFieldErrors() {
        ApiError ordinary = ApiError.of(500, "An unexpected error occurred");
        ApiError validation = ApiError.of(400, "Validation failed", Map.of("email", "Invalid email"));

        assertNotNull(ordinary.timestamp());
        assertTrue(ordinary.fieldErrors().isEmpty());
        assertNotNull(validation.timestamp());
        assertEquals("Invalid email", validation.fieldErrors().get("email"));
    }

    @Test
    void copiesFieldErrorsAndPreventsMutation() {
        Map<String, String> errors = new HashMap<>();
        errors.put("name", "Name is required");

        ApiError response = ApiError.of(400, "Validation failed", errors);
        errors.put("email", "Invalid email");

        assertEquals(Map.of("name", "Name is required"), response.fieldErrors());
        assertThrows(UnsupportedOperationException.class,
                () -> response.fieldErrors().put("age", "Invalid age"));
    }

    @Test
    void rejectsInvalidResponseValues() {
        Instant timestamp = Instant.parse("2026-01-02T03:04:05Z");

        assertThrows(IllegalArgumentException.class,
                () -> new ApiError(200, "Invalid status", timestamp, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ApiError(600, "Invalid status", timestamp, Map.of()));
        assertThrows(NullPointerException.class,
                () -> new ApiError(400, null, timestamp, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ApiError(400, " ", timestamp, Map.of()));
        assertThrows(NullPointerException.class,
                () -> new ApiError(400, "Invalid", null, Map.of()));
        assertThrows(NullPointerException.class,
                () -> new ApiError(400, "Invalid", timestamp, null));
    }
}
