package com.notshahrad.spring_boot_base.consumer;

import com.notshahrad.spring_boot_base.common.exception.BaseException;
import com.notshahrad.spring_boot_base.common.exception.BusinessException;
import com.notshahrad.spring_boot_base.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ApplicationExceptionTest {

    @Test
    void businessExceptionCarriesMessageAndCause() {
        IllegalStateException cause = new IllegalStateException("downstream failure");

        BusinessException exception = new BusinessException("Insufficient balance", cause);

        assertEquals("Insufficient balance", exception.getMessage());
        assertSame(cause, exception.getCause());
        assertInstanceOf(BaseException.class, exception);
    }

    @Test
    void resourceNotFoundExceptionFormatsResourceNameAndIdentifier() {
        ResourceNotFoundException exception = new ResourceNotFoundException("User", 123);

        assertEquals("User not found with id: 123", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void resourceNotFoundExceptionAcceptsStringIdentifier() {
        ResourceNotFoundException exception = new ResourceNotFoundException("Order", "order-42");

        assertEquals("Order not found with id: order-42", exception.getMessage());
    }

    @Test
    void resourceNotFoundExceptionRendersNullIdentifier() {
        ResourceNotFoundException exception = new ResourceNotFoundException("User", null);

        assertEquals("User not found with id: null", exception.getMessage());
    }
}
