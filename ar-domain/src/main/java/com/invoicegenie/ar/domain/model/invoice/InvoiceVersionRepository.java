package com.invoicegenie.ar.domain.model.invoice;

import com.invoicegenie.shared.domain.TenantId;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port: immutable invoice version snapshots.
 */
public interface InvoiceVersionRepository {

    void save(TenantId tenantId, InvoiceVersion version);

    List<InvoiceVersion> findByInvoice(TenantId tenantId, InvoiceId invoiceId);

    Optional<InvoiceVersion> findByInvoiceAndVersion(TenantId tenantId, InvoiceId invoiceId, long version);
}
