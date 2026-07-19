package com.invoicegenie.ar.domain.exception;

/**
 * Thrown when an aggregate cannot transition to the requested state.
 * Mapped to HTTP 409 by the API layer.
 */
public class InvalidStateTransitionException extends RuntimeException {

    public InvalidStateTransitionException(String message) {
        super(message);
    }

    public InvalidStateTransitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
