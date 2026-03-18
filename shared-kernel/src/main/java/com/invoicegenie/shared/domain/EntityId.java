package com.invoicegenie.shared.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Base type for aggregate/entity identifiers. Use concrete types (InvoiceId, PaymentId) in domain.
 */
public abstract class EntityId {

    private final UUID value;

    protected EntityId(UUID value) {
        this.value = Objects.requireNonNull(value, "EntityId value must not be null");
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EntityId entityId = (EntityId) o;
        return value.equals(entityId.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
