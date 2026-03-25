package com.invoicegenie.ar.domain.event;

import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.shared.domain.DomainEvent;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event published when an invoice is issued. Consumed by GL (and others).
 */
public record InvoiceIssued(
        UUID eventId,
        TenantId tenantId,
        InvoiceId invoiceId,
        String customerRef,
        Money total,
        Instant dueDate,
        Instant occurredAt
) implements DomainEvent {

    public InvoiceIssued {
        if (eventId == null) eventId = UUID.randomUUID();
        occurredAt = occurredAt != null ? occurredAt : Instant.now();
    }

    /**
     * Convenience constructor for creating an InvoiceIssued event.
     */
    public InvoiceIssued(TenantId tenantId, InvoiceId invoiceId, String customerRef,
                         Money total, Instant dueDate) {
        this(UUID.randomUUID(), tenantId, invoiceId, customerRef, total, dueDate, Instant.now());
    }
}

