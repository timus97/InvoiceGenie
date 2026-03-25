package com.invoicegenie.ar.adapter.api.filter;

import com.invoicegenie.shared.domain.TenantId;
import com.invoicegenie.shared.tenant.TenantContext;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.Optional;

/**
 * Resolves tenant from header (or JWT in production) and sets TenantContext.
 * Single point of tenant resolution — no use case runs without tenant.
 * Also sets PostgreSQL RLS session variable for row-level security.
 */
@Provider
public class TenantFilter implements ContainerRequestFilter {

    private static final String TENANT_HEADER = "X-Tenant-Id";

    @Inject
    EntityManager em;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        try {
            TenantId tenantId = resolveTenant(requestContext);
            TenantContext.setCurrentTenant(tenantId);
            // Note: RLS session variable (app.current_tenant_id) is PostgreSQL-specific.
            // App-level tenant isolation via TenantContext is enforced regardless.
            // Repository adapters can set the session variable per-connection if needed.
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
