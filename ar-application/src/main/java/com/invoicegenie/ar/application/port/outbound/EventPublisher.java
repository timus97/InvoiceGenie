package com.invoicegenie.ar.application.port.outbound;

import com.invoicegenie.ar.domain.event.InvoiceIssued;

/**
 * Outbound port: publish domain events (e.g. to Kafka). Implemented by ar-adapter-messaging.
 */
public interface EventPublisher {

    void publish(InvoiceIssued event);

    // Future: void publish(PaymentRecorded event); etc.
}
