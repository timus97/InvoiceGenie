package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.InvoiceLifecycleUseCase;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.shared.domain.TenantId;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Application service: invoice lifecycle operations.
 */
public class InvoiceLifecycleService implements InvoiceLifecycleUseCase {

    private final InvoiceRepository invoiceRepository;

    public InvoiceLifecycleService(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    @Override
    public Optional<Invoice> issue(TenantId tenantId, InvoiceId invoiceId) {
        return invoiceRepository.findByTenantAndId(tenantId, invoiceId)
                .map(inv -> { inv.issue(); invoiceRepository.save(tenantId, inv); return inv; });
    }

    @Override
    public Optional<Invoice> markOverdue(TenantId tenantId, InvoiceId invoiceId, LocalDate today) {
        return invoiceRepository.findByTenantAndId(tenantId, invoiceId)
                .map(inv -> { inv.markOverdue(today); invoiceRepository.save(tenantId, inv); return inv; });
    }

    @Override
    public Optional<Invoice> writeOff(TenantId tenantId, InvoiceId invoiceId, String reason) {
        return invoiceRepository.findByTenantAndId(tenantId, invoiceId)
                .map(inv -> { inv.writeOff(reason); invoiceRepository.save(tenantId, inv); return inv; });
    }

    @Override
    public Optional<Invoice> applyPayment(TenantId tenantId, InvoiceId invoiceId, boolean fullyPaid) {
        return invoiceRepository.findByTenantAndId(tenantId, invoiceId)
                .map(inv -> { inv.applyPaymentStatus(fullyPaid); invoiceRepository.save(tenantId, inv); return inv; });
    }

    @Override
    public Optional<Invoice> updateDueDate(TenantId tenantId, InvoiceId invoiceId, LocalDate newDueDate) {
        return invoiceRepository.findByTenantAndId(tenantId, invoiceId)
                .map(inv -> { inv.setDueDate(newDueDate); invoiceRepository.save(tenantId, inv); return inv; });
    }
}
