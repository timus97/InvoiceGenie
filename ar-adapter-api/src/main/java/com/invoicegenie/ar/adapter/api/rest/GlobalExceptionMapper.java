package com.invoicegenie.ar.adapter.api.rest;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Global exception handler for uncaught exceptions.
 */
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    @Override
    public Response toResponse(Throwable exception) {
        int status = 500;
        String code = "INTERNAL_ERROR";

        if (exception instanceof IllegalArgumentException) {
            status = 400;
            code = "VALIDATION_ERROR";
        } else if (exception instanceof IllegalStateException) {
            status = 409;
            code = "STATE_ERROR";
        }

        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(new InvoiceResource.ErrorDto(code, exception.getMessage()))
                .build();
    }
}
