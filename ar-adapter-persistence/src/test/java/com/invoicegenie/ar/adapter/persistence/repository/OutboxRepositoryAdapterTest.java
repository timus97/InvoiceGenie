package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.adapter.persistence.entity.OutboxEntity;
import com.invoicegenie.ar.domain.model.outbox.OutboxEntry;
import com.invoicegenie.ar.domain.model.outbox.OutboxStatus;
import com.invoicegenie.shared.domain.TenantId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OutboxRepositoryAdapterTest {

    private OutboxRepositoryAdapter adapter;
    private EntityManager em;

    @BeforeEach
    void setUp() {
        adapter = new OutboxRepositoryAdapter();
        em = mock(EntityManager.class);
        adapter.em = em;
    }

    @Test
    void savePersistsEntity() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        Instant now = Instant.now();

        OutboxEntry entry = new OutboxEntry(
            id, TenantId.of(tenantId), "Invoice", aggregateId,
            "InvoiceIssued", "{\"id\":\"" + aggregateId + "\"}",
            now, null, OutboxStatus.PENDING, 0, null
        );

        adapter.save(TenantId.of(tenantId), entry);

        verify(em).persist(any(OutboxEntity.class));
    }

    @Test
    void findPending_ReturnsListOfEntries() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        Instant now = Instant.now();

        OutboxEntity entity = new OutboxEntity(
            id, tenantId, "Payment", aggregateId,
            "PaymentRecorded", "{\"amount\":100}"
        );

        TypedQuery<OutboxEntity> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(OutboxEntity.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(entity));

        List<OutboxEntry> result = adapter.findPending(10);

        assertEquals(1, result.size());
        assertEquals(id, result.get(0).getId());
        assertEquals("Payment", result.get(0).getAggregateType());
    }

    @Test
    void findById_ReturnsEntry_WhenFound() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        Instant now = Instant.now();

        OutboxEntity entity = new OutboxEntity(
            id, tenantId, "Invoice", aggregateId,
            "InvoiceIssued", "{}"
        );

        when(em.find(OutboxEntity.class, id)).thenReturn(entity);

        Optional<OutboxEntry> result = adapter.findById(id);

        assertTrue(result.isPresent());
        assertEquals(id, result.get().getId());
    }

    @Test
    void findById_ReturnsEmpty_WhenNotFound() {
        UUID id = UUID.randomUUID();
        when(em.find(OutboxEntity.class, id)).thenReturn(null);

        Optional<OutboxEntry> result = adapter.findById(id);

        assertTrue(result.isEmpty());
    }

    @Test
    void update_UpdatesEntity_WhenFound() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        Instant now = Instant.now();

        OutboxEntity entity = new OutboxEntity(
            id, tenantId, "Invoice", aggregateId,
            "InvoiceIssued", "{}"
        );

        OutboxEntry entry = new OutboxEntry(
            id, TenantId.of(tenantId), "Invoice", aggregateId,
            "InvoiceIssued", "{}",
            now, now, OutboxStatus.PUBLISHED, 0, null
        );

        when(em.find(OutboxEntity.class, id)).thenReturn(entity);

        adapter.update(entry);

        verify(em).merge(any(OutboxEntity.class));
    }

    @Test
    void update_DoesNothing_WhenNotFound() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        Instant now = Instant.now();

        OutboxEntry entry = new OutboxEntry(
            id, TenantId.of(tenantId), "Invoice", aggregateId,
            "InvoiceIssued", "{}",
            now, now, OutboxStatus.PUBLISHED, 0, null
        );

        when(em.find(OutboxEntity.class, id)).thenReturn(null);

        adapter.update(entry);

        verify(em, never()).merge(any());
    }

    @Test
    void deletePublishedOlderThan_DeletesEntries() {
        Instant olderThan = Instant.now().minusSeconds(86400);

        Query query = mock(Query.class);
        when(em.createQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(5);

        int deleted = adapter.deletePublishedOlderThan(olderThan);

        assertEquals(5, deleted);
        verify(query).executeUpdate();
    }

    @Test
    void countByStatus_ReturnsCount() {
        TypedQuery<Long> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(10L);

        long count = adapter.countByStatus(OutboxStatus.PENDING);

        assertEquals(10L, count);
    }
}
