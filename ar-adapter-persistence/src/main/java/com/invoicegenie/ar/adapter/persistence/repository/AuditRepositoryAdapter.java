package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.adapter.persistence.entity.AuditLogEntity;
import com.invoicegenie.ar.domain.model.outbox.AuditEntry;
import com.invoicegenie.ar.domain.model.outbox.AuditRepository;
import com.invoicegenie.shared.domain.TenantId;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Driven adapter: implements AuditRepository using JPA.
 */
@ApplicationScoped
public class AuditRepositoryAdapter implements AuditRepository {

    @PersistenceContext
    EntityManager em;

    @Override
    @Transactional
    public void save(TenantId tenantId, AuditEntry entry) {
        AuditLogEntity entity = toEntity(tenantId, entry);
        em.persist(entity);
    }

    @Override
    public Optional<AuditEntry> findByTenantAndId(TenantId tenantId, UUID id) {
        AuditLogEntity entity = em.find(AuditLogEntity.class, id);
        if (entity == null || !entity.getTenantId().equals(tenantId.getValue())) {
            return Optional.empty();
        }
        return Optional.of(toDomain(entity));
    }

    @Override
    public List<AuditEntry> findByTenantAndEntity(TenantId tenantId, String entityType, UUID entityId) {
        return em.createQuery(
                "SELECT a FROM AuditLogEntity a WHERE a.tenantId = :tenantId " +
                "AND a.entityType = :entityType AND a.entityId = :entityId " +
                "ORDER BY a.createdAt DESC",
                AuditLogEntity.class)
            .setParameter("tenantId", tenantId.getValue())
            .setParameter("entityType", entityType)
            .setParameter("entityId", entityId)
            .getResultStream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<AuditEntry> findRecentByTenant(TenantId tenantId, int limit) {
        return em.createQuery(
                "SELECT a FROM AuditLogEntity a WHERE a.tenantId = :tenantId " +
                "ORDER BY a.createdAt DESC",
                AuditLogEntity.class)
            .setParameter("tenantId", tenantId.getValue())
            .setMaxResults(limit)
            .getResultStream()
            .map(this::toDomain)
            .toList();
    }

    private AuditLogEntity toEntity(TenantId tenantId, AuditEntry entry) {
        AuditLogEntity e = new AuditLogEntity();
        e.setId(entry.getId());
        e.setTenantId(tenantId.getValue());
        e.setEntityType(entry.getEntityType());
        e.setEntityId(entry.getEntityId());
        e.setEntityRef(entry.getEntityRef());
        e.setAction(entry.getAction());
        e.setActorId(entry.getActorId());
        e.setActorType(entry.getActorType());
        e.setBeforeState(entry.getBeforeState());
        e.setAfterState(entry.getAfterState());
        e.setIpAddress(entry.getIpAddress());
        e.setUserAgent(entry.getUserAgent());
        e.setCreatedAt(entry.getCreatedAt());
        return e;
    }

    private AuditEntry toDomain(AuditLogEntity e) {
        return new AuditEntry(
            e.getId(),
            TenantId.of(e.getTenantId()),
            e.getEntityType(),
            e.getEntityId(),
            e.getEntityRef(),
            e.getAction(),
            e.getActorId(),
            e.getActorType(),
            e.getBeforeState(),
            e.getAfterState(),
            e.getIpAddress(),
            e.getUserAgent(),
            e.getCreatedAt()
        );
    }
}
