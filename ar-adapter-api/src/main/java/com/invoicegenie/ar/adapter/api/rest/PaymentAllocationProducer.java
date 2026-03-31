package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.application.port.inbound.PaymentAllocationUseCase;
import com.invoicegenie.ar.application.port.inbound.RecordPaymentUseCase;
import com.invoicegenie.ar.application.port.outbound.IdGenerator;
import com.invoicegenie.ar.application.service.PaymentAllocationService;
import com.invoicegenie.ar.application.service.RecordPaymentService;
import com.invoicegenie.ar.domain.model.customer.CustomerRepository;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.domain.model.outbox.AuditRepository;
import com.invoicegenie.ar.domain.model.payment.PaymentRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

/**
 * CDI producer for Payment-related use cases.
 * This bridges the application layer services to CDI for injection into REST resources.
 */
@ApplicationScoped
public class PaymentAllocationProducer {

    @Inject
    PaymentRepository paymentRepository;

    @Inject
    InvoiceRepository invoiceRepository;

    @Inject
    CustomerRepository customerRepository;

    @Inject
    IdGenerator idGenerator;

    @Inject
    AuditRepository auditRepository;

    @Produces
    @ApplicationScoped
    public PaymentAllocationUseCase paymentAllocationUseCase() {
        return new PaymentAllocationService(paymentRepository, invoiceRepository);
    }

    @Produces
    @ApplicationScoped
    public RecordPaymentUseCase recordPaymentUseCase() {
        return new RecordPaymentService(paymentRepository, customerRepository, idGenerator, auditRepository);
    }
}
