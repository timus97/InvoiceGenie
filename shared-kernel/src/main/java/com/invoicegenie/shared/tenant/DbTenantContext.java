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
 * to set and clear this variable on a JDBC connection.
 * 
 * <p>Usage in application services or repository adapters:
 * <pre>
 * try (Connection conn = dataSource.getConnection()) {
 *     DbTenantContext.setTenant(conn, TenantContext.getCurrentTenant());
 *     // Execute queries - RLS policies now enforce tenant isolation
 * }
 * </pre>
 * 
 * <p>For JPA EntityManager, use inline native queries:
 * <pre>
 * em.createNativeQuery("SELECT set_config('app.current_tenant_id', ?, true)")
 *     .setParameter(1, tenantId.getValue().toString())
 *     .getSingleResult();
 * </pre>
 */
public final class DbTenantContext {

    private static final String SESSION_VAR = "app.current_tenant_id";

    private DbTenantContext() {}

    /**
     * Sets the tenant ID session variable on the given JDBC connection.
     * This enables PostgreSQL RLS policies to enforce tenant isolation.
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
     * Gets the session variable name used for tenant context.
     */
    public static String getSessionVariableName() {
        return SESSION_VAR;
    }
}
