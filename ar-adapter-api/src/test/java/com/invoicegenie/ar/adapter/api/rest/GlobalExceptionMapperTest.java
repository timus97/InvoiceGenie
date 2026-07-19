package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.adapter.api.dto.ErrorResponse;
import com.invoicegenie.ar.domain.exception.DomainValidationException;
import com.invoicegenie.ar.domain.exception.InvalidStateTransitionException;
import com.invoicegenie.ar.domain.exception.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GlobalExceptionMapper")
class GlobalExceptionMapperTest {

    private final GlobalExceptionMapper mapper = new GlobalExceptionMapper();

    @Test
    @DisplayName("maps DomainValidationException to 400")
    void mapsDomainValidation() {
        Response r = mapper.toResponse(new DomainValidationException("invalid"));
        assertEquals(400, r.getStatus());
        ErrorResponse body = (ErrorResponse) r.getEntity();
        assertEquals("VALIDATION_ERROR", body.code());
    }

    @Test
    @DisplayName("maps IllegalArgumentException to 400")
    void mapsIllegalArgument() {
        Response r = mapper.toResponse(new IllegalArgumentException("bad"));
        assertEquals(400, r.getStatus());
    }

    @Test
    @DisplayName("maps InvalidStateTransitionException to 409")
    void mapsState() {
        Response r = mapper.toResponse(new InvalidStateTransitionException("bad state"));
        assertEquals(409, r.getStatus());
        ErrorResponse body = (ErrorResponse) r.getEntity();
        assertEquals("STATE_ERROR", body.code());
    }

    @Test
    @DisplayName("maps IllegalStateException to 409")
    void mapsIllegalState() {
        assertEquals(409, mapper.toResponse(new IllegalStateException("x")).getStatus());
    }

    @Test
    @DisplayName("maps NotFoundException to 404")
    void mapsNotFound() {
        Response r = mapper.toResponse(new NotFoundException("missing"));
        assertEquals(404, r.getStatus());
        ErrorResponse body = (ErrorResponse) r.getEntity();
        assertEquals("NOT_FOUND", body.code());
    }

    @Test
    @DisplayName("maps unknown to 500")
    void mapsUnknown() {
        Response r = mapper.toResponse(new RuntimeException("boom"));
        assertEquals(500, r.getStatus());
        ErrorResponse body = (ErrorResponse) r.getEntity();
        assertEquals("INTERNAL_ERROR", body.code());
    }
}