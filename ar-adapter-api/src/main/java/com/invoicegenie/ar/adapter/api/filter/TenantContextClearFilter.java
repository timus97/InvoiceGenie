package com.invoicegenie.ar.adapter.api.filter;

import com.invoicegenie.shared.tenant.DbTenantContext;
import com.invoicegenie.shared.tenant.TenantContext;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Clears TenantContext after response to avoid leaking into pooled threads.
 * Also resets PostgreSQL RLS session variable when on Postgres (no-op on H2).
 */
@Provider
public class TenantContextClearFilter implements ContainerResponseFilter {

    @Inject
    Instance<EntityManager> entityManager;

    @ConfigProperty(name = "quarkus.datasource.db-kind", defaultValue = "postgresql")
    String dbKind;

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        try {
            if (DbTenantContext.isPostgresDbKind(dbKind)
                    && entityManager != null
                    && !entityManager.isUnsatisfied()) {
                DbTenantContext.clearTenant(entityManager.get());
            }
        } catch (Exception ignored) {
            // best-effort
        } finally {
            TenantContext.clear();
        }
    }
}