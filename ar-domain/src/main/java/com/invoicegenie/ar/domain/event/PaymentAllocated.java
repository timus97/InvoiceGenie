package com.invoicegenie.ar.domain.event;

import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.payment.PaymentId;
import com.invoicegenie.shared.domain.DomainEvent;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event published when payment is allocated to an invoice. Consumed by GL.
 */
public record PaymentAllocated(
        UUID eventId,
        TenantId tenantId,
        PaymentId paymentId,
        InvoiceId invoiceId,
        Money amount,
        Instant occurredAt
) implements DomainEvent {

    public PaymentAllocated {
        if (eventId == null) eventId = UUID.randomUUID();
        occurredAt = occurredAt != null ? occurredAt : Instant.now();
    }

    public PaymentAllocated(TenantId tenantId, PaymentId paymentId, InvoiceId invoiceId, Money amount) {
        this(UUID.randomUUID(), tenantId, paymentId, invoiceId, amount, Instant.now());
    }

    @Override
    public UUID eventId() { return eventId; }
    @Override
    public TenantId tenantId() { return tenantId; }
    @Override
    public Instant occurredAt() { return occurredAt; }
}
