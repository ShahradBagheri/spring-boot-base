package com.notshahrad.spring_boot_base.common.response;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class ApiResponseTest {

    @Test
    void shouldCreateSuccessfulResponseWithData() {
        String data = "Hello World";

        ApiResponse<String> response = ApiResponse.success(data);

        assertTrue(response.success());
        assertEquals("Request completed successfully", response.message());
        assertEquals(data, response.data());
        assertNotNull(response.timestamp());
    }

    @Test
    void shouldCreateSuccessfulResponseWithCustomMessage() {
        String message = "User created";
        String data = "user-123";

        ApiResponse<String> response = ApiResponse.success(message, data);

        assertTrue(response.success());
        assertEquals(message, response.message());
        assertEquals(data, response.data());
        assertNotNull(response.timestamp());
    }

    @Test
    void shouldCreateSuccessfulResponseWithoutData() {
        String message = "Operation completed";

        ApiResponse<Void> response = ApiResponse.successMessage(message);

        assertTrue(response.success());
        assertEquals(message, response.message());
        assertNull(response.data());
        assertNotNull(response.timestamp());
    }
}
