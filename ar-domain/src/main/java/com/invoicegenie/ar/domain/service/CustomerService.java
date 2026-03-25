package com.invoicegenie.ar.domain.service;

import com.invoicegenie.ar.domain.model.customer.Customer;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.customer.CustomerRepository;
import com.invoicegenie.ar.domain.model.customer.CustomerStatus;
import com.invoicegenie.shared.domain.TenantId;

import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain service for customer management operations.
 * 
 * <p>Customer lifecycle:
 * <ol>
 *   <li>ACTIVE: Can receive invoices, make payments</li>
 *   <li>BLOCKED: Can view data but cannot receive new invoices</li>
 *   <li>DELETED: Soft-deleted, retained for audit</li>
 * </ol>
 * 
 * <p>Business rules:
 * <ul>
 *   <li>Customer code must be unique per tenant</li>
 *   <li>Credit limit validation before invoicing</li>
 *   <li>Cannot delete customer with outstanding invoices</li>
 *   <li>BLOCKED customers cannot be invoiced</li>
 * </ul>
 */
@ApplicationScoped
public class CustomerService {

    /**
     * Result of customer creation.
     */
    public record CreateResult(Customer customer, boolean success, String message) {}

    /**
     * Result of customer status change.
     */
    public record StatusResult(Customer customer, boolean success, String message) {}

    /**
     * Result of credit limit check.
     */
    public record CreditCheckResult(boolean canInvoice, BigDecimal availableCredit, String message) {}

    /**
     * Creates a new customer with validation.
     */
    public CreateResult createCustomer(TenantId tenantId, CustomerRepository repository,
                                       String customerCode, String legalName, String currency) {
        try {
            // Check for duplicate code
            if (repository.existsByTenantAndCode(tenantId, customerCode)) {
                return new CreateResult(null, false, "Customer code already exists: " + customerCode);
            }

            CustomerId id = CustomerId.of(UUID.randomUUID());
            Customer customer = new Customer(id, customerCode, legalName, currency);
            customer.validate();

            repository.save(tenantId, customer);
            return new CreateResult(customer, true, "Customer created successfully");
        } catch (Exception e) {
            return new CreateResult(null, false, e.getMessage());
        }
    }

    /**
     * Blocks a customer (prevents new invoices).
     */
    public StatusResult blockCustomer(TenantId tenantId, CustomerRepository repository, CustomerId customerId) {
        try {
            Optional<Customer> opt = repository.findByTenantAndId(tenantId, customerId);
            if (opt.isEmpty()) {
                return new StatusResult(null, false, "Customer not found");
            }

            Customer customer = opt.get();
            if (customer.getStatus() == CustomerStatus.BLOCKED) {
                return new StatusResult(customer, false, "Customer is already blocked");
            }
            if (customer.getStatus() == CustomerStatus.DELETED) {
                return new StatusResult(customer, false, "Cannot block a deleted customer");
            }
            customer.block();
            repository.save(tenantId, customer);
            return new StatusResult(customer, true, "Customer blocked");
        } catch (Exception e) {
            return new StatusResult(null, false, e.getMessage());
        }
    }

    /**
     * Unblocks a customer (allows invoicing again).
     */
    public StatusResult unblockCustomer(TenantId tenantId, CustomerRepository repository, CustomerId customerId) {
        try {
            Optional<Customer> opt = repository.findByTenantAndId(tenantId, customerId);
            if (opt.isEmpty()) {
                return new StatusResult(null, false, "Customer not found");
            }

            Customer customer = opt.get();
            // Unblocking is setting status back to ACTIVE
            if (customer.getStatus() != CustomerStatus.BLOCKED) {
                return new StatusResult(customer, false, "Customer is not blocked (status: " + customer.getStatus() + ")");
            }
            // Create new customer instance with ACTIVE status (immutable pattern)
            Customer active = new Customer(
                    customer.getId(),
                    customer.getCustomerCode(),
                    customer.getLegalName(),
                    customer.getDisplayName(),
                    customer.getEmail(),
                    customer.getPhone(),
                    customer.getBillingAddress(),
                    customer.getCurrency(),
                    customer.getCreditLimit(),
                    customer.getPaymentTerms(),
                    customer.getTaxId(),
                    CustomerStatus.ACTIVE,
                    customer.getCreatedAt(),
                    java.time.Instant.now(),
                    customer.getVersion() + 1
            );
            repository.save(tenantId, active);
            return new StatusResult(active, true, "Customer unblocked");
        } catch (Exception e) {
            return new StatusResult(null, false, e.getMessage());
        }
    }

    /**
     * Soft deletes a customer.
     */
    public StatusResult deleteCustomer(TenantId tenantId, CustomerRepository repository, CustomerId customerId) {
        try {
            Optional<Customer> opt = repository.findByTenantAndId(tenantId, customerId);
            if (opt.isEmpty()) {
                return new StatusResult(null, false, "Customer not found");
            }

            Customer customer = opt.get();
            if (customer.getStatus() == CustomerStatus.DELETED) {
                return new StatusResult(customer, false, "Customer already deleted");
            }
            customer.delete();
            repository.save(tenantId, customer);
            return new StatusResult(customer, true, "Customer deleted");
        } catch (Exception e) {
            return new StatusResult(null, false, e.getMessage());
        }
    }

    /**
     * Checks if customer can be invoiced for a given amount.
     * 
     * @param tenantId Tenant ID
     * @param repository Customer repository
     * @param customerId Customer ID
     * @param outstandingBalance Current outstanding balance
     * @param invoiceAmount New invoice amount
     * @return CreditCheckResult with decision
     */
    public CreditCheckResult checkCreditLimit(TenantId tenantId, CustomerRepository repository,
                                               CustomerId customerId,
                                               BigDecimal outstandingBalance, BigDecimal invoiceAmount) {
        try {
            Optional<Customer> opt = repository.findByTenantAndId(tenantId, customerId);
            if (opt.isEmpty()) {
                return new CreditCheckResult(false, BigDecimal.ZERO, "Customer not found");
            }

            Customer customer = opt.get();
            if (!customer.canBeInvoiced()) {
                return new CreditCheckResult(false, BigDecimal.ZERO, 
                        "Customer is not active (status: " + customer.getStatus() + ")");
            }

            BigDecimal available = customer.getAvailableCredit(outstandingBalance);
            boolean canInvoice = customer.canBeInvoicedForAmount(outstandingBalance, invoiceAmount);

            if (canInvoice) {
                return new CreditCheckResult(true, available, "Credit check passed");
            } else {
                return new CreditCheckResult(false, available, 
                        "Credit limit exceeded. Available: " + available + ", Required: " + invoiceAmount);
            }
        } catch (Exception e) {
            return new CreditCheckResult(false, BigDecimal.ZERO, e.getMessage());
        }
    }

    /**
     * Lists all active customers for a tenant.
     */
    public List<Customer> listActiveCustomers(TenantId tenantId, CustomerRepository repository) {
        return repository.findByTenantAndStatus(tenantId, CustomerStatus.ACTIVE);
    }

    /**
     * Searches customers by name or code.
     */
    public List<Customer> searchCustomers(TenantId tenantId, CustomerRepository repository, String query) {
        if (query == null || query.isBlank()) {
            return repository.findAllByTenant(tenantId, false);
        }
        return repository.searchByTenant(tenantId, query);
    }
}
