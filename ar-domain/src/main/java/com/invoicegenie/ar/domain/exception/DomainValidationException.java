package com.invoicegenie.ar.domain.exception;

/**
 * Thrown when domain validation fails (invalid input or business constraint).
 * Mapped to HTTP 400 by the API layer.
 */
public class DomainValidationException extends RuntimeException {

    public DomainValidationException(String message) {
        super(message);
    }

    public DomainValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
