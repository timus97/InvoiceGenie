package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.application.port.inbound.InvoiceLifecycleUseCase;
import com.invoicegenie.ar.domain.model.payment.ChequeRepository;
import com.invoicegenie.ar.domain.service.ChequeService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

/**
 * CDI producer for ChequeService.
 */
@ApplicationScoped
public class ChequeProducer {

    @Inject
    ChequeRepository chequeRepository;

    @Inject
    InvoiceLifecycleUseCase invoiceLifecycleUseCase;

    @Produces
    @ApplicationScoped
    public ChequeService chequeService() {
        return new ChequeService();
    }
}
