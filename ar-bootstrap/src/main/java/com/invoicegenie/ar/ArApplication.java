package com.invoicegenie.ar;

import com.invoicegenie.ar.application.port.inbound.GetInvoiceUseCase;
import com.invoicegenie.ar.application.port.inbound.InvoiceLifecycleUseCase;
import com.invoicegenie.ar.application.port.inbound.IssueInvoiceUseCase;
import com.invoicegenie.ar.application.port.inbound.ListInvoicesUseCase;
import com.invoicegenie.ar.application.port.outbound.EventPublisher;
import com.invoicegenie.ar.application.port.outbound.IdGenerator;
import com.invoicegenie.ar.application.service.GetInvoiceService;
import com.invoicegenie.ar.application.service.InvoiceLifecycleService;
import com.invoicegenie.ar.application.service.IssueInvoiceService;
import com.invoicegenie.ar.application.service.ListInvoicesService;
import com.invoicegenie.ar.domain.event.InvoiceIssued;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * CDI wiring: adapters implement ports; use cases get dependencies here.
 */
public class ArApplication {

    @Produces
    @ApplicationScoped
    public IssueInvoiceUseCase issueInvoiceUseCase(InvoiceRepository invoiceRepository,
                                                   IdGenerator idGenerator,
                                                   EventPublisher eventPublisher) {
        return new IssueInvoiceService(invoiceRepository, idGenerator, eventPublisher);
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
    public InvoiceLifecycleUseCase lifecycleUseCase(InvoiceRepository invoiceRepository) {
        return new InvoiceLifecycleService(invoiceRepository);
    }

    @Produces
    @ApplicationScoped
    public IdGenerator idGenerator() {
        return new IdGenerator() {
            @Override
            public InvoiceId newInvoiceId() {
                return InvoiceId.of(java.util.UUID.randomUUID());
            }
        };
    }

    /** No-op event publisher stub for dev/test builds. */
    @Produces
    @ApplicationScoped
    public EventPublisher eventPublisher() {
        return new EventPublisher() {
            @Override
            public void publish(InvoiceIssued event) {
                // no-op: Kafka adapter in ar-adapter-messaging handles real publishing
            }
        };
    }
}
