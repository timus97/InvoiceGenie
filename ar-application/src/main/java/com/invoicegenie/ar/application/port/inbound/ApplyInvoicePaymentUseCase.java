package com.invoicegenie.ar.application.port.inbound;

import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.shared.domain.TenantId;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Inbound port: apply a payment directly to a single invoice.
 *
 * <p>Creates a real {@code Payment} aggregate and allocates it to the target invoice
 * (unifying the legacy "mark paid" shortcut with the payment/allocation path).
 */
public interface ApplyInvoicePaymentUseCase {

    /**
     * Applies a payment to an invoice.
     *
     * @param tenantId  tenant from TenantContext
     * @param invoiceId target invoice
     * @param command   amount (optional when fullyPaid) and fullyPaid flag
     * @return updated invoice, or empty if invoice not found
     */
    Optional<Invoice> apply(TenantId tenantId, InvoiceId invoiceId, ApplyPaymentCommand command);

    /**
     * @param amount    optional payment amount; required when fullyPaid is false/null
     * @param fullyPaid when true (default if amount is null), pays remaining balance
     */
    record ApplyPaymentCommand(BigDecimal amount, Boolean fullyPaid) {
        public boolean isFullyPaid() {
            if (fullyPaid != null) {
                return fullyPaid;
            }
            // Default: full pay when no amount specified (legacy {"fullyPaid": true} / empty body)
            return amount == null;
        }
    }
}
