package com.invoicegenie.ar.domain.model.payment;

import com.invoicegenie.shared.domain.EntityId;

import java.util.UUID;

/**
 * Value object identifier for Payment aggregate.
 */
public final class PaymentId extends EntityId {

    public PaymentId(UUID value) {
        super(value);
    }

    public static PaymentId generate() {
        return new PaymentId(UUID.randomUUID());
    }

    public static PaymentId of(UUID value) {
        return new PaymentId(value);
    }
}
