package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.PaymentQueryUseCase;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.payment.Payment;
import com.invoicegenie.ar.domain.model.payment.PaymentId;
import com.invoicegenie.ar.domain.model.payment.PaymentRepository;
import com.invoicegenie.shared.domain.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Application service: payment list/get.
 */
public class PaymentQueryService implements PaymentQueryUseCase {

    private final PaymentRepository paymentRepository;

    public PaymentQueryService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
    public Optional<Payment> get(TenantId tenantId, PaymentId paymentId) {
        return paymentRepository.findByTenantAndId(tenantId, paymentId);
    }

    @Override
    public ListResult list(TenantId tenantId, PaymentListFilter filter) {
        List<Payment> all;
        if (filter.unallocatedOnly() && filter.customerId() != null) {
            all = paymentRepository.findUnallocatedByTenantAndCustomer(
                    tenantId, CustomerId.of(filter.customerId()));
        } else if (filter.customerId() != null) {
            all = paymentRepository.findByTenantAndCustomer(tenantId, CustomerId.of(filter.customerId()));
        } else {
            all = paymentRepository.findByTenant(tenantId, filter.limit());
        }

        List<Payment> filtered = all.stream()
                .filter(p -> filter.status() == null || p.getStatus() == filter.status())
                .filter(p -> filter.fromDate() == null || !p.getPaymentDate().isBefore(filter.fromDate()))
                .filter(p -> filter.toDate() == null || !p.getPaymentDate().isAfter(filter.toDate()))
                .filter(p -> !filter.unallocatedOnly() || p.getAmountUnallocated().getAmount().signum() > 0)
                .limit(filter.limit())
                .collect(Collectors.toList());

        return new ListResult(filtered, filtered.size());
    }
}
