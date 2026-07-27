package com.invoicegenie.ar.adapter.messaging;

/**
 * Extractable HTTP client for Meta Graph API (PP-002) — unit-testable.
 */
public interface MetaGraphHttpClient {

    record HttpResult(int statusCode, String body) {}

    HttpResult postJson(String url, String bearerToken, String jsonBody) throws Exception;
}
