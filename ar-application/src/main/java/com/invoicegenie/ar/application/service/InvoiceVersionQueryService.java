package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.InvoiceVersionUseCase;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceVersion;
import com.invoicegenie.ar.domain.model.invoice.InvoiceVersionRepository;
import com.invoicegenie.shared.domain.TenantId;

import java.util.List;
import java.util.Optional;

/**
 * Application service: invoice version history queries.
 */
public class InvoiceVersionQueryService implements InvoiceVersionUseCase {

    private final InvoiceVersionRepository invoiceVersionRepository;

    public InvoiceVersionQueryService(InvoiceVersionRepository invoiceVersionRepository) {
        this.invoiceVersionRepository = invoiceVersionRepository;
    }

    @Override
    public List<InvoiceVersion> list(TenantId tenantId, InvoiceId invoiceId) {
        return invoiceVersionRepository.findByInvoice(tenantId, invoiceId);
    }

    @Override
    public Optional<InvoiceVersion> get(TenantId tenantId, InvoiceId invoiceId, long version) {
        return invoiceVersionRepository.findByInvoiceAndVersion(tenantId, invoiceId, version);
    }
}