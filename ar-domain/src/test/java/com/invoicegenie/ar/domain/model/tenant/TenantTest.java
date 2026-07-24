package com.invoicegenie.ar.domain.model.tenant;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TenantTest {

    @Test
    void createActiveTenant() {
        Tenant t = new Tenant(UUID.randomUUID(), "acme", "Acme Corp", "usd");
        assertEquals("ACME", t.getCode());
        assertEquals("USD", t.getBaseCurrency());
        assertTrue(t.isActive());
    }

    @Test
    void suspendAndActivate() {
        Tenant t = new Tenant(UUID.randomUUID(), "X", "X Co", "EUR");
        t.suspend();
        assertEquals(TenantStatus.SUSPENDED, t.getStatus());
        t.activate();
        assertTrue(t.isActive());
    }

    @Test
    void rejectsBlankCode() {
        assertThrows(IllegalArgumentException.class,
                () -> new Tenant(UUID.randomUUID(), " ", "Name", "USD"));
    }
}