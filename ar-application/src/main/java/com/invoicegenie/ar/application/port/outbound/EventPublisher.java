package com.invoicegenie.ar.application.port.outbound;

import com.invoicegenie.shared.domain.DomainEvent;

/**
 * Outbound port: publish domain events to the messaging infrastructure.
 * 
 * <p>Implementations should use the transactional outbox pattern to ensure
 * reliable delivery. Events are saved to the outbox table in the same
 * transaction as the aggregate changes, then published asynchronously.
 * 
 * <p>This interface is part of the application layer and defines what
 * the application needs from the messaging infrastructure.
 */
public interface EventPublisher {

    /**
     * Publishes a domain event.
     * 
     * <p>The event will be saved to the outbox table and published
     * asynchronously by the OutboxWorker.
     * 
     * @param event the domain event to publish
     */
    void publish(DomainEvent event);
}

