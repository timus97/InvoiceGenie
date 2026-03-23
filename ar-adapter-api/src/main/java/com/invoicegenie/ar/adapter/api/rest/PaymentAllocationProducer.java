package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.application.port.inbound.PaymentAllocationUseCase;
import com.invoicegenie.ar.application.service.PaymentAllocationService;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.domain.model.payment.PaymentRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

/**
 * CDI producer for PaymentAllocationUseCase.
 * This bridges the application layer service to CDI for injection into REST resources.
 */
@ApplicationScoped
public class PaymentAllocationProducer {

    @Inject
    PaymentRepository paymentRepository;

    @Inject
    InvoiceRepository invoiceRepository;

    @Produces
    @ApplicationScoped
    public PaymentAllocationUseCase paymentAllocationUseCase() {
        return new PaymentAllocationService(paymentRepository, invoiceRepository);
    }
}
