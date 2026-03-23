package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.domain.model.ledger.LedgerRepository;
import com.invoicegenie.ar.domain.service.LedgerService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

/**
 * CDI producer for LedgerService.
 */
@ApplicationScoped
public class LedgerProducer {

    @Inject
    LedgerRepository ledgerRepository;

    @Produces
    @ApplicationScoped
    public LedgerService ledgerService() {
        return new LedgerService();
    }
}
