package com.invoicegenie.ar.application.port.inbound;

import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.shared.domain.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
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

    record IssueInvoiceCommand(
            String customerRef,
            String currencyCode,
            Instant dueDate,
            List<LineItem> lines
    ) {
        public record LineItem(String description, BigDecimal amount) {}
    }
}
