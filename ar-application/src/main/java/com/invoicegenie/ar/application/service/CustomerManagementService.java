package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.CustomerUseCase;
import com.invoicegenie.ar.domain.model.customer.Customer;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.customer.CustomerRepository;
import com.invoicegenie.ar.domain.model.customer.CustomerStatus;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.domain.service.CustomerService;
import com.invoicegenie.shared.domain.TenantId;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Application service: customer management use cases.
 *
 * <p>Wraps {@link CustomerService} domain operations and repository access.
 */
public class CustomerManagementService implements CustomerUseCase {

    private final CustomerService customerService;
    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;

    public CustomerManagementService(CustomerService customerService,
                                     CustomerRepository customerRepository,
                                     InvoiceRepository invoiceRepository) {
        this.customerService = customerService;
        this.customerRepository = customerRepository;
        this.invoiceRepository = invoiceRepository;
    }

    /** Backward-compatible ctor for unit tests without AR summary. */
    public CustomerManagementService(CustomerService customerService,
                                     CustomerRepository customerRepository) {
        this(customerService, customerRepository, null);
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
        BigDecimal systemOutstanding = outstanding;
        // STORY-014: default outstanding from system AR when client sends 0/null
        if ((outstanding == null || outstanding.signum() == 0) && invoiceRepository != null) {
            systemOutstanding = arSummary(tenantId, customerId)
                    .map(ArSummary::totalBalanceBaseCurrency)
                    .orElse(BigDecimal.ZERO);
        }
        return customerService.checkCreditLimit(tenantId, customerRepository, customerId,
                systemOutstanding != null ? systemOutstanding : BigDecimal.ZERO, invoiceAmount);
    }

    @Override
    public Optional<ArSummary> arSummary(TenantId tenantId, CustomerId customerId) {
        if (customerRepository.findByTenantAndId(tenantId, customerId).isEmpty()) {
            return Optional.empty();
        }
        if (invoiceRepository == null) {
            return Optional.of(new ArSummary(customerId.getValue().toString(), 0, Map.of(),
                    BigDecimal.ZERO, "USD"));
        }
        List<Invoice> open = invoiceRepository.findOpenByTenantAndCustomer(tenantId, customerId);
        Map<String, Agg> aggs = new HashMap<>();
        for (Invoice inv : open) {
            String ccy = inv.getCurrencyCode() != null ? inv.getCurrencyCode() : "USD";
            Agg a = aggs.computeIfAbsent(ccy, k -> new Agg());
            a.count++;
            a.billed = a.billed.add(inv.getTotal().getAmount());
            a.paid = a.paid.add(inv.getAmountPaid() != null ? inv.getAmountPaid().getAmount() : BigDecimal.ZERO);
            a.balance = a.balance.add(inv.getBalanceDue().getAmount());
        }
        Map<String, ArSummary.CurrencyBalance> byCcy = new HashMap<>();
        BigDecimal totalBase = BigDecimal.ZERO;
        String base = "USD";
        for (var e : aggs.entrySet()) {
            Agg a = e.getValue();
            byCcy.put(e.getKey(), new ArSummary.CurrencyBalance(
                    e.getKey(), a.count, a.billed, a.paid, a.balance));
            // Prefer customer base currency if single; else sum balances (multi-ccy reported separately)
            totalBase = totalBase.add(a.balance);
            base = e.getKey();
        }
        if (byCcy.size() > 1) {
            // Multi-currency: totalBalanceBaseCurrency is sum of balances (not FX-converted)
            base = "MIXED";
        } else if (byCcy.isEmpty()) {
            base = "USD";
            totalBase = BigDecimal.ZERO;
        }
        return Optional.of(new ArSummary(
                customerId.getValue().toString(),
                open.size(),
                byCcy,
                totalBase,
                base
        ));
    }

    private static final class Agg {
        int count;
        BigDecimal billed = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal balance = BigDecimal.ZERO;
    }

    @Override
    public CustomerStats stats(TenantId tenantId) {
        long active = customerRepository.countByTenantAndStatus(tenantId, CustomerStatus.ACTIVE);
        long blocked = customerRepository.countByTenantAndStatus(tenantId, CustomerStatus.BLOCKED);
        long deleted = customerRepository.countByTenantAndStatus(tenantId, CustomerStatus.DELETED);
        return new CustomerStats(active, blocked, deleted);
    }
}
