package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.domain.service.AgingService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * CDI producer for domain services that don't have @ApplicationScoped annotation.
 */
@ApplicationScoped
public class AgingProducer {

    @Produces
    @ApplicationScoped
    public AgingService agingService() {
        return new AgingService();
    }
}
