package com.notshahrad.spring_boot_base.common.exception;

import com.notshahrad.spring_boot_base.common.response.ApiResponse;
import com.notshahrad.spring_boot_base.common.response.ValidationErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

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

    @Test
    void shouldReturnBadRequestForValidationException() {
        BindingResult bindingResult = new BeanPropertyBindingResult(
                new Object(),
                "testRequest"
        );
        bindingResult.addError(
                new FieldError(
                        "testRequest",
                        "name",
                        "",
                        false,
                        null,
                        null,
                        "Name is required"
                )
        );
        bindingResult.addError(
                new FieldError(
                        "testRequest",
                        "email",
                        "invalid",
                        false,
                        null,
                        null,
                        "Email must be valid"
                )
        );

        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(null, bindingResult);
        ResponseEntity<ValidationErrorResponse> response = handler.handleValidationException(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        assertFalse(response.getBody().success());
        assertEquals("Validation failed", response.getBody().message());

        assertEquals(2, response.getBody().errors().size());
        assertEquals("Name is required", response.getBody().errors().get("name"));
        assertEquals("Email must be valid", response.getBody().errors().get("email"));

        assertNotNull(response.getBody().timestamp());
    }
}
