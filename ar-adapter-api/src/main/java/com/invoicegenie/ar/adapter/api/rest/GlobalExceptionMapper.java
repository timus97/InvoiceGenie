package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.domain.exception.DomainValidationException;
import com.invoicegenie.ar.domain.exception.InvalidStateTransitionException;
import com.invoicegenie.ar.domain.exception.NotFoundException;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Global exception handler for uncaught exceptions.
 *
 * <p>Maps domain exceptions (and common JDK runtime exceptions) to HTTP status codes:
 * <ul>
 *   <li>DomainValidationException / IllegalArgumentException → 400 VALIDATION_ERROR</li>
 *   <li>InvalidStateTransitionException / IllegalStateException → 409 STATE_ERROR</li>
 *   <li>NotFoundException → 404 NOT_FOUND</li>
 *   <li>everything else → 500 INTERNAL_ERROR</li>
 * </ul>
 */
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    @Override
    public Response toResponse(Throwable exception) {
        int status = 500;
        String code = "INTERNAL_ERROR";

        if (exception instanceof DomainValidationException
                || exception instanceof IllegalArgumentException) {
            status = 400;
            code = "VALIDATION_ERROR";
        } else if (exception instanceof InvalidStateTransitionException
                || exception instanceof IllegalStateException) {
            status = 409;
            code = "STATE_ERROR";
        } else if (exception instanceof NotFoundException) {
            status = 404;
            code = "NOT_FOUND";
        }

        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(new InvoiceResource.ErrorDto(code, exception.getMessage()))
                .build();
    }
}
