package com.notshahrad.spring_boot_base.common.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class ApplicationExceptionTest {

    @Test
    void businessExceptionPreservesMessageAndCause() {
        IllegalStateException cause = new IllegalStateException("operation failed");
        BusinessException exception = new BusinessException("Business rule violated", cause);

        assertInstanceOf(BaseException.class, exception);
        assertEquals("Business rule violated", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void businessExceptionSupportsMessageOnly() {
        BusinessException exception = new BusinessException("Insufficient balance");

        assertEquals("Insufficient balance", exception.getMessage());
    }

    @Test
    void notFoundExceptionDescribesMissingResource() {
        ResourceNotFoundException exception = new ResourceNotFoundException("User", 123);

        assertInstanceOf(BaseException.class, exception);
        assertEquals("User not found with id: 123", exception.getMessage());
    }
}
