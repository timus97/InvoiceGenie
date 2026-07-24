package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.adapter.persistence.entity.InvoiceVersionEntity;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceVersion;
import com.invoicegenie.ar.domain.model.invoice.InvoiceVersionRepository;
import com.invoicegenie.shared.domain.TenantId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class InvoiceVersionRepositoryAdapter implements InvoiceVersionRepository {

    @PersistenceContext
    EntityManager em;

    @Override
    @Transactional
    public void save(TenantId tenantId, InvoiceVersion version) {
        InvoiceVersionEntity e = new InvoiceVersionEntity();
        e.setId(version.getId());
        e.setTenantId(tenantId.getValue());
        e.setInvoiceId(version.getInvoiceId().getValue());
        e.setVersion(version.getVersion());
        e.setSnapshot(version.getSnapshotJson());
        e.setChangeReason(version.getChangeReason());
        e.setCreatedAt(version.getCreatedAt());
        em.merge(e);
    }

    @Override
    public List<InvoiceVersion> findByInvoice(TenantId tenantId, InvoiceId invoiceId) {
        return em.createQuery(
                        "SELECT v FROM InvoiceVersionEntity v WHERE v.tenantId = :tid AND v.invoiceId = :iid " +
                                "ORDER BY v.version DESC",
                        InvoiceVersionEntity.class)
                .setParameter("tid", tenantId.getValue())
                .setParameter("iid", invoiceId.getValue())
                .getResultStream().map(this::toDomain).toList();
    }

    @Override
    public Optional<InvoiceVersion> findByInvoiceAndVersion(TenantId tenantId, InvoiceId invoiceId, long version) {
        InvoiceVersionEntity e = em.find(InvoiceVersionEntity.class,
                new InvoiceVersionEntity.Pk(tenantId.getValue(), invoiceId.getValue(), version));
        return e == null ? Optional.empty() : Optional.of(toDomain(e));
    }

    private InvoiceVersion toDomain(InvoiceVersionEntity e) {
        return new InvoiceVersion(
                e.getId(),
                TenantId.of(e.getTenantId()),
                InvoiceId.of(e.getInvoiceId()),
                e.getVersion(),
                e.getSnapshot(),
                e.getChangeReason(),
                e.getCreatedAt()
        );
    }
}