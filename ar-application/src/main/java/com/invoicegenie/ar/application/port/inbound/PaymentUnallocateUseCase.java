package com.invoicegenie.ar.application.port.inbound;

import com.invoicegenie.ar.domain.model.payment.PaymentId;
import com.invoicegenie.shared.domain.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Inbound port: reverse specific payment allocation(s) while payment stays RECEIVED (STORY-013).
 */
public interface PaymentUnallocateUseCase {

    /**
     * Unallocates payment from the given invoices.
     *
     * @param expectedVersion optional optimistic lock on payment version (null = no client version check)
     */
    Optional<UnallocateResult> unallocate(TenantId tenantId, PaymentId paymentId,
                                          List<UUID> invoiceIds, String reason,
                                          Long expectedVersion);

    record UnallocateResult(
            PaymentId paymentId,
            String paymentStatus,
            List<UUID> unallocatedInvoiceIds,
            long paymentVersion,
            String message
    ) {}
}