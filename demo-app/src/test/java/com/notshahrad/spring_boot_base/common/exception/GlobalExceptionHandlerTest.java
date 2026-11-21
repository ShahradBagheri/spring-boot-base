package com.notshahrad.spring_boot_base.common.exception;

import com.notshahrad.spring_boot_base.common.response.ApiError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void shouldReturnNotFoundForResourceNotFoundException() {
        ResponseEntity<ApiError> response = handler.handleResourceNotFound(new ResourceNotFoundException("User", 123));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().status());
        assertEquals("User not found with id: 123", response.getBody().message());
        assertTrue(response.getBody().fieldErrors().isEmpty());
        assertNotNull(response.getBody().timestamp());
    }

    @Test
    void shouldReturnBadRequestForBusinessException() {
        ResponseEntity<ApiError> response = handler.handleBusinessException(new BusinessException("Insufficient balance"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().status());
        assertEquals("Insufficient balance", response.getBody().message());
        assertTrue(response.getBody().fieldErrors().isEmpty());
    }

    @Test
    void shouldReturnInternalServerErrorWithoutExposingExceptionDetails() {
        ResponseEntity<ApiError> response = handler.handleGeneralException(new RuntimeException("Internal details"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().status());
        assertEquals("An unexpected error occurred", response.getBody().message());
        assertTrue(response.getBody().fieldErrors().isEmpty());
    }

    @Test
    void shouldReturnBadRequestWithFieldLevelValidationErrors() {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "testRequest");
        bindingResult.addError(new FieldError("testRequest", "name", "Name is required"));
        bindingResult.addError(new FieldError("testRequest", "email", "Email must be valid"));

        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(null, bindingResult);
        ResponseEntity<ApiError> response = handler.handleValidationException(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().status());
        assertEquals("Validation failed", response.getBody().message());
        assertEquals("Name is required", response.getBody().fieldErrors().get("name"));
        assertEquals("Email must be valid", response.getBody().fieldErrors().get("email"));
        assertNotNull(response.getBody().timestamp());
    }

    @Test
    void shouldUseSafeFallbackWhenValidationMessageIsUnavailable() {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "testRequest");
        bindingResult.addError(new FieldError("testRequest", "name", null));

        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(null, bindingResult);
        ResponseEntity<ApiError> response = handler.handleValidationException(exception);

        assertNotNull(response.getBody());
        assertEquals("Invalid value", response.getBody().fieldErrors().get("name"));
    }
}
