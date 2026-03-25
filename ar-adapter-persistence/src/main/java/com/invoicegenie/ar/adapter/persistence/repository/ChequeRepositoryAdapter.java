package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.adapter.persistence.entity.ChequeEntity;
import com.invoicegenie.ar.adapter.persistence.mapper.ChequeMapper;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.payment.Cheque;
import com.invoicegenie.ar.domain.model.payment.ChequeRepository;
import com.invoicegenie.ar.domain.model.payment.ChequeStatus;
import com.invoicegenie.shared.domain.TenantId;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Driven adapter: implements ChequeRepository port using JPA.
 * 
 * <p>Enforces tenant isolation on all queries.
 * <p>Cheque lifecycle: RECEIVED → DEPOSITED → CLEARED or BOUNCED
 */
@ApplicationScoped
public class ChequeRepositoryAdapter implements ChequeRepository {

    @PersistenceContext
    EntityManager em;

    private final ChequeMapper mapper = new ChequeMapper();

    @Override
    @Transactional
    public void save(TenantId tenantId, Cheque cheque) {
        ChequeEntity entity = mapper.toEntity(tenantId, cheque);
        em.merge(entity);
    }

    @Override
    public Optional<Cheque> findByTenantAndId(TenantId tenantId, UUID id) {
        ChequeEntity e = em.find(ChequeEntity.class, id);
        if (e == null || !e.getTenantId().equals(tenantId.getValue())) {
            return Optional.empty();
        }
        return Optional.of(mapper.toDomain(e));
    }

    @Override
    public Optional<Cheque> findByTenantAndChequeNumber(TenantId tenantId, String chequeNumber) {
        return em.createQuery(
                        "SELECT c FROM ChequeEntity c WHERE c.tenantId = :tenantId AND c.chequeNumber = :number",
                        ChequeEntity.class)
                .setParameter("tenantId", tenantId.getValue())
                .setParameter("number", chequeNumber)
                .getResultStream()
                .findFirst()
                .map(mapper::toDomain);
    }

    @Override
    public List<Cheque> findByTenantAndCustomer(TenantId tenantId, CustomerId customerId) {
        return em.createQuery(
                        "SELECT c FROM ChequeEntity c WHERE c.tenantId = :tenantId AND c.customerId = :customerId",
                        ChequeEntity.class)
                .setParameter("tenantId", tenantId.getValue())
                .setParameter("customerId", customerId.getValue())
                .getResultStream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Cheque> findByTenantAndStatus(TenantId tenantId, ChequeStatus status) {
        return em.createQuery(
                        "SELECT c FROM ChequeEntity c WHERE c.tenantId = :tenantId AND c.status = :status",
                        ChequeEntity.class)
                .setParameter("tenantId", tenantId.getValue())
                .setParameter("status", status)
                .getResultStream()
                .map(mapper::toDomain)
                .toList();
    }
}
