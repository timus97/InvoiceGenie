package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.CustomerUseCase;
import com.invoicegenie.ar.domain.model.customer.Customer;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.customer.CustomerRepository;
import com.invoicegenie.ar.domain.model.customer.CustomerStatus;
import com.invoicegenie.ar.domain.service.CustomerService;
import com.invoicegenie.shared.domain.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Application service: customer management use cases.
 *
 * <p>Wraps {@link CustomerService} domain operations and repository access.
 */
public class CustomerManagementService implements CustomerUseCase {

    private final CustomerService customerService;
    private final CustomerRepository customerRepository;

    public CustomerManagementService(CustomerService customerService,
                                     CustomerRepository customerRepository) {
        this.customerService = customerService;
        this.customerRepository = customerRepository;
    }

    @Override
    public CustomerService.CreateResult create(TenantId tenantId, String customerCode,
                                               String legalName, String currency) {
        return customerService.createCustomer(tenantId, customerRepository,
                customerCode, legalName, currency);
    }

    @Override
    public Optional<Customer> get(TenantId tenantId, CustomerId customerId) {
        return customerRepository.findByTenantAndId(tenantId, customerId);
    }

    @Override
    public ListResult list(TenantId tenantId, String status, String search, boolean includeDeleted) {
        if (search != null && !search.isBlank()) {
            return ListResult.ok(customerService.searchCustomers(tenantId, customerRepository, search));
        }
        if (status != null) {
            try {
                CustomerStatus customerStatus = CustomerStatus.valueOf(status.toUpperCase());
                return ListResult.ok(customerRepository.findByTenantAndStatus(tenantId, customerStatus));
            } catch (IllegalArgumentException e) {
                return ListResult.invalidStatus("Unknown status: " + status);
            }
        }
        return ListResult.ok(customerRepository.findAllByTenant(tenantId, includeDeleted));
    }

    @Override
    public Optional<Customer> update(TenantId tenantId, CustomerId customerId, UpdateCustomerCommand command) {
        return customerRepository.findByTenantAndId(tenantId, customerId)
                .map(customer -> {
                    if (command.displayName() != null) {
                        customer.updateDisplayName(command.displayName());
                    }
                    if (command.email() != null || command.phone() != null || command.billingAddress() != null) {
                        customer.updateContact(command.email(), command.phone(), command.billingAddress());
                    }
                    if (command.creditLimit() != null) {
                        customer.setCreditLimit(command.creditLimit());
                    }
                    if (command.paymentTerms() != null) {
                        customer.setPaymentTerms(command.paymentTerms());
                    }
                    customerRepository.save(tenantId, customer);
                    return customer;
                });
    }

    @Override
    public CustomerService.StatusResult block(TenantId tenantId, CustomerId customerId) {
        return customerService.blockCustomer(tenantId, customerRepository, customerId);
    }

    @Override
    public CustomerService.StatusResult unblock(TenantId tenantId, CustomerId customerId) {
        return customerService.unblockCustomer(tenantId, customerRepository, customerId);
    }

    @Override
    public CustomerService.StatusResult delete(TenantId tenantId, CustomerId customerId) {
        return customerService.deleteCustomer(tenantId, customerRepository, customerId);
    }

    @Override
    public CustomerService.CreditCheckResult checkCredit(TenantId tenantId, CustomerId customerId,
                                                          BigDecimal outstanding, BigDecimal invoiceAmount) {
        return customerService.checkCreditLimit(tenantId, customerRepository, customerId,
                outstanding, invoiceAmount);
    }

    @Override
    public CustomerStats stats(TenantId tenantId) {
        long active = customerRepository.countByTenantAndStatus(tenantId, CustomerStatus.ACTIVE);
        long blocked = customerRepository.countByTenantAndStatus(tenantId, CustomerStatus.BLOCKED);
        long deleted = customerRepository.countByTenantAndStatus(tenantId, CustomerStatus.DELETED);
        return new CustomerStats(active, blocked, deleted);
    }
}
