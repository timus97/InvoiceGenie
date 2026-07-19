package com.invoicegenie.ar.adapter.api.filter;

import com.invoicegenie.shared.domain.TenantId;
import com.invoicegenie.shared.tenant.DbTenantContext;
import com.invoicegenie.shared.tenant.TenantContext;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Resolves tenant from header (or JWT in production) and sets TenantContext.
 * Single point of tenant resolution — no use case runs without tenant.
 *
 * <p>Also attempts to set PostgreSQL RLS session variable
 * {@code app.current_tenant_id} via {@link DbTenantContext#setTenantLocal}
 * when the datasource is PostgreSQL and an {@link EntityManager} is available.
 * On H2 / non-Postgres this is skipped (no failing SQL). Application-level
 * tenant filtering remains authoritative.
 */
@Provider
public class TenantFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(TenantFilter.class);
    private static final String TENANT_HEADER = "X-Tenant-Id";

    /**
     * Optional — present when Hibernate is on the runtime classpath (bootstrap).
     */
    @Inject
    Instance<EntityManager> entityManager;

    @ConfigProperty(name = "quarkus.datasource.db-kind", defaultValue = "postgresql")
    String dbKind;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        try {
            TenantId tenantId = resolveTenant(requestContext);
            TenantContext.setCurrentTenant(tenantId);
            bindPostgresRls(tenantId);
        } catch (IllegalArgumentException e) {
            requestContext.abortWith(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity("Invalid or missing tenant")
                            .build());
        }
    }

    /**
     * Sets Postgres GUC for RLS when running on PostgreSQL with EntityManager.
     * Skipped entirely on H2 so native SQL never fails mid-request.
     */
    private void bindPostgresRls(TenantId tenantId) {
        try {
            if (!DbTenantContext.isPostgresDbKind(dbKind)) {
                LOG.debugf("Skipping RLS GUC set (db-kind=%s)", dbKind);
                return;
            }
            if (entityManager == null || entityManager.isUnsatisfied()) {
                return;
            }
            EntityManager em = entityManager.get();
            boolean set = DbTenantContext.setTenantLocal(em, tenantId);
            if (set) {
                LOG.debugf("Set Postgres RLS GUC %s=%s",
                        DbTenantContext.getSessionVariableName(), tenantId);
            } else {
                LOG.debug("Postgres RLS GUC set failed — app-level isolation still active");
            }
        } catch (Exception e) {
            // Never fail the request because of RLS session binding
            LOG.debugf(e, "Skipping RLS GUC set: %s", e.getMessage());
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