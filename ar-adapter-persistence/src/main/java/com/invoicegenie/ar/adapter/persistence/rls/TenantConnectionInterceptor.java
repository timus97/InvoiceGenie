package com.invoicegenie.ar.adapter.persistence.rls;

import com.invoicegenie.shared.domain.TenantId;
import com.invoicegenie.shared.tenant.DbTenantContext;
import com.invoicegenie.shared.tenant.TenantContext;
import io.agroal.api.AgroalPoolInterceptor;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Re-binds PostgreSQL RLS GUC on every connection checkout from the pool.
 *
 * <p>Prevents tenant leakage when Agroal reuses physical connections across
 * requests. Application-level tenant filters remain authoritative; this hardens
 * DB-side RLS under pooling (P1-07).
 */
@ApplicationScoped
public class TenantConnectionInterceptor implements AgroalPoolInterceptor {

    private static final Logger LOG = Logger.getLogger(TenantConnectionInterceptor.class);

    @ConfigProperty(name = "quarkus.datasource.db-kind", defaultValue = "postgresql")
    String dbKind;

    @Override
    public void onConnectionAcquire(Connection connection) {
        if (!DbTenantContext.isPostgresDbKind(dbKind)) {
            return;
        }
        TenantId tenant = TenantContext.getCurrentTenantOrNull();
        if (tenant == null) {
            return;
        }
        try {
            DbTenantContext.setTenant(connection, tenant);
            LOG.debugf("Bound RLS GUC on acquire for tenant %s", tenant);
        } catch (SQLException e) {
            LOG.debugf(e, "Failed to set RLS GUC on connection acquire: %s", e.getMessage());
        }
    }

    @Override
    public void onConnectionReturn(Connection connection) {
        if (!DbTenantContext.isPostgresDbKind(dbKind)) {
            return;
        }
        try {
            DbTenantContext.clearTenant(connection);
        } catch (SQLException e) {
            LOG.debugf(e, "Failed to clear RLS GUC on connection return: %s", e.getMessage());
        }
    }
}