package com.invoicegenie.ar.adapter.api.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ErrorResponse")
class ErrorResponseTest {

    @Test
    @DisplayName("should hold code and message")
    void holdsFields() {
        ErrorResponse r = new ErrorResponse("VALIDATION_ERROR", "bad input");
        assertEquals("VALIDATION_ERROR", r.code());
        assertEquals("bad input", r.message());
    }
}