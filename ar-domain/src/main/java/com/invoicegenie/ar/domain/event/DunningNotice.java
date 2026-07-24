package com.invoicegenie.ar.domain.event;

import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.shared.domain.DomainEvent;
import com.invoicegenie.shared.domain.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event: dunning notice for an overdue invoice (STORY-015).
 */
public record DunningNotice(
        UUID eventId,
        TenantId tenantId,
        CustomerId customerId,
        InvoiceId invoiceId,
        int dunningLevel,
        int daysPastDue,
        String balanceDue,
        String currency,
        Instant occurredAt
) implements DomainEvent {

    public DunningNotice {
        if (eventId == null) eventId = UUID.randomUUID();
        occurredAt = occurredAt != null ? occurredAt : Instant.now();
    }

    public DunningNotice(TenantId tenantId, CustomerId customerId, InvoiceId invoiceId,
                         int dunningLevel, int daysPastDue, String balanceDue, String currency) {
        this(UUID.randomUUID(), tenantId, customerId, invoiceId, dunningLevel,
                daysPastDue, balanceDue, currency, Instant.now());
    }

    @Override public UUID eventId() { return eventId; }
    @Override public TenantId tenantId() { return tenantId; }
    @Override public Instant occurredAt() { return occurredAt; }
}