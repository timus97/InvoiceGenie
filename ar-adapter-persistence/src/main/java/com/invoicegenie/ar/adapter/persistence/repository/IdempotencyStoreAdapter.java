package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.adapter.persistence.entity.IdempotencyEntity;
import com.invoicegenie.ar.application.port.outbound.IdempotencyStore;
import com.invoicegenie.shared.domain.TenantId;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Optional;

/**
 * JPA-backed durable {@link IdempotencyStore}.
 */
@ApplicationScoped
public class IdempotencyStoreAdapter implements IdempotencyStore {

    @PersistenceContext
    EntityManager em;

    @Override
    public Optional<IdempotencyRecord> find(TenantId tenantId, String key) {
        IdempotencyEntity entity = em.find(
                IdempotencyEntity.class,
                new IdempotencyEntity.Pk(tenantId.getValue(), key));
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.of(new IdempotencyRecord(
                entity.getKey(),
                entity.getRequestHash(),
                entity.getResponseJson(),
                entity.getCreatedAt()));
    }

    @Override
    @Transactional
    public void put(TenantId tenantId, String key, String requestHash, String responseJson) {
        IdempotencyEntity existing = em.find(
                IdempotencyEntity.class,
                new IdempotencyEntity.Pk(tenantId.getValue(), key));
        if (existing != null) {
            existing.setRequestHash(requestHash);
            existing.setResponseJson(responseJson);
            em.merge(existing);
        } else {
            em.persist(new IdempotencyEntity(
                    tenantId.getValue(),
                    key,
                    requestHash,
                    responseJson,
                    Instant.now()));
        }
    }
}
