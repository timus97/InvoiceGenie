package com.invoicegenie.ar;

import com.invoicegenie.ar.application.port.inbound.IssueInvoiceUseCase;
import com.invoicegenie.ar.application.port.outbound.EventPublisher;
import com.invoicegenie.ar.application.port.outbound.IdGenerator;
import com.invoicegenie.ar.application.service.IssueInvoiceService;
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
    public IdGenerator idGenerator() {
        return new IdGenerator() {
            @Override
            public InvoiceId newInvoiceId() {
                return InvoiceId.of(java.util.UUID.randomUUID());
            }
        };
    }
}
