package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.domain.service.AgingService;
import com.invoicegenie.ar.domain.service.CreditNoteService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * CDI producer for AgingService and CreditNoteService.
 */
@ApplicationScoped
public class AgingProducer {

    @Produces
    @ApplicationScoped
    public AgingService agingService() {
        return new AgingService();
    }

    @Produces
    @ApplicationScoped
    public CreditNoteService creditNoteService() {
        return new CreditNoteService();
    }
}
