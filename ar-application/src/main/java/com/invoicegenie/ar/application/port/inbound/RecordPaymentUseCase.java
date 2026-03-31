package com.invoicegenie.ar.application.port.inbound;

import com.invoicegenie.ar.domain.model.payment.PaymentId;
import com.invoicegenie.ar.domain.model.payment.PaymentMethod;
import com.invoicegenie.shared.domain.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Inbound port: record a new payment received from a customer.
 * 
 * <p>This is the entry point for creating a Payment aggregate. The payment is
 * created in RECEIVED status and can later be allocated to invoices.
 * 
 * <p>Customer must be identified at payment creation time (required field).
 * 
 * <p>Features:
 * <ul>
 *   <li>Idempotency: same idempotency key returns existing payment</li>
 *   <li>Validates customer exists (application layer)</li>
 *   <li>Creates audit log entry</li>
 * </ul>
 */
public interface RecordPaymentUseCase {

    /**
     * Records a new payment received from a customer.
     * 
     * @param tenantId the tenant (from TenantContext)
     * @param command the payment creation command
     * @return the created payment ID
     * @throws IllegalArgumentException if validation fails
     */
    PaymentId record(TenantId tenantId, RecordPaymentCommand command);

    /**
     * Command to record a payment.
     */
    record RecordPaymentCommand(
            String paymentNumber,
            String customerId,
            BigDecimal amount,
            String currencyCode,
            LocalDate paymentDate,
            PaymentMethod method,
            String reference,
            String notes
    ) {
        public RecordPaymentCommand {
            if (paymentNumber == null || paymentNumber.isBlank()) {
                throw new IllegalArgumentException("paymentNumber is required");
            }
            if (customerId == null || customerId.isBlank()) {
                throw new IllegalArgumentException("customerId is required");
            }
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("amount must be positive");
            }
            if (paymentDate == null) {
                throw new IllegalArgumentException("paymentDate is required");
            }
            if (method == null) {
                throw new IllegalArgumentException("method is required");
            }
        }
    }
}
