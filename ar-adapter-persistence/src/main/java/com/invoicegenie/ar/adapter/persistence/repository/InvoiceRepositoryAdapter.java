package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.domain.model.invoice.InvoiceStatus;
import com.invoicegenie.ar.adapter.persistence.entity.InvoiceEntity;
import com.invoicegenie.shared.domain.TenantId;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Driven adapter: implements InvoiceRepository port. All queries include tenant_id.
 */
@ApplicationScoped
public class InvoiceRepositoryAdapter implements InvoiceRepository {

    @PersistenceContext
    EntityManager em;

    @Override
    @Transactional
    public void save(TenantId tenantId, Invoice invoice) {
        InvoiceEntity e = new InvoiceEntity();
        e.setId(invoice.getId().getValue());
        e.setTenantId(tenantId.getValue());
        e.setCustomerRef(invoice.getCustomerRef());
        e.setCurrencyCode(invoice.getCurrencyCode());
        e.setDueDate(invoice.getDueDate());
        e.setCreatedAt(invoice.getCreatedAt());
        e.setStatus(invoice.getStatus().name());
        e.setTotalAmount(invoice.getTotal().getAmount());
        em.merge(e);
    }

    @Override
    public Optional<Invoice> findByTenantAndId(TenantId tenantId, InvoiceId id) {
        InvoiceEntity e = em.find(InvoiceEntity.class, id.getValue());
        if (e == null || !e.getTenantId().equals(tenantId.getValue())) {
            return Optional.empty();
        }
        return Optional.of(toDomain(e));
    }

    @Override
    public Page<Invoice> findByTenant(TenantId tenantId, int limit, PageCursor cursor) {
        String jpql = "SELECT e FROM InvoiceEntity e WHERE e.tenantId = :tenantId";
        if (cursor != null) {
            jpql += " AND (e.createdAt < :createdAt OR (e.createdAt = :createdAt AND e.id < :id))";
        }
        jpql += " ORDER BY e.createdAt DESC, e.id DESC";
        var query = em.createQuery(jpql, InvoiceEntity.class)
                .setParameter("tenantId", tenantId.getValue())
                .setMaxResults(limit + 1);
        if (cursor != null) {
            query.setParameter("createdAt", cursor.createdAt())
                    .setParameter("id", cursor.id().getValue());
        }
        List<InvoiceEntity> list = query.getResultList();
        boolean hasMore = list.size() > limit;
        List<Invoice> items = list.stream().limit(limit).map(this::toDomain).toList();
        Optional<PageCursor> next = hasMore && !items.isEmpty()
                ? Optional.of(new PageCursor(items.get(items.size() - 1).getCreatedAt(), items.get(items.size() - 1).getId()))
                : Optional.empty();
        return new Page(items, next);
    }

    private Invoice toDomain(InvoiceEntity e) {
        return new Invoice(
                InvoiceId.of(e.getId()),
                e.getCustomerRef(),
                e.getCurrencyCode(),
                e.getDueDate(),
                e.getCreatedAt(),
                List.of(),
                InvoiceStatus.valueOf(e.getStatus())
        );
    }
}
