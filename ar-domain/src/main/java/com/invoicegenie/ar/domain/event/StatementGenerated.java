package com.invoicegenie.ar.domain.event;

import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.shared.domain.DomainEvent;
import com.invoicegenie.shared.domain.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Domain event: customer AR statement generated (STORY-015).
 */
public record StatementGenerated(
        UUID eventId,
        TenantId tenantId,
        CustomerId customerId,
        LocalDate asOfDate,
        int openItemCount,
        String totalBalance,
        String currency,
        Instant occurredAt
) implements DomainEvent {

    public StatementGenerated {
        if (eventId == null) eventId = UUID.randomUUID();
        occurredAt = occurredAt != null ? occurredAt : Instant.now();
    }

    public StatementGenerated(TenantId tenantId, CustomerId customerId, LocalDate asOfDate,
                              int openItemCount, String totalBalance, String currency) {
        this(UUID.randomUUID(), tenantId, customerId, asOfDate, openItemCount,
                totalBalance, currency, Instant.now());
    }

    @Override public UUID eventId() { return eventId; }
    @Override public TenantId tenantId() { return tenantId; }
    @Override public Instant occurredAt() { return occurredAt; }
}