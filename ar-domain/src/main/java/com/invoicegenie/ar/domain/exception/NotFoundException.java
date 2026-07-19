package com.invoicegenie.ar.domain.exception;

/**
 * Thrown when a domain entity or aggregate cannot be found.
 * Mapped to HTTP 404 by the API layer.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public NotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
