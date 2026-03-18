package com.invoicegenie.ar.application.port.inbound;

import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.shared.domain.TenantId;

import java.util.Optional;

/**
 * Inbound port: fetch invoice by ID.
 */
public interface GetInvoiceUseCase {

    Optional<Invoice> get(TenantId tenantId, InvoiceId invoiceId);
}
