package com.invoicegenie.shared.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Value object identifying a tenant. Never null in AR operations.
 * Used by all bounded contexts (AR, AP, GL) for isolation.
 */
public final class TenantId {

    private final UUID value;

    public TenantId(UUID value) {
        this.value = Objects.requireNonNull(value, "TenantId value must not be null");
    }

    public static TenantId of(UUID value) {
        return new TenantId(value);
    }

    public static TenantId of(String value) {
        return new TenantId(UUID.fromString(value));
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TenantId tenantId = (TenantId) o;
        return value.equals(tenantId.value);
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
