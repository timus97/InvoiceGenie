package com.invoicegenie.ar.adapter.api.security;

/**
 * Request-scoped security attributes resolved by {@link AuthFilter}.
 */
public final class SecurityConstants {
    public static final String AUTH_TENANT_PROPERTY = "invoicegenie.auth.tenantId";
    public static final String AUTH_SUBJECT_PROPERTY = "invoicegenie.auth.subject";
    public static final String AUTH_METHOD_PROPERTY = "invoicegenie.auth.method";

    public static final String HEADER_API_KEY = "X-API-Key";
    public static final String HEADER_TENANT = "X-Tenant-Id";
    public static final String HEADER_AUTHORIZATION = "Authorization";

    private SecurityConstants() {}
}
