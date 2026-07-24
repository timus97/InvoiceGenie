package com.invoicegenie.ar.application.port.inbound;

import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.shared.domain.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Inbound port: invoice lifecycle operations.
 */
public interface InvoiceLifecycleUseCase {

    Optional<Invoice> issue(TenantId tenantId, InvoiceId invoiceId);

    Optional<Invoice> markOverdue(TenantId tenantId, InvoiceId invoiceId, LocalDate today);

    Optional<Invoice> writeOff(TenantId tenantId, InvoiceId invoiceId, String reason);

    Optional<Invoice> applyPayment(TenantId tenantId, InvoiceId invoiceId, boolean fullyPaid);

    Optional<Invoice> updateDueDate(TenantId tenantId, InvoiceId invoiceId, LocalDate newDueDate);

    /**
     * Update DRAFT invoice fields/lines (STORY-011). Rejects non-DRAFT.
     */
    Optional<Invoice> updateDraft(TenantId tenantId, InvoiceId invoiceId, UpdateDraftCommand command);

    /**
     * Reopen invoice after payment reversal (e.g., cheque bounce).
     * Reverts status from PAID/PARTIALLY_PAID back to ISSUED.
     */
    Optional<Invoice> reopen(TenantId tenantId, InvoiceId invoiceId, String reason);

    record UpdateDraftCommand(
            LocalDate dueDate,
            String notes,
            String terms,
            String customerRef,
            List<DraftLine> lines
    ) {
        public record DraftLine(
                String description,
                BigDecimal amount,
                BigDecimal quantity,
                BigDecimal unitPrice,
                BigDecimal discountAmount,
                BigDecimal taxRate
        ) {}
    }
}
