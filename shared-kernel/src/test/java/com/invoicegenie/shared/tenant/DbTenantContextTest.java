package com.invoicegenie.shared.tenant;

import com.invoicegenie.shared.domain.TenantId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

class DbTenantContextTest {

    @Test
    void setTenant_OnConnection_SetsSessionVariable() throws Exception {
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);

        when(conn.createStatement()).thenReturn(stmt);

        TenantId tenantId = TenantId.of("550e8400-e29b-41d4-a716-446655440000");

        DbTenantContext.setTenant(conn, tenantId);

        verify(stmt).execute(contains("SET app.current_tenant_id"));
    }

    @Test
    void clearTenant_OnConnection_ResetsSessionVariable() throws Exception {
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);

        when(conn.createStatement()).thenReturn(stmt);

        DbTenantContext.clearTenant(conn);

        verify(stmt).execute(contains("RESET app.current_tenant_id"));
    }

    @Test
    void getCurrentTenant_OnConnection_ReturnsValue() throws Exception {
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.executeQuery(contains("current_setting"))).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getString(1)).thenReturn("550e8400-e29b-41d4-a716-446655440000");

        String result = DbTenantContext.getCurrentTenant(conn);

        assertEquals("550e8400-e29b-41d4-a716-446655440000", result);
    }

    @Test
    void getCurrentTenant_OnConnection_ReturnsNullWhenNotSet() throws Exception {
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        ResultSet rs = mock(ResultSet.class);

        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.executeQuery(contains("current_setting"))).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getString(1)).thenReturn("");

        String result = DbTenantContext.getCurrentTenant(conn);

        assertEquals("", result);
    }

    @Test
    void getSessionVariableName_ReturnsCorrectName() {
        assertEquals("app.current_tenant_id", DbTenantContext.getSessionVariableName());
    }

    @Test
    void isPostgresDbKind_RecognizesPostgresVariants() {
        assertTrue(DbTenantContext.isPostgresDbKind("postgresql"));
        assertTrue(DbTenantContext.isPostgresDbKind("POSTGRES"));
        assertTrue(DbTenantContext.isPostgresDbKind("pgsql"));
        assertTrue(DbTenantContext.isPostgresDbKind("postgres"));
        assertFalse(DbTenantContext.isPostgresDbKind("h2"));
        assertFalse(DbTenantContext.isPostgresDbKind(null));
        assertFalse(DbTenantContext.isPostgresDbKind(""));
    }

    @Test
    void setTenantLocal_WithEntityManager_UsesSetConfig() {
        EntityManager em = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(em.createNativeQuery(contains("set_config"))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn("ok");

        TenantId tenantId = TenantId.of("550e8400-e29b-41d4-a716-446655440000");
        boolean result = DbTenantContext.setTenantLocal(em, tenantId);

        assertTrue(result);
        verify(em).createNativeQuery(contains("set_config"));
        verify(query).setParameter("tid", "550e8400-e29b-41d4-a716-446655440000");
    }

    @Test
    void setTenantLocal_OnH2StyleFailure_ReturnsFalse() {
        EntityManager em = mock(EntityManager.class);
        when(em.createNativeQuery(anyString())).thenThrow(new RuntimeException("Syntax error"));

        TenantId tenantId = TenantId.of("550e8400-e29b-41d4-a716-446655440000");
        boolean result = DbTenantContext.setTenantLocal(em, tenantId);

        assertFalse(result);
    }

    @Test
    void setTenantLocal_NullEntityManager_ReturnsFalse() {
        assertFalse(DbTenantContext.setTenantLocal(null, TenantId.of(java.util.UUID.randomUUID())));
    }

    @Test
    void clearTenant_WithEntityManager_UsesSetConfig() {
        EntityManager em = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(em.createNativeQuery(contains("set_config"))).thenReturn(query);
        when(query.getSingleResult()).thenReturn("");

        assertTrue(DbTenantContext.clearTenant(em));
    }

    @Test
    void clearTenant_OnH2_ReturnsFalse() {
        EntityManager em = mock(EntityManager.class);
        when(em.createNativeQuery(anyString())).thenThrow(new RuntimeException("not supported"));

        assertFalse(DbTenantContext.clearTenant(em));
    }
}