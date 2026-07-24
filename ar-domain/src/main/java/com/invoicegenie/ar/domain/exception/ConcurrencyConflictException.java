package com.invoicegenie.ar.domain.exception;

/**
 * Thrown when an optimistic concurrency check fails (stale version).
 * Mapped to HTTP 409 by the API layer.
 */
public class ConcurrencyConflictException extends RuntimeException {

    public ConcurrencyConflictException(String message) {
        super(message);
    }
}