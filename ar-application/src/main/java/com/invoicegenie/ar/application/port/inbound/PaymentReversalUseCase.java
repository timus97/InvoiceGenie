package com.invoicegenie.ar.application.port.inbound;

import com.invoicegenie.ar.domain.model.payment.PaymentId;
import com.invoicegenie.shared.domain.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Inbound port: reverse or refund a RECEIVED payment (unwinds allocations + ledger).
 */
public interface PaymentReversalUseCase {

    Optional<ReversalResult> reverse(TenantId tenantId, PaymentId paymentId, String reason, String idempotencyKey);

    Optional<ReversalResult> refund(TenantId tenantId, PaymentId paymentId, String reason, String idempotencyKey);

    record ReversalResult(
            PaymentId paymentId,
            String newStatus,
            List<UUID> affectedInvoiceIds,
            String message
    ) {}
}
