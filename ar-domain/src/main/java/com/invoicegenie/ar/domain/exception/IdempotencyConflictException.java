package com.invoicegenie.ar.domain.exception;

/**
 * Thrown when an Idempotency-Key is reused with a different request payload.
 * Mapped to HTTP 409 by the API layer.
 */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String message) {
        super(message);
    }
}