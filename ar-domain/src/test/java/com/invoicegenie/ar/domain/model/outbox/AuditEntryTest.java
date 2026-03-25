package com.invoicegenie.ar.domain.model.outbox;

import com.invoicegenie.shared.domain.TenantId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuditEntryTest {

    @Test
    void create_InitializesEntry() {
        TenantId tenantId = TenantId.of(UUID.randomUUID());
        UUID entityId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        
        AuditEntry entry = AuditEntry.create(tenantId, "INVOICE", entityId, "INV-001", actorId, "{\"amount\":100}");
        
        assertNotNull(entry.getId());
        assertEquals(tenantId, entry.getTenantId());
        assertEquals("INVOICE", entry.getEntityType());
        assertEquals(entityId, entry.getEntityId());
        assertEquals("INV-001", entry.getEntityRef());
        assertEquals("CREATE", entry.getAction());
        assertEquals(actorId, entry.getActorId());
        assertEquals("USER", entry.getActorType());
        assertNull(entry.getBeforeState());
        assertEquals("{\"amount\":100}", entry.getAfterState());
        assertNotNull(entry.getCreatedAt());
    }

    @Test
    void update_InitializesEntryWithBeforeAndAfter() {
        TenantId tenantId = TenantId.of(UUID.randomUUID());
        UUID entityId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        
        AuditEntry entry = AuditEntry.update(tenantId, "INVOICE", entityId, "INV-001", actorId, 
            "{\"amount\":100}", "{\"amount\":150}");
        
        assertEquals("UPDATE", entry.getAction());
        assertEquals("{\"amount\":100}", entry.getBeforeState());
        assertEquals("{\"amount\":150}", entry.getAfterState());
    }

    @Test
    void delete_InitializesEntryWithBeforeState() {
        TenantId tenantId = TenantId.of(UUID.randomUUID());
        UUID entityId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        
        AuditEntry entry = AuditEntry.delete(tenantId, "INVOICE", entityId, "INV-001", actorId, 
            "{\"amount\":100}");
        
        assertEquals("DELETE", entry.getAction());
        assertEquals("{\"amount\":100}", entry.getBeforeState());
        assertNull(entry.getAfterState());
    }

    @Test
    void transition_UsesProvidedAction() {
        TenantId tenantId = TenantId.of(UUID.randomUUID());
        UUID entityId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        
        AuditEntry entry = AuditEntry.transition(tenantId, "INVOICE", entityId, "INV-001", actorId,
            "MARK_OVERDUE", "{\"status\":\"ISSUED\"}", "{\"status\":\"OVERDUE\"}");
        
        assertEquals("MARK_OVERDUE", entry.getAction());
        assertEquals("{\"status\":\"ISSUED\"}", entry.getBeforeState());
        assertEquals("{\"status\":\"OVERDUE\"}", entry.getAfterState());
    }

    @Test
    void constructor_ThrowsIfEntityTypeBlank() {
        TenantId tenantId = TenantId.of(UUID.randomUUID());
        UUID id = UUID.randomUUID();
        
        assertThrows(IllegalArgumentException.class, () -> 
            new AuditEntry(id, tenantId, "", UUID.randomUUID(), null, "CREATE", 
                UUID.randomUUID(), "USER", null, null, null, null, null));
    }

    @Test
    void constructor_ThrowsIfActionBlank() {
        TenantId tenantId = TenantId.of(UUID.randomUUID());
        UUID id = UUID.randomUUID();
        
        assertThrows(IllegalArgumentException.class, () -> 
            new AuditEntry(id, tenantId, "INVOICE", UUID.randomUUID(), null, "", 
                UUID.randomUUID(), "USER", null, null, null, null, null));
    }

    @Test
    void equals_SameId() {
        UUID id = UUID.randomUUID();
        TenantId tenantId = TenantId.of(UUID.randomUUID());
        
        AuditEntry e1 = new AuditEntry(id, tenantId, "INVOICE", UUID.randomUUID(), null,
            "CREATE", UUID.randomUUID(), "USER", null, null, null, null, null);
        AuditEntry e2 = new AuditEntry(id, tenantId, "INVOICE", UUID.randomUUID(), null,
            "CREATE", UUID.randomUUID(), "USER", null, null, null, null, null);
        
        assertEquals(e1, e2);
        assertEquals(e1.hashCode(), e2.hashCode());
    }

    @Test
    void equals_DifferentId() {
        TenantId tenantId = TenantId.of(UUID.randomUUID());
        
        AuditEntry e1 = new AuditEntry(UUID.randomUUID(), tenantId, "INVOICE", UUID.randomUUID(), null,
            "CREATE", UUID.randomUUID(), "USER", null, null, null, null, null);
        AuditEntry e2 = new AuditEntry(UUID.randomUUID(), tenantId, "INVOICE", UUID.randomUUID(), null,
            "CREATE", UUID.randomUUID(), "USER", null, null, null, null, null);
        
        assertNotEquals(e1, e2);
    }
}
