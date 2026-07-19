package com.invoicegenie.ar.application.port.inbound;

import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.shared.domain.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Inbound port: issue a new invoice (DRAFT → ISSUED).
 */
public interface IssueInvoiceUseCase {

    /**
     * @param tenantId from TenantContext (enforced by adapter)
     * @return issued invoice id
     */
    InvoiceId issue(TenantId tenantId, IssueInvoiceCommand command);

    /**
     * Issue with optional durable {@code Idempotency-Key}.
     * When the key is present and was seen before with the same payload, returns the prior invoice id.
     */
    InvoiceId issue(TenantId tenantId, IssueInvoiceCommand command, String idempotencyKey);

    record IssueInvoiceCommand(
            String invoiceNumber,
            String customerId,
            String customerRef,
            String currencyCode,
            LocalDate dueDate,
            List<LineItem> lines
    ) {
        public IssueInvoiceCommand {
            if (invoiceNumber == null || invoiceNumber.isBlank()) {
                throw new IllegalArgumentException("invoiceNumber is required");
            }
            if (customerId == null || customerId.isBlank()) {
                throw new IllegalArgumentException("customerId is required");
            }
            if (dueDate == null) {
                throw new IllegalArgumentException("dueDate is required");
            }
            if (lines == null || lines.isEmpty()) {
                throw new IllegalArgumentException("at least one line is required");
            }
            // Default customerRef to customerId for display when not provided
            if (customerRef == null || customerRef.isBlank()) {
                customerRef = customerId;
            }
        }
        public record LineItem(String description, BigDecimal amount) {}
    }
}
