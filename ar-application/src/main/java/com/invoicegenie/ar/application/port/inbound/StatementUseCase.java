package com.invoicegenie.ar.application.port.inbound;

import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.shared.domain.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Inbound port: customer AR statements (STORY-015).
 */
public interface StatementUseCase {

    /**
     * Open items statement as-of date. Emits StatementGenerated outbox event.
     */
    Optional<CustomerStatement> generate(TenantId tenantId, CustomerId customerId, LocalDate asOf);

    /**
     * CSV representation of a statement (header + rows).
     */
    String toCsv(CustomerStatement statement);

    record CustomerStatement(
            String customerId,
            String customerCode,
            String customerName,
            LocalDate asOfDate,
            List<OpenItem> openItems,
            BigDecimal totalBalance,
            String currency
    ) {}

    record OpenItem(
            String invoiceId,
            String invoiceNumber,
            LocalDate issueDate,
            LocalDate dueDate,
            int daysPastDue,
            BigDecimal total,
            BigDecimal amountPaid,
            BigDecimal balanceDue,
            String currency,
            String status
    ) {}
}