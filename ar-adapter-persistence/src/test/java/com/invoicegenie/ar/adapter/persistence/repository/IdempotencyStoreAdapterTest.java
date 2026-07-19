package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.adapter.persistence.entity.IdempotencyEntity;
import com.invoicegenie.ar.application.port.outbound.IdempotencyStore;
import com.invoicegenie.shared.domain.TenantId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class IdempotencyStoreAdapterTest {

    private IdempotencyStoreAdapter adapter;
    private EntityManager em;
    private TenantId tenantId;

    @BeforeEach
    void setUp() {
        adapter = new IdempotencyStoreAdapter();
        em = mock(EntityManager.class);
        adapter.em = em;
        tenantId = TenantId.of(UUID.randomUUID());
    }

    @Test
    void find_ReturnsEmpty_WhenNotFound() {
        when(em.find(eq(IdempotencyEntity.class), any(IdempotencyEntity.Pk.class))).thenReturn(null);
        assertTrue(adapter.find(tenantId, "key-1").isEmpty());
    }

    @Test
    void find_ReturnsRecord_WhenFound() {
        IdempotencyEntity entity = new IdempotencyEntity(
                tenantId.getValue(), "key-1", "hash-abc", "{\"id\":\"x\"}", Instant.now());
        when(em.find(eq(IdempotencyEntity.class), any(IdempotencyEntity.Pk.class))).thenReturn(entity);

        Optional<IdempotencyStore.IdempotencyRecord> result = adapter.find(tenantId, "key-1");
        assertTrue(result.isPresent());
        assertEquals("key-1", result.get().key());
        assertEquals("hash-abc", result.get().requestHash());
        assertEquals("{\"id\":\"x\"}", result.get().responseJson());
    }

    @Test
    void put_PersistsNewEntity() {
        when(em.find(eq(IdempotencyEntity.class), any(IdempotencyEntity.Pk.class))).thenReturn(null);
        adapter.put(tenantId, "key-2", "hash-1", "response");
        verify(em).persist(any(IdempotencyEntity.class));
    }

    @Test
    void put_MergesExistingEntity() {
        IdempotencyEntity existing = new IdempotencyEntity(
                tenantId.getValue(), "key-3", "old-hash", "old", Instant.now());
        when(em.find(eq(IdempotencyEntity.class), any(IdempotencyEntity.Pk.class))).thenReturn(existing);

        adapter.put(tenantId, "key-3", "new-hash", "new-response");

        verify(em).merge(existing);
        assertEquals("new-hash", existing.getRequestHash());
        assertEquals("new-response", existing.getResponseJson());
        verify(em, never()).persist(any());
    }
}
