package com.invoicegenie.shared.tenant;

import com.invoicegenie.shared.domain.TenantId;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Utility for setting PostgreSQL RLS (Row Level Security) session variable.
 *
 * <p>PostgreSQL RLS policies defined in the schema expect the session variable
 * <code>app.current_tenant_id</code> to be set. This utility provides methods
 * to set and clear this variable on a JDBC connection or JPA EntityManager.
 *
 * <p>Callers should only invoke EntityManager helpers when running against
 * PostgreSQL. On H2, skip the call (see {@link #isPostgresDbKind(String)}) so
 * failed native SQL does not mark the JPA transaction rollback-only.
 */
public final class DbTenantContext {

    private static final String SESSION_VAR = "app.current_tenant_id";

    private DbTenantContext() {}

    /**
     * Sets the tenant ID session variable on the given JDBC connection.
     *
     * @param connection JDBC connection
     * @param tenantId the tenant ID to set
     * @throws SQLException if setting the variable fails
     */
    public static void setTenant(Connection connection, TenantId tenantId) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET " + SESSION_VAR + " = '" + tenantId.getValue().toString() + "'");
        }
    }

    /**
     * Clears the tenant ID session variable on the given JDBC connection.
     *
     * @param connection JDBC connection
     * @throws SQLException if clearing the variable fails
     */
    public static void clearTenant(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("RESET " + SESSION_VAR);
        }
    }

    /**
     * Gets the current tenant ID from the session variable.
     *
     * @param connection JDBC connection
     * @return the current tenant ID string, or null if not set
     * @throws SQLException if querying fails
     */
    public static String getCurrentTenant(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery("SELECT current_setting('" + SESSION_VAR + "', true)")) {
            if (rs.next()) {
                return rs.getString(1);
            }
            return null;
        }
    }

    /**
     * Returns true when the configured DB kind is PostgreSQL.
     *
     * @param dbKind value of {@code quarkus.datasource.db-kind} (may be null)
     */
    public static boolean isPostgresDbKind(String dbKind) {
        if (dbKind == null || dbKind.isBlank()) {
            return false;
        }
        String k = dbKind.trim().toLowerCase();
        return k.equals("postgresql") || k.equals("pgsql") || k.equals("postgres");
    }

    /**
     * Sets {@code app.current_tenant_id} for the current Postgres transaction via
     * {@code SET LOCAL}. Safe no-op when {@code entityManager} is null or not an
     * EntityManager; callers should gate on {@link #isPostgresDbKind(String)}.
     *
     * @param entityManager JPA EntityManager (may be null — ignored)
     * @param tenantId tenant to bind
     * @return true if the GUC was set successfully, false if skipped/failed
     */
    public static boolean setTenantLocal(Object entityManager, TenantId tenantId) {
        if (entityManager == null || tenantId == null) {
            return false;
        }
        if (!(entityManager instanceof jakarta.persistence.EntityManager jpaEm)) {
            return false;
        }
        String uuid = tenantId.getValue().toString();
        // Prefer set_config(..., true) for transaction-local scope; fall back to SET LOCAL.
        try {
            jpaEm.createNativeQuery("SELECT set_config('" + SESSION_VAR + "', :tid, true)")
                    .setParameter("tid", uuid)
                    .getSingleResult();
            return true;
        } catch (Exception primary) {
            try {
                jpaEm.createNativeQuery("SET LOCAL " + SESSION_VAR + " = '" + uuid + "'")
                        .executeUpdate();
                return true;
            } catch (Exception secondary) {
                return false;
            }
        }
    }

    /**
     * Clears {@code app.current_tenant_id} via EntityManager.
     *
     * @param entityManager JPA EntityManager (may be null — ignored)
     * @return true if cleared successfully
     */
    public static boolean clearTenant(Object entityManager) {
        if (entityManager == null) {
            return false;
        }
        if (!(entityManager instanceof jakarta.persistence.EntityManager jpaEm)) {
            return false;
        }
        try {
            jpaEm.createNativeQuery("SELECT set_config('" + SESSION_VAR + "', '', true)")
                    .getSingleResult();
            return true;
        } catch (Exception primary) {
            try {
                jpaEm.createNativeQuery("RESET " + SESSION_VAR).executeUpdate();
                return true;
            } catch (Exception secondary) {
                return false;
            }
        }
    }

    /**
     * Gets the session variable name used for tenant context.
     */
    public static String getSessionVariableName() {
        return SESSION_VAR;
    }
}