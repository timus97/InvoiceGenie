package com.invoicegenie.ar.adapter.api.filter;

import com.invoicegenie.shared.domain.TenantId;
import com.invoicegenie.shared.tenant.TenantContext;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.Optional;

/**
 * Resolves tenant from header (or JWT in production) and sets TenantContext.
 * Single point of tenant resolution — no use case runs without tenant.
 */
@Provider
public class TenantFilter implements ContainerRequestFilter {

    private static final String TENANT_HEADER = "X-Tenant-Id";

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        try {
            TenantId tenantId = resolveTenant(requestContext);
            TenantContext.setCurrentTenant(tenantId);
        } catch (IllegalArgumentException e) {
            requestContext.abortWith(Response.status(Response.Status.BAD_REQUEST).entity("Invalid or missing tenant").build());
        }
    }

    private TenantId resolveTenant(ContainerRequestContext requestContext) {
        String value = requestContext.getHeaderString(TENANT_HEADER);
        return Optional.ofNullable(value)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(TenantId::of)
                .orElseThrow(() -> new IllegalArgumentException("Missing " + TENANT_HEADER));
    }
}
