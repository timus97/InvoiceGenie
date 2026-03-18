package com.invoicegenie.ar.domain.model.customer;

import com.invoicegenie.shared.domain.TenantId;

import java.util.Optional;

/**
 * Port (outbound): persistence for Customer aggregate.
 * All methods require TenantId — enforced by application layer.
 */
public interface CustomerRepository {

    void save(TenantId tenantId, Customer customer);

    Optional<Customer> findByTenantAndId(TenantId tenantId, CustomerId id);

    Optional<Customer> findByTenantAndCode(TenantId tenantId, String customerCode);

    /**
     * Checks if customer exists and is active.
     */
    boolean existsActive(TenantId tenantId, CustomerId id);
}
