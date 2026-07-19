package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.PaymentAllocationUseCase;
import com.invoicegenie.ar.application.port.outbound.EventPublisher;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Application service: payment allocation operations.
 *
 * <p>Features:
 * <ul>
 *   <li>FIFO auto-allocation</li>
 *   <li>Manual allocation</li>
 *   <li>Idempotency support</li>
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
    private final PaymentAllocationEngine allocationEngine;

    // Simple in-memory idempotency store (in production, use database table)
    private final ConcurrentMap<String, AllocationResult> idempotencyCache = new ConcurrentHashMap<>();

    public PaymentAllocationService(
            PaymentRepository paymentRepository,
            InvoiceRepository invoiceRepository,
            EventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.eventPublisher = eventPublisher;
        this.allocationEngine = new PaymentAllocationEngine();
    }

    @Override
    public Optional<AllocationResult> autoAllocateFIFO(
            TenantId tenantId,
            PaymentId paymentId,
            UUID allocatedBy,
            String idempotencyKey) {

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String key = tenantId.getValue() + ":" + paymentId.getValue() + ":" + idempotencyKey;
            AllocationResult cached = idempotencyCache.get(key);
            if (cached != null) {
                return Optional.of(cached);
            }
        }

        Optional<Payment> paymentOpt = paymentRepository.findByTenantAndId(tenantId, paymentId);
        if (paymentOpt.isEmpty()) {
            return Optional.empty();
        }
        Payment payment = paymentOpt.get();

        List<Invoice> customerInvoices = invoiceRepository.findOpenByTenantAndCustomer(
                tenantId, payment.getCustomerId());

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

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String key = tenantId.getValue() + ":" + paymentId.getValue() + ":" + idempotencyKey;
            idempotencyCache.put(key, result);
        }

        return Optional.of(result);
    }

    @Override
    public Optional<AllocationResult> manualAllocate(
            TenantId tenantId,
            PaymentId paymentId,
            List<ManualAllocationRequest> requests,
            UUID allocatedBy,
            String idempotencyKey) {

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String key = tenantId.getValue() + ":" + paymentId.getValue() + ":" + idempotencyKey;
            AllocationResult cached = idempotencyCache.get(key);
            if (cached != null) {
                return Optional.of(cached);
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
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                String key = tenantId.getValue() + ":" + paymentId.getValue() + ":" + idempotencyKey;
                idempotencyCache.put(key, result);
            }
            return Optional.of(result);
        }

        AllocationResult result = toResult(payment, engineResult);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String key = tenantId.getValue() + ":" + paymentId.getValue() + ":" + idempotencyKey;
            idempotencyCache.put(key, result);
        }

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
}
