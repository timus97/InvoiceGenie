package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.adapter.persistence.entity.AuditLogEntity;
import com.invoicegenie.ar.domain.model.outbox.AuditEntry;
import com.invoicegenie.shared.domain.TenantId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditRepositoryAdapterTest {

    private AuditRepositoryAdapter adapter;
    private EntityManager em;

    @BeforeEach
    void setUp() {
        adapter = new AuditRepositoryAdapter();
        em = mock(EntityManager.class);
        adapter.em = em;
    }

    @Test
    void save_PersistsEntity() {
        TenantId tenantId = TenantId.of(UUID.randomUUID());
        AuditEntry entry = AuditEntry.create(tenantId, "INVOICE", UUID.randomUUID(), "INV-1", UUID.randomUUID(), "{}");

        adapter.save(tenantId, entry);

        verify(em).persist(any(AuditLogEntity.class));
    }

    @Test
    void findByTenantAndId_ReturnsEntry_WhenFoundAndTenantMatches() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        AuditLogEntity entity = new AuditLogEntity();
        entity.setId(id);
        entity.setTenantId(tenantId);
        entity.setEntityType("INVOICE");
        entity.setEntityId(UUID.randomUUID());
        entity.setAction("CREATE");

        when(em.find(AuditLogEntity.class, id)).thenReturn(entity);

        Optional<AuditEntry> result = adapter.findByTenantAndId(TenantId.of(tenantId), id);

        assertTrue(result.isPresent());
        assertEquals("INVOICE", result.get().getEntityType());
    }

    @Test
    void findByTenantAndId_ReturnsEmpty_WhenNotFound() {
        UUID id = UUID.randomUUID();
        when(em.find(AuditLogEntity.class, id)).thenReturn(null);

        Optional<AuditEntry> result = adapter.findByTenantAndId(TenantId.of(UUID.randomUUID()), id);

        assertTrue(result.isEmpty());
    }

    @Test
    void findByTenantAndId_ReturnsEmpty_ForDifferentTenant() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID differentTenantId = UUID.randomUUID();

        AuditLogEntity entity = new AuditLogEntity();
        entity.setId(id);
        entity.setTenantId(differentTenantId);

        when(em.find(AuditLogEntity.class, id)).thenReturn(entity);

        Optional<AuditEntry> result = adapter.findByTenantAndId(TenantId.of(tenantId), id);

        assertTrue(result.isEmpty());
    }

    @Test
    void findByTenantAndEntity_ReturnsMatchingEntries() {
        TenantId tenantId = TenantId.of(UUID.randomUUID());
        UUID entityId = UUID.randomUUID();

        AuditLogEntity entity = new AuditLogEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(tenantId.getValue());
        entity.setEntityType("INVOICE");
        entity.setEntityId(entityId);
        entity.setAction("CREATE");

        when(em.createQuery(anyString(), eq(AuditLogEntity.class))).thenReturn(mock(jakarta.persistence.TypedQuery.class));
        jakarta.persistence.TypedQuery<AuditLogEntity> query = mock(jakarta.persistence.TypedQuery.class);
        when(em.createQuery(anyString(), eq(AuditLogEntity.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultStream()).thenReturn(java.util.stream.Stream.of(entity));

        List<AuditEntry> result = adapter.findByTenantAndEntity(tenantId, "INVOICE", entityId);

        assertEquals(1, result.size());
    }

    @Test
    void findRecentByTenant_ReturnsLimitedEntries() {
        TenantId tenantId = TenantId.of(UUID.randomUUID());

        AuditLogEntity entity = new AuditLogEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(tenantId.getValue());
        entity.setEntityType("PAYMENT");
        entity.setAction("CREATE");

        jakarta.persistence.TypedQuery<AuditLogEntity> query = mock(jakarta.persistence.TypedQuery.class);
        when(em.createQuery(anyString(), eq(AuditLogEntity.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);
        when(query.getResultStream()).thenReturn(java.util.stream.Stream.of(entity));

        List<AuditEntry> result = adapter.findRecentByTenant(tenantId, 10);

        assertEquals(1, result.size());
        verify(query).setMaxResults(10);
    }
}
