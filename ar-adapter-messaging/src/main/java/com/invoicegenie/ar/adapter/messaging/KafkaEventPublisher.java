package com.invoicegenie.ar.adapter.messaging;

import com.invoicegenie.ar.application.port.outbound.EventPublisher;
import com.invoicegenie.ar.domain.event.InvoiceIssued;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Driven adapter: publishes domain events to Kafka. Tenant in header for subscriber isolation.
 */
@ApplicationScoped
public class KafkaEventPublisher implements EventPublisher {

    @Override
    public void publish(InvoiceIssued event) {
        // In production: serialize to JSON and set tenant_id header, send via Kafka connector
        // Stubbed for build: no-op
    }
}
