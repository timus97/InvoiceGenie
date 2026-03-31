package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.adapter.persistence.entity.CreditNoteEntity;
import com.invoicegenie.ar.adapter.persistence.mapper.CreditNoteMapper;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.payment.CreditNote;
import com.invoicegenie.ar.domain.model.payment.CreditNoteRepository;
import com.invoicegenie.shared.domain.TenantId;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Driven adapter: implements CreditNoteRepository port using JPA.
 * 
 * <p>Enforces tenant isolation on all queries.
 * <p>Credit note lifecycle: ISSUED → APPLIED (or EXPIRED/VOIDED)
 */
@ApplicationScoped
public class CreditNoteRepositoryAdapter implements CreditNoteRepository {

    @PersistenceContext
    EntityManager em;

    private final CreditNoteMapper mapper = new CreditNoteMapper();

    @Override
    @Transactional
    public void save(TenantId tenantId, CreditNote creditNote) {
        CreditNoteEntity entity = mapper.toEntity(tenantId, creditNote);
        em.merge(entity);
    }

    @Override
    public Optional<CreditNote> findByTenantAndId(TenantId tenantId, UUID id) {
        CreditNoteEntity e = em.find(CreditNoteEntity.class, id);
        if (e == null || !e.getTenantId().equals(tenantId.getValue())) {
            return Optional.empty();
        }
        return Optional.of(mapper.toDomain(e));
    }

    @Override
    public List<CreditNote> findByTenantAndCustomer(TenantId tenantId, CustomerId customerId) {
        return em.createQuery(
                        "SELECT c FROM CreditNoteEntity c WHERE c.tenantId = :tenantId AND c.customerId = :customerId",
                        CreditNoteEntity.class)
                .setParameter("tenantId", tenantId.getValue())
                .setParameter("customerId", customerId.getValue())
                .getResultStream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<CreditNote> findByTenantAndStatus(TenantId tenantId, CreditNote.CreditNoteStatus status) {
        CreditNoteEntity.CreditNoteStatus entityStatus = toEntityStatus(status);
        return em.createQuery(
                        "SELECT c FROM CreditNoteEntity c WHERE c.tenantId = :tenantId AND c.status = :status",
                        CreditNoteEntity.class)
                .setParameter("tenantId", tenantId.getValue())
                .setParameter("status", entityStatus)
                .getResultStream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<CreditNote> findAvailableByTenantAndCustomer(TenantId tenantId, CustomerId customerId) {
        return em.createQuery(
                        "SELECT c FROM CreditNoteEntity c WHERE c.tenantId = :tenantId AND c.customerId = :customerId " +
                        "AND c.status = :status AND (c.expiryDate IS NULL OR c.expiryDate >= :today)",
                        CreditNoteEntity.class)
                .setParameter("tenantId", tenantId.getValue())
                .setParameter("customerId", customerId.getValue())
                .setParameter("status", CreditNoteEntity.CreditNoteStatus.ISSUED)
                .setParameter("today", java.time.LocalDate.now())
                .getResultStream()
                .map(mapper::toDomain)
                .toList();
    }

    private CreditNoteEntity.CreditNoteStatus toEntityStatus(CreditNote.CreditNoteStatus status) {
        return switch (status) {
            case ISSUED -> CreditNoteEntity.CreditNoteStatus.ISSUED;
            case APPLIED -> CreditNoteEntity.CreditNoteStatus.APPLIED;
            case EXPIRED -> CreditNoteEntity.CreditNoteStatus.EXPIRED;
            case VOIDED -> CreditNoteEntity.CreditNoteStatus.VOIDED;
        };
    }
}
