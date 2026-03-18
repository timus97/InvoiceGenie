package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.GetInvoiceUseCase;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.shared.domain.TenantId;

import java.util.Optional;

/**
 * Application service: fetch invoice.
 */
public class GetInvoiceService implements GetInvoiceUseCase {

    private final InvoiceRepository invoiceRepository;

    public GetInvoiceService(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    @Override
    public Optional<Invoice> get(TenantId tenantId, InvoiceId invoiceId) {
        return invoiceRepository.findByTenantAndId(tenantId, invoiceId);
    }
}
