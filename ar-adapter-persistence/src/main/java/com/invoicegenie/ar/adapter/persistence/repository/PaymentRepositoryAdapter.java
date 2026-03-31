package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.adapter.persistence.entity.PaymentAllocationEntity;
import com.invoicegenie.ar.adapter.persistence.entity.PaymentEntity;
import com.invoicegenie.ar.adapter.persistence.mapper.PaymentMapper;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.payment.Payment;
import com.invoicegenie.ar.domain.model.payment.PaymentAllocation;
import com.invoicegenie.ar.domain.model.payment.PaymentId;
import com.invoicegenie.ar.domain.model.payment.PaymentRepository;
import com.invoicegenie.shared.domain.TenantId;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;

import com.invoicegenie.shared.domain.Money;

/**
 * Driven adapter: implements PaymentRepository port.
 */
@ApplicationScoped
public class PaymentRepositoryAdapter implements PaymentRepository {

    @PersistenceContext
    EntityManager em;

    private final PaymentMapper mapper = new PaymentMapper();

    @Override
    @Transactional
    public void save(TenantId tenantId, Payment payment) {
        PaymentEntity entity = mapper.toEntity(tenantId, payment);
        em.merge(entity);

        em.createQuery("DELETE FROM PaymentAllocationEntity a WHERE a.tenantId = :tenantId AND a.paymentId = :paymentId")
                .setParameter("tenantId", tenantId.getValue())
                .setParameter("paymentId", payment.getId().getValue())
                .executeUpdate();

        for (PaymentAllocationEntity allocation : mapper.toAllocationEntities(tenantId, payment)) {
            em.persist(allocation);
        }
    }

    @Override
    public Optional<Payment> findByTenantAndId(TenantId tenantId, PaymentId id) {
        PaymentEntity e = em.find(PaymentEntity.class, id.getValue());
        if (e == null || !e.getTenantId().equals(tenantId.getValue())) {
            return Optional.empty();
        }
        List<PaymentAllocationEntity> allocations = loadAllocations(tenantId, id);
        return Optional.of(mapper.toDomain(e, allocations));
    }

    @Override
    public Optional<Payment> findByTenantAndNumber(TenantId tenantId, String paymentNumber) {
        return em.createQuery("SELECT p FROM PaymentEntity p WHERE p.tenantId = :tenantId AND p.paymentNumber = :number",
                        PaymentEntity.class)
                .setParameter("tenantId", tenantId.getValue())
                .setParameter("number", paymentNumber)
                .getResultStream()
                .findFirst()
                .map(p -> mapper.toDomain(p, loadAllocations(tenantId, PaymentId.of(p.getId()))));
    }

    @Override
    public List<Payment> findUnallocatedByTenantAndCustomer(TenantId tenantId, CustomerId customerId) {
        List<PaymentEntity> payments = em.createQuery(
                        "SELECT p FROM PaymentEntity p WHERE p.tenantId = :tenantId AND p.customerId = :customerId AND p.amountUnallocated > 0",
                        PaymentEntity.class)
                .setParameter("tenantId", tenantId.getValue())
                .setParameter("customerId", customerId.getValue())
                .getResultList();

        return payments.stream()
                .map(p -> mapper.toDomain(p, loadAllocations(tenantId, PaymentId.of(p.getId()))))
                .toList();
    }

    @Override
    public List<PaymentAllocation> findAllocationsByTenantAndInvoice(TenantId tenantId, InvoiceId invoiceId) {
        return em.createQuery(
                        "SELECT a FROM PaymentAllocationEntity a WHERE a.tenantId = :tenantId AND a.invoiceId = :invoiceId ORDER BY a.allocatedAt",
                        PaymentAllocationEntity.class)
                .setParameter("tenantId", tenantId.getValue())
                .setParameter("invoiceId", invoiceId.getValue())
                .getResultStream()
                .map(a -> new PaymentAllocation(
                        a.getId(),
                        InvoiceId.of(a.getInvoiceId()),
                        Money.of(a.getAmount(), a.getCurrency()),
                        a.getAllocatedAt(),
                        a.getAllocatedBy(),
                        a.getNotes()
                ))
                .toList();
    }

    private List<PaymentAllocationEntity> loadAllocations(TenantId tenantId, PaymentId paymentId) {
        return em.createQuery(
                        "SELECT a FROM PaymentAllocationEntity a WHERE a.tenantId = :tenantId AND a.paymentId = :paymentId ORDER BY a.allocatedAt",
                        PaymentAllocationEntity.class)
                .setParameter("tenantId", tenantId.getValue())
                .setParameter("paymentId", paymentId.getValue())
                .getResultList();
    }
}
