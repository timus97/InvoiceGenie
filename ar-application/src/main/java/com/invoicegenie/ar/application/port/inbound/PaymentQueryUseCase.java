package com.invoicegenie.ar.application.port.inbound;

import com.invoicegenie.ar.domain.model.payment.Payment;
import com.invoicegenie.ar.domain.model.payment.PaymentId;
import com.invoicegenie.ar.domain.model.payment.PaymentStatus;
import com.invoicegenie.shared.domain.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Inbound port: payment query and cash application reverse/refund.
 */
public interface PaymentQueryUseCase {

    Optional<Payment> get(TenantId tenantId, PaymentId paymentId);

    ListResult list(TenantId tenantId, PaymentListFilter filter);

    record PaymentListFilter(
            UUID customerId,
            PaymentStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            boolean unallocatedOnly,
            int limit
    ) {
        public PaymentListFilter {
            if (limit <= 0) {
                limit = 50;
            }
            if (limit > 200) {
                limit = 200;
            }
        }
    }

    record ListResult(List<Payment> items, int count) {}
}
