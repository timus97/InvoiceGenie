package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.PaymentReversalUseCase;
import com.invoicegenie.ar.application.port.outbound.IdempotencyStore;
import com.invoicegenie.ar.domain.exception.IdempotencyConflictException;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.domain.model.ledger.LedgerRepository;
import com.invoicegenie.ar.domain.model.outbox.AuditEntry;
import com.invoicegenie.ar.domain.model.outbox.AuditRepository;
import com.invoicegenie.ar.domain.model.payment.Payment;
import com.invoicegenie.ar.domain.model.payment.PaymentId;
import com.invoicegenie.ar.domain.model.payment.PaymentRepository;
import com.invoicegenie.ar.domain.model.payment.PaymentStatus;
import com.invoicegenie.ar.domain.service.LedgerService;
import com.invoicegenie.shared.domain.TenantId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service: reverse/refund RECEIVED payments, unwind allocations, reverse ledger.
 */
public class PaymentReversalService implements PaymentReversalUseCase {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final LedgerService ledgerService;
    private final LedgerRepository ledgerRepository;
    private final AuditRepository auditRepository;
    private final IdempotencyStore idempotencyStore;

    public PaymentReversalService(PaymentRepository paymentRepository,
                                  InvoiceRepository invoiceRepository,
                                  LedgerService ledgerService,
                                  LedgerRepository ledgerRepository,
                                  AuditRepository auditRepository,
                                  IdempotencyStore idempotencyStore) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.ledgerService = ledgerService;
        this.ledgerRepository = ledgerRepository;
        this.auditRepository = auditRepository;
        this.idempotencyStore = idempotencyStore;
    }

    @Override
    public Optional<ReversalResult> reverse(TenantId tenantId, PaymentId paymentId, String reason, String idempotencyKey) {
        return reverseInternal(tenantId, paymentId, reason, idempotencyKey, false);
    }

    @Override
    public Optional<ReversalResult> refund(TenantId tenantId, PaymentId paymentId, String reason, String idempotencyKey) {
        return reverseInternal(tenantId, paymentId, reason, idempotencyKey, true);
    }

    private Optional<ReversalResult> reverseInternal(TenantId tenantId, PaymentId paymentId,
                                                     String reason, String idempotencyKey, boolean refund) {
        String action = refund ? "refund" : "reverse";
        String storeKey = null;
        String requestHash = null;
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            storeKey = "payment-" + action + ":" + paymentId.getValue() + ":" + idempotencyKey;
            requestHash = hash(action + "|" + paymentId.getValue() + "|" + reason);
            var existing = idempotencyStore.find(tenantId, storeKey);
            if (existing.isPresent()) {
                if (!existing.get().requestHash().equals(requestHash)) {
                    throw new IdempotencyConflictException(
                            "Idempotency-Key reused with different payload for payment " + action);
                }
                return Optional.of(new ReversalResult(paymentId, existing.get().responseJson(), List.of(),
                        "Idempotent replay"));
            }
        }

        Optional<Payment> opt = paymentRepository.findByTenantAndId(tenantId, paymentId);
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        Payment payment = opt.get();
        if (payment.getStatus() != PaymentStatus.RECEIVED) {
            throw new IllegalStateException("Only RECEIVED payments can be " + action + "d, current: " + payment.getStatus());
        }

        List<UUID> affected = new ArrayList<>();
        for (var allocation : payment.getAllocations()) {
            affected.add(allocation.getInvoiceId().getValue());
            invoiceRepository.findByTenantAndId(tenantId, allocation.getInvoiceId()).ifPresent(inv -> {
                inv.reverseAllocation(allocation.getAmount());
                inv.refreshStatusAfterReversal();
                invoiceRepository.save(tenantId, inv);
            });
        }

        if (refund) {
            payment.refund();
        } else {
            payment.reverse();
        }
        paymentRepository.save(tenantId, payment);

        LedgerService.TransactionResult ledgerTx = ledgerService.recordPaymentReversal(
                tenantId, paymentId.getValue(), payment.getPaymentNumber(), payment.getAmount());
        ledgerService.assertBalanced(ledgerTx.entries());
        ledgerRepository.saveAll(tenantId, ledgerTx.entries());

        String after = String.format("{\"status\":\"%s\",\"reason\":\"%s\"}", payment.getStatus(), reason);
        auditRepository.save(tenantId, AuditEntry.transition(
                tenantId, "PAYMENT", paymentId.getValue(), payment.getPaymentNumber(),
                (UUID) null, action.toUpperCase(), "{\"status\":\"RECEIVED\"}", after));

        ReversalResult result = new ReversalResult(paymentId, payment.getStatus().name(), affected,
                "Payment " + action + "d successfully");
        if (storeKey != null) {
            idempotencyStore.put(tenantId, storeKey, requestHash, payment.getStatus().name());
        }
        return Optional.of(result);
    }

    private static String hash(String payload) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(payload.hashCode());
        }
    }
}
