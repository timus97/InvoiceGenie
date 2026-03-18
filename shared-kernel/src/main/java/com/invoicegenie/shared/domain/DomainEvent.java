package com.invoicegenie.shared.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Base interface for domain events. All AR events implement this.
 * Events are immutable; occurredAt is set at construction.
 */
public interface DomainEvent {

    /**
     * Unique event id (UUID v7).
     */
    UUID eventId();

    /**
     * Tenant that owns this event. Never null.
     */
    TenantId tenantId();

    /**
     * When the event occurred (UTC). Set at construction.
     */
    Instant occurredAt();
}
