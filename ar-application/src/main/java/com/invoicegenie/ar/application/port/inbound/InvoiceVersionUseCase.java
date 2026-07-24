package com.invoicegenie.ar.application.port.inbound;

import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceVersion;
import com.invoicegenie.shared.domain.TenantId;

import java.util.List;
import java.util.Optional;

/**
 * Inbound port: invoice version history.
 */
public interface InvoiceVersionUseCase {

    List<InvoiceVersion> list(TenantId tenantId, InvoiceId invoiceId);

    Optional<InvoiceVersion> get(TenantId tenantId, InvoiceId invoiceId, long version);
}