package com.invoicegenie.ar.adapter.messaging;

import com.invoicegenie.ar.application.port.outbound.EventPublisher;
import com.invoicegenie.ar.domain.event.InvoiceIssued;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

/**
 * Driven adapter: publishes domain events to Kafka. Tenant in header for subscriber isolation.
 */
@ApplicationScoped
public class KafkaEventPublisher implements EventPublisher {

    @Channel("invoice-issued")
    Emitter<String> emitter;

    @Override
    public void publish(InvoiceIssued event) {
        // In production: serialize to JSON and set tenant_id header
        String payload = String.format(
                "{\"tenantId\":\"%s\",\"invoiceId\":\"%s\",\"customerRef\":\"%s\",\"total\":\"%s %s\",\"dueDate\":\"%s\"}",
                event.tenantId(), event.invoiceId().getValue(), event.customerRef(),
                event.total().getAmount(), event.total().getCurrencyCode(), event.dueDate()
        );
        // In production add Kafka header "tenant_id" = event.tenantId().toString() via connector config or interceptor
        emitter.send(payload);
    }
}
