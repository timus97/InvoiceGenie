package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.ApplyInvoicePaymentUseCase;
import com.invoicegenie.ar.application.port.inbound.InvoiceLifecycleUseCase;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.domain.model.outbox.AuditEntry;
import com.invoicegenie.ar.domain.model.outbox.AuditRepository;
import com.invoicegenie.shared.domain.TenantId;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Application service: invoice lifecycle operations.
 */
public class InvoiceLifecycleService implements InvoiceLifecycleUseCase {

    private final InvoiceRepository invoiceRepository;
    private final AuditRepository auditRepository;
    private final ApplyInvoicePaymentUseCase applyInvoicePaymentUseCase;

    public InvoiceLifecycleService(InvoiceRepository invoiceRepository,
                                   AuditRepository auditRepository,
                                   ApplyInvoicePaymentUseCase applyInvoicePaymentUseCase) {
        this.invoiceRepository = invoiceRepository;
        this.auditRepository = auditRepository;
        this.applyInvoicePaymentUseCase = applyInvoicePaymentUseCase;
    }

    @Override
    public Optional<Invoice> issue(TenantId tenantId, InvoiceId invoiceId) {
        return invoiceRepository.findByTenantAndId(tenantId, invoiceId)
                .map(inv -> {
                    String before = String.format("{\"status\":\"%s\"}", inv.getStatus());
                    inv.issue();
                    invoiceRepository.save(tenantId, inv);
                    String after = String.format("{\"status\":\"%s\"}", inv.getStatus());
                    auditRepository.save(tenantId, AuditEntry.transition(tenantId, "INVOICE", invoiceId.getValue(),
                            inv.getInvoiceNumber(), null, "ISSUE", before, after));
                    return inv;
                });
    }

    @Override
    public Optional<Invoice> markOverdue(TenantId tenantId, InvoiceId invoiceId, LocalDate today) {
        return invoiceRepository.findByTenantAndId(tenantId, invoiceId)
                .map(inv -> {
                    String before = String.format("{\"status\":\"%s\"}", inv.getStatus());
                    inv.markOverdue(today);
                    invoiceRepository.save(tenantId, inv);
                    String after = String.format("{\"status\":\"%s\"}", inv.getStatus());
                    auditRepository.save(tenantId, AuditEntry.transition(tenantId, "INVOICE", invoiceId.getValue(),
                            inv.getInvoiceNumber(), null, "MARK_OVERDUE", before, after));
                    return inv;
                });
    }

    @Override
    public Optional<Invoice> writeOff(TenantId tenantId, InvoiceId invoiceId, String reason) {
        return invoiceRepository.findByTenantAndId(tenantId, invoiceId)
                .map(inv -> {
                    String before = String.format("{\"status\":\"%s\"}", inv.getStatus());
                    inv.writeOff(reason);
                    invoiceRepository.save(tenantId, inv);
                    String after = String.format("{\"status\":\"%s\",\"reason\":\"%s\"}", inv.getStatus(), reason);
                    auditRepository.save(tenantId, AuditEntry.transition(tenantId, "INVOICE", invoiceId.getValue(),
                            inv.getInvoiceNumber(), null, "WRITE_OFF", before, after));
                    return inv;
                });
    }

    @Override
    public Optional<Invoice> applyPayment(TenantId tenantId, InvoiceId invoiceId, boolean fullyPaid) {
        // Creates a real Payment + allocates to this invoice (PaymentRecorded + PaymentAllocated)
        return applyInvoicePaymentUseCase.apply(tenantId, invoiceId,
                new ApplyInvoicePaymentUseCase.ApplyPaymentCommand(null, fullyPaid));
    }

    @Override
    public Optional<Invoice> updateDueDate(TenantId tenantId, InvoiceId invoiceId, LocalDate newDueDate) {
        return invoiceRepository.findByTenantAndId(tenantId, invoiceId)
                .map(inv -> {
                    String before = String.format("{\"dueDate\":\"%s\"}", inv.getDueDate());
                    inv.setDueDate(newDueDate);
                    invoiceRepository.save(tenantId, inv);
                    String after = String.format("{\"dueDate\":\"%s\"}", inv.getDueDate());
                    auditRepository.save(tenantId, AuditEntry.transition(tenantId, "INVOICE", invoiceId.getValue(),
                            inv.getInvoiceNumber(), null, "UPDATE_DUE_DATE", before, after));
                    return inv;
                });
    }

    @Override
    public Optional<Invoice> reopen(TenantId tenantId, InvoiceId invoiceId, String reason) {
        return invoiceRepository.findByTenantAndId(tenantId, invoiceId)
                .map(inv -> {
                    String before = String.format("{\"status\":\"%s\"}", inv.getStatus());
                    inv.reopen(reason);
                    invoiceRepository.save(tenantId, inv);
                    String after = String.format("{\"status\":\"%s\",\"reason\":\"%s\"}", inv.getStatus(), reason);
                    auditRepository.save(tenantId, AuditEntry.transition(tenantId, "INVOICE", invoiceId.getValue(),
                            inv.getInvoiceNumber(), null, "REOPEN", before, after));
                    return inv;
                });
    }
}
