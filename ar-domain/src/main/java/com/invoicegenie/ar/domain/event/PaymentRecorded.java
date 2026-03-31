package com.invoicegenie.ar.domain.event;

import com.invoicegenie.ar.domain.model.payment.PaymentId;
import com.invoicegenie.shared.domain.DomainEvent;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event published when a payment is recorded. Consumed by GL.
 */
public record PaymentRecorded(
        UUID eventId,
        TenantId tenantId,
        PaymentId paymentId,
        String customerRef,
        Money amount,
        Instant paymentDate,
        Instant occurredAt
) implements DomainEvent {

    public PaymentRecorded {
        if (eventId == null) eventId = UUID.randomUUID();
        occurredAt = occurredAt != null ? occurredAt : Instant.now();
    }

    public PaymentRecorded(TenantId tenantId, PaymentId paymentId, String customerRef,
                           Money amount, Instant paymentDate) {
        this(UUID.randomUUID(), tenantId, paymentId, customerRef, amount, paymentDate, Instant.now());
    }

    @Override
    public UUID eventId() { return eventId; }
    @Override
    public TenantId tenantId() { return tenantId; }
    @Override
    public Instant occurredAt() { return occurredAt; }
}
