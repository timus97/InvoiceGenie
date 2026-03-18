package com.invoicegenie.shared.tenant;

import com.invoicegenie.shared.domain.TenantId;

/**
 * Request-scoped tenant. Set by TenantFilter before any use case runs.
 * Do not use in domain layer; application layer reads from here and passes TenantId to ports.
 */
public final class TenantContext {

    private static final ThreadLocal<TenantId> CURRENT = new ThreadLocal<>();

    public static void setCurrentTenant(TenantId tenantId) {
        CURRENT.set(tenantId);
    }

    public static TenantId getCurrentTenant() {
        TenantId tenant = CURRENT.get();
        if (tenant == null) {
            throw new IllegalStateException("TenantContext not set — ensure TenantFilter runs and tenant is resolved");
        }
        return tenant;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
