package com.invoicegenie.ar;

import com.invoicegenie.ar.application.port.inbound.GetInvoiceUseCase;
import com.invoicegenie.ar.application.port.inbound.InvoiceLifecycleUseCase;
import com.invoicegenie.ar.application.port.inbound.IssueInvoiceUseCase;
import com.invoicegenie.ar.application.port.inbound.ListInvoicesUseCase;
import com.invoicegenie.ar.application.port.outbound.IdGenerator;
import com.invoicegenie.ar.application.service.GetInvoiceService;
import com.invoicegenie.ar.application.service.InvoiceLifecycleService;
import com.invoicegenie.ar.application.service.IssueInvoiceService;
import com.invoicegenie.ar.application.service.ListInvoicesService;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.shared.domain.UuidV7;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * CDI wiring: adapters implement ports; use cases get dependencies here.
 * 
 * <p>EventPublisher is now provided by KafkaEventPublisher in ar-adapter-messaging,
 * which uses the transactional outbox pattern for reliable event delivery.
 */
public class ArApplication {

    @Produces
    @ApplicationScoped
    public IssueInvoiceUseCase issueInvoiceUseCase(InvoiceRepository invoiceRepository,
                                                   IdGenerator idGenerator,
                                                   com.invoicegenie.ar.application.port.outbound.EventPublisher eventPublisher,
                                                   com.invoicegenie.ar.domain.model.outbox.AuditRepository auditRepository) {
        return new IssueInvoiceService(invoiceRepository, idGenerator, eventPublisher, auditRepository);
    }

    @Produces
    @ApplicationScoped
    public GetInvoiceUseCase getInvoiceUseCase(InvoiceRepository invoiceRepository) {
        return new GetInvoiceService(invoiceRepository);
    }

    @Produces
    @ApplicationScoped
    public ListInvoicesUseCase listInvoicesUseCase(InvoiceRepository invoiceRepository) {
        return new ListInvoicesService(invoiceRepository);
    }

    @Produces
    @ApplicationScoped
    public InvoiceLifecycleUseCase lifecycleUseCase(InvoiceRepository invoiceRepository,
                                                     com.invoicegenie.ar.domain.model.outbox.AuditRepository auditRepository) {
        return new InvoiceLifecycleService(invoiceRepository, auditRepository);
    }

    @Produces
    @ApplicationScoped
    public IdGenerator idGenerator() {
        return new IdGenerator() {
            @Override
            public InvoiceId newInvoiceId() {
                return InvoiceId.of(UuidV7.generate());
            }
        };
    }
}
