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
            String customerRef,
            String currencyCode,
            LocalDate dueDate,
            List<LineItem> lines
    ) {
        public record LineItem(String description, BigDecimal amount) {}
    }
}
