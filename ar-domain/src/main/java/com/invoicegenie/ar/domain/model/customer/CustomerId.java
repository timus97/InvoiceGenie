package com.invoicegenie.ar.domain.model.customer;

import com.invoicegenie.shared.domain.EntityId;

import java.util.UUID;

/**
 * Value object identifier for Customer aggregate.
 */
public final class CustomerId extends EntityId {

    public CustomerId(UUID value) {
        super(value);
    }

    public static CustomerId generate() {
        return new CustomerId(UUID.randomUUID());
    }

    public static CustomerId of(UUID value) {
        return new CustomerId(value);
    }
}
