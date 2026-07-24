package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.PaymentUnallocateUseCase;
import com.invoicegenie.ar.domain.exception.ConcurrencyConflictException;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.domain.model.outbox.AuditEntry;
import com.invoicegenie.ar.domain.model.outbox.AuditRepository;
import com.invoicegenie.ar.domain.model.payment.Payment;
import com.invoicegenie.ar.domain.model.payment.PaymentAllocation;
import com.invoicegenie.ar.domain.model.payment.PaymentId;
import com.invoicegenie.ar.domain.model.payment.PaymentRepository;
import com.invoicegenie.ar.domain.model.payment.PaymentStatus;
import com.invoicegenie.shared.domain.TenantId;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service: unallocate specific payment→invoice links without reversing the receipt.
 *
 * <p>Reuses invoice {@code reverseAllocation} / {@code refreshStatusAfterReversal} patterns
 * from {@link PaymentReversalService}. Payment remains RECEIVED; cash returns to unallocated
 * so controllers can reallocate via existing allocate endpoints.
 *
 * <p>No ledger entries: payment receipt already posted AR; allocation is subledger only.
 */
public class PaymentUnallocateService implements PaymentUnallocateUseCase {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final AuditRepository auditRepository;

    public PaymentUnallocateService(PaymentRepository paymentRepository,
                                    InvoiceRepository invoiceRepository,
                                    AuditRepository auditRepository) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.auditRepository = auditRepository;
    }

    @Override
    public Optional<UnallocateResult> unallocate(TenantId tenantId, PaymentId paymentId,
                                                 List<UUID> invoiceIds, String reason,
                                                 Long expectedVersion) {
        if (invoiceIds == null || invoiceIds.isEmpty()) {
            throw new IllegalArgumentException("invoiceIds is required and must not be empty");
        }

        Optional<Payment> opt = paymentRepository.findByTenantAndId(tenantId, paymentId);
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        Payment payment = opt.get();

        if (payment.getStatus() != PaymentStatus.RECEIVED) {
            throw new IllegalStateException(
                    "Only RECEIVED payments can be unallocated, current: " + payment.getStatus());
        }

        if (expectedVersion != null && expectedVersion != payment.getOriginalVersion()) {
            throw new ConcurrencyConflictException(
                    "Payment version conflict: expected " + expectedVersion
                            + " but current is " + payment.getOriginalVersion());
        }

        String before = snapshot(payment);
        List<UUID> affected = new ArrayList<>();

        for (UUID invUuid : invoiceIds) {
            InvoiceId invoiceId = InvoiceId.of(invUuid);
            Optional<PaymentAllocation> existing = payment.findAllocation(invoiceId);
            if (existing.isEmpty()) {
                throw new IllegalArgumentException(
                        "Payment has no allocation for invoice: " + invUuid);
            }
            PaymentAllocation removed = payment.unallocate(invoiceId);
            invoiceRepository.findByTenantAndId(tenantId, invoiceId).ifPresent(inv -> {
                inv.reverseAllocation(removed.getAmount());
                inv.refreshStatusAfterReversal();
                invoiceRepository.save(tenantId, inv);
            });
            affected.add(invUuid);
        }

        paymentRepository.save(tenantId, payment);

        String after = snapshot(payment);
        String reasonSafe = reason != null ? reason.replace("\"", "'") : "";
        auditRepository.save(tenantId, AuditEntry.transition(
                tenantId, "PAYMENT", paymentId.getValue(), payment.getPaymentNumber(),
                (UUID) null, "UNALLOCATE",
                before,
                String.format("{\"status\":\"%s\",\"reason\":\"%s\",\"unallocatedInvoiceIds\":%s,\"version\":%d}",
                        payment.getStatus(), reasonSafe, affected, payment.getVersion())));

        return Optional.of(new UnallocateResult(
                paymentId,
                payment.getStatus().name(),
                affected,
                payment.getVersion(),
                "Unallocated " + affected.size() + " invoice(s); payment remains RECEIVED"));
    }

    private static String snapshot(Payment payment) {
        StringBuilder allocs = new StringBuilder("[");
        boolean first = true;
        for (PaymentAllocation a : payment.getAllocations()) {
            if (!first) allocs.append(',');
            first = false;
            allocs.append("{\"invoiceId\":\"").append(a.getInvoiceId().getValue())
                    .append("\",\"amount\":\"").append(a.getAmount().getAmount().toPlainString())
                    .append("\"}");
        }
        allocs.append(']');
        return String.format(
                "{\"status\":\"%s\",\"unallocated\":\"%s\",\"version\":%d,\"allocations\":%s}",
                payment.getStatus(),
                payment.getAmountUnallocated().getAmount().toPlainString(),
                payment.getVersion(),
                allocs);
    }
}