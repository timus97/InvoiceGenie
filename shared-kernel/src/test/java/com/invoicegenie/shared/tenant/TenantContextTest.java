package com.invoicegenie.shared.tenant;

import com.invoicegenie.shared.domain.TenantId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TenantContext")
class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("Setting and Getting Tenant")
    class SetAndGetTenant {

        @Test
        @DisplayName("should set and get current tenant")
        void shouldSetAndGetTenant() {
            TenantId tenantId = TenantId.of(UUID.randomUUID());
            
            TenantContext.setCurrentTenant(tenantId);
            
            assertEquals(tenantId, TenantContext.getCurrentTenant());
        }

        @Test
        @DisplayName("should allow changing tenant")
        void shouldAllowChangingTenant() {
            TenantId tenant1 = TenantId.of(UUID.randomUUID());
            TenantId tenant2 = TenantId.of(UUID.randomUUID());
            
            TenantContext.setCurrentTenant(tenant1);
            TenantContext.setCurrentTenant(tenant2);
            
            assertEquals(tenant2, TenantContext.getCurrentTenant());
        }
    }

    @Nested
    @DisplayName("Clearing Tenant")
    class ClearTenant {

        @Test
        @DisplayName("should clear tenant context")
        void shouldClearTenantContext() {
            TenantId tenantId = TenantId.of(UUID.randomUUID());
            TenantContext.setCurrentTenant(tenantId);
            
            TenantContext.clear();
            
            assertThrows(IllegalStateException.class, TenantContext::getCurrentTenant);
        }
    }

    @Nested
    @DisplayName("Getting Tenant When Not Set")
    class GetTenantWhenNotSet {

        @Test
        @DisplayName("should throw when tenant not set")
        void shouldThrowWhenTenantNotSet() {
            IllegalStateException ex = assertThrows(IllegalStateException.class, TenantContext::getCurrentTenant);
            assertTrue(ex.getMessage().contains("TenantContext not set"));
        }
    }
}
