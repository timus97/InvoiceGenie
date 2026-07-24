package com.invoicegenie.ar.domain.exception;

/**
 * Thrown when a customer cannot be invoiced (blocked/deleted or credit limit exceeded).
 * Mapped to HTTP 409 with code CUSTOMER_NOT_INVOICEABLE.
 */
public class CustomerNotInvoiceableException extends RuntimeException {

    public static final String CODE = "CUSTOMER_NOT_INVOICEABLE";

    private final String errorCode;

    public CustomerNotInvoiceableException(String message) {
        this(CODE, message);
    }

    public CustomerNotInvoiceableException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode != null ? errorCode : CODE;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
