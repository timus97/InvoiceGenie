package com.invoicegenie.ar.application.port.inbound;

import com.invoicegenie.ar.domain.model.customer.Customer;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.service.CustomerService;
import com.invoicegenie.shared.domain.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Inbound port: customer management operations.
 */
public interface CustomerUseCase {

    CustomerService.CreateResult create(TenantId tenantId, String customerCode, String legalName, String currency);

    Optional<Customer> get(TenantId tenantId, CustomerId customerId);

    /**
     * Lists customers with optional filters.
     *
     * @param status         status name (ACTIVE/BLOCKED/DELETED), or null
     * @param search         free-text search, or null
     * @param includeDeleted include soft-deleted when listing all
     */
    ListResult list(TenantId tenantId, String status, String search, boolean includeDeleted);

    Optional<Customer> update(TenantId tenantId, CustomerId customerId, UpdateCustomerCommand command);

    CustomerService.StatusResult block(TenantId tenantId, CustomerId customerId);

    CustomerService.StatusResult unblock(TenantId tenantId, CustomerId customerId);

    CustomerService.StatusResult delete(TenantId tenantId, CustomerId customerId);

    CustomerService.CreditCheckResult checkCredit(TenantId tenantId, CustomerId customerId,
                                                   BigDecimal outstanding, BigDecimal invoiceAmount);

    /**
     * System-calculated open AR for a customer (STORY-014).
     * Empty optional when customer not found.
     */
    Optional<ArSummary> arSummary(TenantId tenantId, CustomerId customerId);

    CustomerStats stats(TenantId tenantId);

    record UpdateCustomerCommand(
            String displayName,
            String email,
            String phone,
            String billingAddress,
            BigDecimal creditLimit,
            String paymentTerms
    ) {}

    record CustomerStats(long active, long blocked, long deleted) {}

    /**
     * Open AR summary by currency (STORY-014).
     */
    record ArSummary(
            String customerId,
            int openInvoiceCount,
            Map<String, CurrencyBalance> byCurrency,
            BigDecimal totalBalanceBaseCurrency,
            String baseCurrency
    ) {
        public record CurrencyBalance(
                String currency,
                int openCount,
                BigDecimal totalBilled,
                BigDecimal totalPaid,
                BigDecimal balance
        ) {}
    }

    /**
     * List outcome: either customers or an invalid-status error.
     */
    record ListResult(List<Customer> customers, boolean success, String errorMessage) {
        public static ListResult ok(List<Customer> customers) {
            return new ListResult(customers, true, null);
        }

        public static ListResult invalidStatus(String message) {
            return new ListResult(List.of(), false, message);
        }
    }
}
