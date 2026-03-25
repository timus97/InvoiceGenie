package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.IssueInvoiceUseCase;
import com.invoicegenie.ar.domain.model.outbox.AuditRepository;
import com.invoicegenie.ar.application.port.outbound.EventPublisher;
import com.invoicegenie.ar.application.port.outbound.IdGenerator;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceLine;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.domain.model.outbox.AuditEntry;
import com.invoicegenie.ar.domain.event.InvoiceIssued;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Application service: orchestrates domain and ports. No UI; no tenant resolution (receives TenantId).
 */
public class IssueInvoiceService implements IssueInvoiceUseCase {

    private final InvoiceRepository invoiceRepository;
    private final IdGenerator idGenerator;
    private final EventPublisher eventPublisher;
    private final AuditRepository auditRepository;

    public IssueInvoiceService(InvoiceRepository invoiceRepository,
                               IdGenerator idGenerator,
                               EventPublisher eventPublisher,
                               AuditRepository auditRepository) {
        this.invoiceRepository = invoiceRepository;
        this.idGenerator = idGenerator;
        this.eventPublisher = eventPublisher;
        this.auditRepository = auditRepository;
    }

    @Override
    public InvoiceId issue(TenantId tenantId, IssueInvoiceCommand command) {
        InvoiceId id = idGenerator.newInvoiceId();
        List<InvoiceLine> lines = IntStream.range(0, command.lines().size())
                .mapToObj(i -> {
                    var item = command.lines().get(i);
                    return new InvoiceLine(i + 1, item.description(), Money.of(item.amount(), command.currencyCode()));
                })
                .toList();
        Invoice invoice = new Invoice(id, command.invoiceNumber(), command.customerRef(), command.currencyCode(),
                command.dueDate(), command.dueDate(), lines);
        invoice.issue();
        invoiceRepository.save(tenantId, invoice);

        // Audit: record invoice creation
        String afterState = String.format("{\"id\":\"%s\",\"number\":\"%s\",\"customer\":\"%s\",\"total\":%s}",
                id.getValue(), command.invoiceNumber(), command.customerRef(), invoice.getTotal().getAmount());
        AuditEntry audit = AuditEntry.create(tenantId, "INVOICE", id.getValue(), command.invoiceNumber(),
                null, afterState);
        auditRepository.save(tenantId, audit);

        eventPublisher.publish(new InvoiceIssued(tenantId, id, command.customerRef(), invoice.getTotal(), command.dueDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant()));
        return id;
    }
}
