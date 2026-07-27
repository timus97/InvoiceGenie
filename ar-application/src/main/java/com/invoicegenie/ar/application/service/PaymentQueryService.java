package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.PaymentQueryUseCase;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.payment.Payment;
import com.invoicegenie.ar.domain.model.payment.PaymentId;
import com.invoicegenie.ar.domain.model.payment.PaymentRepository;
import com.invoicegenie.shared.domain.TenantId;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Application service: payment list/get with cursor pagination (PP-023).
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
        boolean hasFilters = filter.customerId() != null
                || filter.status() != null
                || filter.fromDate() != null
                || filter.toDate() != null
                || filter.unallocatedOnly();

        // Customer / unallocated specialized queries (legacy in-memory filters)
        if (filter.unallocatedOnly() && filter.customerId() != null) {
            List<Payment> all = paymentRepository.findUnallocatedByTenantAndCustomer(
                    tenantId, CustomerId.of(filter.customerId()));
            return filterInMemory(all, filter, null);
        }
        if (filter.customerId() != null) {
            List<Payment> all = paymentRepository.findByTenantAndCustomer(
                    tenantId, CustomerId.of(filter.customerId()));
            return filterInMemory(all, filter, null);
        }

        // Cursor path for tenant-wide list (PP-023). When filters need post-processing
        // and no cursor is supplied, keep the simple list API used by existing tests.
        if (!hasFilters || (filter.cursor() != null && !filter.cursor().isBlank())) {
            PaymentRepository.PageCursor pageCursor = decodeCursor(filter.cursor());
            PaymentRepository.Page page = paymentRepository.findByTenant(tenantId, filter.limit(), pageCursor);
            List<Payment> filtered = applyFilters(page.items(), filter);
            String next = page.nextCursor().map(this::encodeCursor).orElse(null);
            return new ListResult(filtered, filtered.size(), next);
        }

        // Filtered tenant list without cursor: 2-arg find (backward compatible)
        List<Payment> all = paymentRepository.findByTenant(tenantId, filter.limit());
        return filterInMemory(all, filter, null);
    }

    private ListResult filterInMemory(List<Payment> all, PaymentListFilter filter, String nextCursor) {
        List<Payment> filtered = applyFilters(all, filter);
        return new ListResult(filtered, filtered.size(), nextCursor);
    }

    private static List<Payment> applyFilters(List<Payment> all, PaymentListFilter filter) {
        return all.stream()
                .filter(p -> filter.status() == null || p.getStatus() == filter.status())
                .filter(p -> filter.fromDate() == null || !p.getPaymentDate().isBefore(filter.fromDate()))
                .filter(p -> filter.toDate() == null || !p.getPaymentDate().isAfter(filter.toDate()))
                .filter(p -> !filter.unallocatedOnly() || p.getAmountUnallocated().getAmount().signum() > 0)
                .limit(filter.limit())
                .collect(Collectors.toList());
    }

    private PaymentRepository.PageCursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor));
            String[] parts = decoded.split("\\|", 2);
            if (parts.length == 2) {
                return new PaymentRepository.PageCursor(
                        java.time.Instant.parse(parts[0]),
                        PaymentId.of(java.util.UUID.fromString(parts[1])));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String encodeCursor(PaymentRepository.PageCursor c) {
        String raw = c.createdAt().toString() + "|" + c.id().getValue().toString();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes());
    }
}