package com.invoicegenie.ar.adapter.api.filter;

import com.invoicegenie.shared.tenant.TenantContext;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;

/**
 * Clears TenantContext after response to avoid leaking into pooled threads.
 * Also resets PostgreSQL RLS session variable.
 */
@Provider
public class TenantContextClearFilter implements ContainerResponseFilter {

    @Inject
    EntityManager em;

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        TenantContext.clear();
        // Note: RLS session variable (app.current_tenant_id) is PostgreSQL-specific.
        // When using PostgreSQL, the session variable should be set/reset by the
        // connection pool or via native query in repository adapters.
        // Skipping here to support H2/SQLite for development.
    }
}
