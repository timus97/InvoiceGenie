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

    /**
     * @param issueImmediately when null or true, create as ISSUED with ledger (default, backward compatible).
     *                         When false, persist pure DRAFT with no ledger / InvoiceIssued event.
     */
    record IssueInvoiceCommand(
            String invoiceNumber,
            String customerId,
            String customerRef,
            String currencyCode,
            LocalDate dueDate,
            List<LineItem> lines,
            Boolean issueImmediately
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
            if (issueImmediately == null) {
                issueImmediately = Boolean.TRUE;
            }
        }

        /** Backward-compatible constructor — issues immediately. */
        public IssueInvoiceCommand(
                String invoiceNumber,
                String customerId,
                String customerRef,
                String currencyCode,
                LocalDate dueDate,
                List<LineItem> lines) {
            this(invoiceNumber, customerId, customerRef, currencyCode, dueDate, lines, true);
        }

        public boolean shouldIssueImmediately() {
            return issueImmediately == null || issueImmediately;
        }

        /**
         * Line create payload. Prefer quantity+unitPrice (+ optional discount/taxRate);
         * {@code amount} alone remains supported for simple lines.
         */
        public record LineItem(
                String description,
                BigDecimal amount,
                BigDecimal quantity,
                BigDecimal unitPrice,
                BigDecimal discountAmount,
                BigDecimal taxRate
        ) {
            public LineItem {
                if (description == null || description.isBlank()) {
                    throw new IllegalArgumentException("line description is required");
                }
                boolean rich = quantity != null && unitPrice != null;
                if (!rich && (amount == null || amount.signum() <= 0)) {
                    throw new IllegalArgumentException("line amount or quantity+unitPrice is required");
                }
            }

            /** Simple description + amount (backward compatible). */
            public LineItem(String description, BigDecimal amount) {
                this(description, amount, null, null, null, null);
            }
        }
    }
}
