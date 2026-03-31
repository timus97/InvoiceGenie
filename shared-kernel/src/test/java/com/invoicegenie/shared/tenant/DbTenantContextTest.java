package com.invoicegenie.shared.tenant;

import com.invoicegenie.shared.domain.TenantId;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;
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
}
