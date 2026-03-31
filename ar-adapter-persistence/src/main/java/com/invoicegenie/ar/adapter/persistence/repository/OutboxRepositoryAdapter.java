package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.adapter.persistence.entity.OutboxEntity;
import com.invoicegenie.ar.domain.model.outbox.OutboxEntry;
import com.invoicegenie.ar.domain.model.outbox.OutboxRepository;
import com.invoicegenie.ar.domain.model.outbox.OutboxStatus;
import com.invoicegenie.shared.domain.TenantId;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Driven adapter: implements OutboxRepository port using JPA.
 * 
 * <p>Handles persistence of outbox entries for the transactional outbox pattern.
 * All operations are tenant-aware.
 */
@ApplicationScoped
public class OutboxRepositoryAdapter implements OutboxRepository {

    @PersistenceContext
    EntityManager em;

    @Override
    @Transactional
    public void save(TenantId tenantId, OutboxEntry entry) {
        OutboxEntity entity = toEntity(tenantId, entry);
        em.persist(entity);
    }

    @Override
    public List<OutboxEntry> findPending(int limit) {
        List<OutboxEntity> entities = em.createQuery(
                "SELECT e FROM OutboxEntity e WHERE e.status = :status " +
                "ORDER BY e.createdAt ASC",
                OutboxEntity.class)
                .setParameter("status", OutboxEntity.OutboxStatus.PENDING)
                .setMaxResults(limit)
                .getResultList();
        
        return entities.stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<OutboxEntry> findById(UUID id) {
        OutboxEntity entity = em.find(OutboxEntity.class, id);
        return Optional.ofNullable(entity).map(this::toDomain);
    }

    @Override
    @Transactional
    public void update(OutboxEntry entry) {
        OutboxEntity entity = em.find(OutboxEntity.class, entry.getId());
        if (entity != null) {
            updateEntity(entity, entry);
            em.merge(entity);
        }
    }

    @Override
    @Transactional
    public int deletePublishedOlderThan(Instant olderThan) {
        return em.createQuery(
                "DELETE FROM OutboxEntity e WHERE e.status = :status AND e.publishedAt < :olderThan")
                .setParameter("status", OutboxEntity.OutboxStatus.PUBLISHED)
                .setParameter("olderThan", olderThan)
                .executeUpdate();
    }

    @Override
    public long countByStatus(OutboxStatus status) {
        return em.createQuery(
                "SELECT COUNT(e) FROM OutboxEntity e WHERE e.status = :status",
                Long.class)
                .setParameter("status", toEntityStatus(status))
                .getSingleResult();
    }

    // ==================== Mapping ====================

    private OutboxEntity toEntity(TenantId tenantId, OutboxEntry entry) {
        return new OutboxEntity(
                entry.getId(),
                tenantId.getValue(),
                entry.getAggregateType(),
                entry.getAggregateId(),
                entry.getEventType(),
                entry.getPayload()
        );
    }

    private OutboxEntry toDomain(OutboxEntity entity) {
        return new OutboxEntry(
                entity.getId(),
                TenantId.of(entity.getTenantId()),
                entity.getAggregateType(),
                entity.getAggregateId(),
                entity.getEventType(),
                entity.getPayload(),
                entity.getCreatedAt(),
                entity.getPublishedAt(),
                toDomainStatus(entity.getStatus()),
                entity.getRetryCount(),
                entity.getLastError()
        );
    }

    private void updateEntity(OutboxEntity entity, OutboxEntry entry) {
        entity.setStatus(toEntityStatus(entry.getStatus()));
        entity.setPublishedAt(entry.getPublishedAt());
        entity.setRetryCount(entry.getRetryCount());
        entity.setLastError(entry.getLastError());
    }

    private OutboxStatus toDomainStatus(OutboxEntity.OutboxStatus status) {
        return switch (status) {
            case PENDING -> OutboxStatus.PENDING;
            case PROCESSING -> OutboxStatus.PROCESSING;
            case PUBLISHED -> OutboxStatus.PUBLISHED;
            case FAILED -> OutboxStatus.FAILED;
        };
    }

    private OutboxEntity.OutboxStatus toEntityStatus(OutboxStatus status) {
        return switch (status) {
            case PENDING -> OutboxEntity.OutboxStatus.PENDING;
            case PROCESSING -> OutboxEntity.OutboxStatus.PROCESSING;
            case PUBLISHED -> OutboxEntity.OutboxStatus.PUBLISHED;
            case FAILED -> OutboxEntity.OutboxStatus.FAILED;
        };
    }
}
