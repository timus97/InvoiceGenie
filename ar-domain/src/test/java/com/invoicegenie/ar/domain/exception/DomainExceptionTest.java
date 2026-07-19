package com.invoicegenie.ar.domain.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Domain exceptions")
class DomainExceptionTest {

    @Test
    @DisplayName("DomainValidationException carries message and cause")
    void domainValidationException() {
        RuntimeException cause = new RuntimeException("root");
        DomainValidationException ex = new DomainValidationException("bad input", cause);
        assertEquals("bad input", ex.getMessage());
        assertSame(cause, ex.getCause());
        assertTrue(ex instanceof RuntimeException);
    }

    @Test
    @DisplayName("InvalidStateTransitionException carries message and cause")
    void invalidStateTransitionException() {
        InvalidStateTransitionException ex =
                new InvalidStateTransitionException("A -> B", new IllegalStateException("x"));
        assertEquals("A -> B", ex.getMessage());
        assertNotNull(ex.getCause());
    }

    @Test
    @DisplayName("NotFoundException carries message")
    void notFoundException() {
        NotFoundException ex = new NotFoundException("Customer not found");
        assertEquals("Customer not found", ex.getMessage());
        assertNull(ex.getCause());
    }
}
