package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.PaymentAllocationUseCase;
import com.invoicegenie.ar.application.port.outbound.EventPublisher;
import com.invoicegenie.ar.application.port.outbound.IdempotencyStore;
import com.invoicegenie.ar.domain.event.PaymentAllocated;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.domain.model.payment.Payment;
import com.invoicegenie.ar.domain.model.payment.PaymentAllocation;
import com.invoicegenie.ar.domain.model.payment.PaymentId;
import com.invoicegenie.ar.domain.model.payment.PaymentRepository;
import com.invoicegenie.ar.domain.service.PaymentAllocationEngine;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application service: payment allocation operations.
 *
 * <p>Features:
 * <ul>
 *   <li>FIFO auto-allocation</li>
 *   <li>Manual allocation</li>
 *   <li>Durable idempotency support</li>
 *   <li>Concurrency safety via optimistic locking</li>
 *   <li>One payment can be allocated to multiple invoices</li>
 *   <li>Cumulative {@code amountPaid} on invoices (prevents over-allocation)</li>
 *   <li>Publishes {@link PaymentAllocated} events after successful allocation</li>
 * </ul>
 */
public class PaymentAllocationService implements PaymentAllocationUseCase {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final EventPublisher eventPublisher;
    private final IdempotencyStore idempotencyStore;
    private final PaymentAllocationEngine allocationEngine;

