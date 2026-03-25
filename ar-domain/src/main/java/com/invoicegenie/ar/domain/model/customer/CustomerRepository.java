package com.invoicegenie.ar.domain.model.customer;

import com.invoicegenie.shared.domain.TenantId;

import java.util.List;
import java.util.Optional;

/**
 * Port (outbound): persistence for Customer aggregate.
 * All methods require TenantId — enforced by application layer.
 * 
 * <p>Business rules enforced:
 * <ul>
 *   <li>Customer code is unique per tenant</li>
 *   <li>Only ACTIVE customers can be invoiced</li>
 *   <li>BLOCKED customers can view invoices but not receive new ones</li>
 *   <li>DELETED customers are soft-deleted (retained for audit)</li>
 * </ul>
 */
public interface CustomerRepository {

    /**
     * Saves (inserts or updates) a customer.
     * Validates uniqueness of customer code within tenant.
     */
    void save(TenantId tenantId, Customer customer);

    /**
     * Finds a customer by ID within a tenant.
     */
    Optional<Customer> findByTenantAndId(TenantId tenantId, CustomerId id);

    /**
     * Finds a customer by business code (customer_code) within a tenant.
     * Used for external references (e.g., from ERP systems).
     */
    Optional<Customer> findByTenantAndCode(TenantId tenantId, String customerCode);

    /**
     * Checks if customer exists and is active (can be invoiced).
     */
    boolean existsActive(TenantId tenantId, CustomerId id);

    /**
     * Finds all customers for a tenant.
     * 
     * @param includeDeleted if true, includes DELETED status customers
     */
    List<Customer> findAllByTenant(TenantId tenantId, boolean includeDeleted);

    /**
     * Finds customers by status for a tenant.
     */
    List<Customer> findByTenantAndStatus(TenantId tenantId, CustomerStatus status);

    /**
     * Searches customers by name or code (partial match).
     */
    List<Customer> searchByTenant(TenantId tenantId, String query);

    /**
     * Counts customers by status for a tenant.
     */
    long countByTenantAndStatus(TenantId tenantId, CustomerStatus status);

    /**
     * Checks if a customer code is already used by another customer in the tenant.
     */
    boolean existsByTenantAndCode(TenantId tenantId, String customerCode);

    /**
     * Deletes a customer (soft delete - marks as DELETED).
     */
    void delete(TenantId tenantId, CustomerId id);
}
