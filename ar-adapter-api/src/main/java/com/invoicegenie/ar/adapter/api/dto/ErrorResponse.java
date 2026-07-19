package com.invoicegenie.ar.adapter.api.dto;

/**
 * Shared error response body for REST adapters.
 */
public record ErrorResponse(String code, String message) {}