    public PaymentAllocationService(
            PaymentRepository paymentRepository,
            InvoiceRepository invoiceRepository,
            EventPublisher eventPublisher,
            IdempotencyStore idempotencyStore) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.eventPublisher = eventPublisher;
        this.idempotencyStore = idempotencyStore;
        this.allocationEngine = new PaymentAllocationEngine();
    }

    @Override
    public Optional<AllocationResult> autoAllocateFIFO(
            TenantId tenantId,
            PaymentId paymentId,
            UUID allocatedBy,
            String idempotencyKey) {

        String storeKey = null;
        String requestHash = null;
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            storeKey = "alloc:" + paymentId.getValue() + ":" + idempotencyKey;
            requestHash = hash("auto|" + paymentId.getValue());
            Optional<AllocationResult> cached = loadCached(tenantId, storeKey, requestHash);
            if (cached.isPresent()) {
                return cached;
            }
        }

        Optional<Payment> paymentOpt = paymentRepository.findByTenantAndId(tenantId, paymentId);
        if (paymentOpt.isEmpty()) {
            return Optional.empty();
        }
        Payment payment = paymentOpt.get();
        String paymentCurrency = payment.getAmount().getCurrencyCode();

        // Same-currency only (STORY-010)
        List<Invoice> customerInvoices = invoiceRepository.findOpenByTenantAndCustomer(
                tenantId, payment.getCustomerId()).stream()
                .filter(inv -> paymentCurrency.equalsIgnoreCase(inv.getCurrencyCode()))
                .toList();

        PaymentAllocationEngine.AllocationResult engineResult = allocationEngine.autoAllocateFIFO(
                tenantId, payment, customerInvoices, allocatedBy);

        if (!engineResult.allocations().isEmpty()) {
            paymentRepository.save(tenantId, payment);

            // Apply each allocation to invoice aggregate (cumulative amountPaid)
            for (PaymentAllocation allocation : engineResult.allocations()) {
                customerInvoices.stream()
                        .filter(inv -> inv.getId().equals(allocation.getInvoiceId()))
                        .findFirst()
                        .ifPresent(invoice -> {
                            invoice.recordPaymentApplied(allocation.getAmount());
                            invoiceRepository.save(tenantId, invoice);
                        });
            }

            publishEvents(engineResult.events());
        }

        AllocationResult result = toResult(payment, engineResult);
        storeIfNeeded(tenantId, storeKey, requestHash, result);
        return Optional.of(result);
    }

    @Override
    public Optional<AllocationResult> manualAllocate(
            TenantId tenantId,
            PaymentId paymentId,
            List<ManualAllocationRequest> requests,
            UUID allocatedBy,
            String idempotencyKey) {

        String storeKey = null;
        String requestHash = null;
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            storeKey = "alloc:" + paymentId.getValue() + ":" + idempotencyKey;
            requestHash = hash("manual|" + paymentId.getValue() + "|" + requests);
            Optional<AllocationResult> cached = loadCached(tenantId, storeKey, requestHash);
            if (cached.isPresent()) {
                return cached;
            }
        }

        Optional<Payment> paymentOpt = paymentRepository.findByTenantAndId(tenantId, paymentId);
        if (paymentOpt.isEmpty()) {
            return Optional.empty();
        }
        Payment payment = paymentOpt.get();

        String paymentCurrency = payment.getAmount().getCurrencyCode();
        Map<InvoiceId, Invoice> invoicesById = new HashMap<>();
        Map<InvoiceId, Money> remainingByInvoice = new HashMap<>();
        List<Invoice> invoices = new ArrayList<>();
        List<PaymentAllocationEngine.ManualAllocationRequest> engineRequests = new ArrayList<>();
        List<String> preErrors = new ArrayList<>();

        for (ManualAllocationRequest request : requests) {
            // Force payment currency (REST may hardcode incorrectly)
            Money amount = Money.of(request.amount().getAmount().toPlainString(), paymentCurrency);

            Invoice invoice = invoicesById.computeIfAbsent(request.invoiceId(), id ->
                    invoiceRepository.findByTenantAndId(tenantId, id).orElse(null));
            if (invoice == null) {
                preErrors.add("Invoice not found: " + request.invoiceId().getValue());
                continue;
            }
            if (!paymentCurrency.equalsIgnoreCase(invoice.getCurrencyCode())) {
                preErrors.add("Currency mismatch: payment is " + paymentCurrency
                        + " but invoice " + request.invoiceId().getValue()
                        + " is " + invoice.getCurrencyCode()
                        + " (same-currency allocation only)");
                continue;
            }
            if (!invoices.contains(invoice)) {
                invoices.add(invoice);
            }
            if (!invoice.canReceivePayments()) {
                preErrors.add("Invoice cannot receive payments: " + request.invoiceId().getValue());
                continue;
            }
            Money remaining = remainingByInvoice.computeIfAbsent(request.invoiceId(),
                    id -> invoice.getBalanceDue());
            if (amount.getAmount().compareTo(remaining.getAmount()) > 0) {
                preErrors.add("Allocation exceeds balance due for invoice "
                        + request.invoiceId().getValue()
                        + " (requested " + amount.getAmount()
                        + ", remaining " + remaining.getAmount() + ")");
                continue;
            }
            remainingByInvoice.put(request.invoiceId(), remaining.subtract(amount));
            engineRequests.add(new PaymentAllocationEngine.ManualAllocationRequest(
                    request.invoiceId(), amount, request.notes()));
        }

        PaymentAllocationEngine.AllocationResult engineResult = engineRequests.isEmpty()
                ? new PaymentAllocationEngine.AllocationResult(
                        List.of(),
                        Money.of("0.00", paymentCurrency),
                        payment.getAmountUnallocated(),
                        List.of(),
                        List.of())
                : allocationEngine.manualAllocate(
                        tenantId, payment, invoices, engineRequests, allocatedBy);

        if (!engineResult.allocations().isEmpty()) {
            paymentRepository.save(tenantId, payment);

            for (PaymentAllocation allocation : engineResult.allocations()) {
                Invoice invoice = invoicesById.get(allocation.getInvoiceId());
                if (invoice != null) {
                    invoice.recordPaymentApplied(allocation.getAmount());
                    invoiceRepository.save(tenantId, invoice);
                }
            }

            publishEvents(engineResult.events());
        }

        if (!preErrors.isEmpty()) {
            List<String> allErrors = new ArrayList<>(preErrors);
            engineResult.errors().forEach(e -> allErrors.add(e.reason()));
            AllocationResult result = new AllocationResult(
                    payment.getId(),
                    engineResult.allocations().stream()
                            .map(a -> new AllocationResult.AllocationDetail(
                                    a.getInvoiceId(), a.getAmount(), a.getId()))
                            .toList(),
                    engineResult.totalAllocated(),
                    engineResult.remainingUnallocated(),
                    allErrors,
                    payment.getVersion()
            );
            storeIfNeeded(tenantId, storeKey, requestHash, result);
            return Optional.of(result);
        }

        AllocationResult result = toResult(payment, engineResult);
        storeIfNeeded(tenantId, storeKey, requestHash, result);
        return Optional.of(result);
    }

    @Override
    public Optional<AllocationResult> getAllocations(TenantId tenantId, PaymentId paymentId) {
        return paymentRepository.findByTenantAndId(tenantId, paymentId)
                .map(payment -> {
                    List<AllocationResult.AllocationDetail> details = payment.getAllocations().stream()
                            .map(a -> new AllocationResult.AllocationDetail(
                                    a.getInvoiceId(), a.getAmount(), a.getId()))
                            .toList();

                    return new AllocationResult(
                            payment.getId(),
                            details,
                            payment.getAmount().subtract(payment.getAmountUnallocated()),
                            payment.getAmountUnallocated(),
                            List.of(),
                            payment.getVersion()
                    );
                });
    }

    @Override
    public List<AllocationResult.AllocationDetail> getInvoiceAllocations(TenantId tenantId, InvoiceId invoiceId) {
        return paymentRepository.findAllocationsByTenantAndInvoice(tenantId, invoiceId).stream()
                .map(a -> new AllocationResult.AllocationDetail(a.getInvoiceId(), a.getAmount(), a.getId()))
                .toList();
    }

    private void publishEvents(List<PaymentAllocated> events) {
        if (events == null) {
            return;
        }
        for (PaymentAllocated event : events) {
            eventPublisher.publish(event);
        }
    }

    private AllocationResult toResult(Payment payment, PaymentAllocationEngine.AllocationResult engineResult) {
        List<AllocationResult.AllocationDetail> details = engineResult.allocations().stream()
                .map(a -> new AllocationResult.AllocationDetail(a.getInvoiceId(), a.getAmount(), a.getId()))
                .toList();

        List<String> errors = engineResult.errors().stream()
                .map(e -> e.reason())
                .toList();

        return new AllocationResult(
                payment.getId(),
                details,
                engineResult.totalAllocated(),
                engineResult.remainingUnallocated(),
                errors,
                payment.getVersion()
        );
    }

    private Optional<AllocationResult> loadCached(TenantId tenantId, String storeKey, String requestHash) {
        return idempotencyStore.find(tenantId, storeKey)
                .map(record -> {
                    if (!record.requestHash().equals(requestHash)) {
                        throw new IllegalArgumentException(
                                "Idempotency-Key reused with different request payload: " + storeKey);
                    }
                    return deserialize(record.responseJson());
                });
    }

    private void storeIfNeeded(TenantId tenantId, String storeKey, String requestHash, AllocationResult result) {
        if (storeKey != null && requestHash != null) {
            idempotencyStore.put(tenantId, storeKey, requestHash, serialize(result));
        }
    }

    /**
     * Compact pipe-delimited serialization (no Jackson dependency in ar-application).
     * Format: paymentId|currency|total|remaining|version|errorCount|err1;err2|allocCount|inv,amt,allocId;...
     */
    static String serialize(AllocationResult result) {
        String currency = result.totalAllocated().getCurrencyCode();
        String errors = result.errors().stream()
                .map(e -> e.replace(";", "%3B").replace("|", "%7C"))
                .collect(Collectors.joining(";"));
        String allocs = result.allocations().stream()
                .map(a -> a.invoiceId().getValue() + "," + a.amount().getAmount().toPlainString()
                        + "," + a.allocationId())
                .collect(Collectors.joining(";"));
        return result.paymentId().getValue()
                + "|" + currency
                + "|" + result.totalAllocated().getAmount().toPlainString()
                + "|" + result.remainingUnallocated().getAmount().toPlainString()
                + "|" + result.paymentVersion()
                + "|" + result.errors().size()
                + "|" + errors
                + "|" + result.allocations().size()
                + "|" + allocs;
    }

    static AllocationResult deserialize(String json) {
        String[] parts = json.split("\\|", -1);
        if (parts.length < 9) {
            throw new IllegalStateException("Corrupt idempotency payload");
        }
        PaymentId paymentId = PaymentId.of(UUID.fromString(parts[0]));
        String currency = parts[1];
        Money total = Money.of(parts[2], currency);
        Money remaining = Money.of(parts[3], currency);
        long version = Long.parseLong(parts[4]);
        List<String> errors = new ArrayList<>();
        if (!parts[6].isBlank()) {
            for (String e : parts[6].split(";", -1)) {
                if (!e.isBlank()) {
                    errors.add(e.replace("%3B", ";").replace("%7C", "|"));
                }
            }
        }
        List<AllocationResult.AllocationDetail> details = new ArrayList<>();
        if (!parts[8].isBlank()) {
            for (String a : parts[8].split(";", -1)) {
                if (a.isBlank()) continue;
                String[] f = a.split(",", -1);
                details.add(new AllocationResult.AllocationDetail(
                        InvoiceId.of(UUID.fromString(f[0])),
                        Money.of(new BigDecimal(f[1]), currency),
                        UUID.fromString(f[2])));
            }
        }
        return new AllocationResult(paymentId, details, total, remaining, errors, version);
    }

    private static String hash(String payload) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(payload.hashCode());
        }
    }
}
