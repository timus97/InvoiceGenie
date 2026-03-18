package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.adapter.persistence.entity.InvoiceEntity;
import com.invoicegenie.ar.adapter.persistence.entity.InvoiceLineEntity;
import com.invoicegenie.ar.adapter.persistence.mapper.InvoiceMapper;
import com.invoicegenie.shared.domain.TenantId;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;

/**
 * Driven adapter: implements InvoiceRepository port. All queries include tenant_id.
 */
@ApplicationScoped
public class InvoiceRepositoryAdapter implements InvoiceRepository {

    @PersistenceContext
    EntityManager em;

    private final InvoiceMapper mapper = new InvoiceMapper();

    @Override
    @Transactional
    public void save(TenantId tenantId, Invoice invoice) {
        InvoiceEntity entity = mapper.toEntity(tenantId, invoice);
        em.merge(entity);

        // Replace line items for this invoice
        em.createQuery("DELETE FROM InvoiceLineEntity l WHERE l.tenantId = :tenantId AND l.invoiceId = :invoiceId")
                .setParameter("tenantId", tenantId.getValue())
                .setParameter("invoiceId", invoice.getId().getValue())
                .executeUpdate();

        for (InvoiceLineEntity lineEntity : mapper.toLineEntities(tenantId, invoice)) {
            em.persist(lineEntity);
        }
    }

    @Override
    public Optional<Invoice> findByTenantAndId(TenantId tenantId, InvoiceId id) {
        InvoiceEntity e = em.find(InvoiceEntity.class, id.getValue());
        if (e == null || !e.getTenantId().equals(tenantId.getValue())) {
            return Optional.empty();
        }
        List<InvoiceLineEntity> lines = em.createQuery(
                        "SELECT l FROM InvoiceLineEntity l WHERE l.tenantId = :tenantId AND l.invoiceId = :invoiceId ORDER BY l.sequence",
                        InvoiceLineEntity.class)
                .setParameter("tenantId", tenantId.getValue())
                .setParameter("invoiceId", id.getValue())
                .getResultList();
        return Optional.of(mapper.toDomain(e, lines));
    }

    @Override
    public Page findByTenant(TenantId tenantId, int limit, PageCursor cursor) {
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
        List<Invoice> items = list.stream().limit(limit)
                .map(e -> mapper.toDomain(e, List.of()))
                .toList();
        Optional<PageCursor> next = hasMore && !items.isEmpty()
                ? Optional.of(new PageCursor(items.get(items.size() - 1).getCreatedAt(), items.get(items.size() - 1).getId()))
                : Optional.empty();
        return new Page(items, next);
    }
}
