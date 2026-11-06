package com.notshahrad.spring_boot_base.common.exception;

import com.notshahrad.spring_boot_base.common.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

public class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void shouldReturnNotFoundForResourceNotFoundException() {
        ResourceNotFoundException exception = new ResourceNotFoundException("User", 123);
        ResponseEntity<ApiResponse<Void>> response = handler.handleResourceNotFound(exception);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().success());
        assertEquals("User not found with id: 123", response.getBody().message());
        assertNull(response.getBody().data());
    }

    @Test
    void shouldReturnBadRequestForBusinessException() {
        BusinessException exception = new BusinessException("Insufficient balance");
        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(exception);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().success());
        assertEquals("Insufficient balance", response.getBody().message());
    }

    @Test
    void shouldReturnInternalServerErrorForUnexpectedException() {
        Exception exception = new RuntimeException("Internal details");
        ResponseEntity<ApiResponse<Void>> response = handler.handleGeneralException(exception);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().success());
        assertEquals("An unexpected error occurred", response.getBody().message());
        assertNotEquals("Internal details", response.getBody().message());
    }
}
