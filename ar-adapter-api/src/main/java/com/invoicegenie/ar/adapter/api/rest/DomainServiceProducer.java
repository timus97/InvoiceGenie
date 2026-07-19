package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.domain.service.CreditNoteService;
import com.invoicegenie.ar.domain.service.CustomerService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * CDI producers for plain domain services (no CDI annotations in ar-domain).
 */
@ApplicationScoped
public class DomainServiceProducer {

    @Produces
    @ApplicationScoped
    public CustomerService customerService() {
        return new CustomerService();
    }

    @Produces
    @ApplicationScoped
    public CreditNoteService creditNoteService() {
        return new CreditNoteService();
    }
}
