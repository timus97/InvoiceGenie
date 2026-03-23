package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.PaymentAllocationUseCase;
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
import java.util.List;
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
 * </ul>
 */
public class PaymentAllocationService implements PaymentAllocationUseCase {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentAllocationEngine allocationEngine;
    
    // Simple in-memory idempotency store (in production, use database table)
    private final ConcurrentMap<String, AllocationResult> idempotencyCache = new ConcurrentHashMap<>();

    public PaymentAllocationService(
            PaymentRepository paymentRepository,
            InvoiceRepository invoiceRepository) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.allocationEngine = new PaymentAllocationEngine();
    }

    @Override
    public Optional<AllocationResult> autoAllocateFIFO(
            TenantId tenantId,
            PaymentId paymentId,
            UUID allocatedBy,
            String idempotencyKey) {
        
        // Check idempotency
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String key = tenantId.getValue() + ":" + paymentId.getValue() + ":" + idempotencyKey;
            AllocationResult cached = idempotencyCache.get(key);
            if (cached != null) {
                return Optional.of(cached);
            }
        }

        // Load payment with optimistic locking
        Optional<Payment> paymentOpt = paymentRepository.findByTenantAndId(tenantId, paymentId);
        if (paymentOpt.isEmpty()) {
            return Optional.empty();
        }
        Payment payment = paymentOpt.get();

        // Load open invoices for this customer
        List<Invoice> customerInvoices = invoiceRepository.findOpenByTenantAndCustomer(tenantId, payment.getCustomerId());

        // Perform allocation
        PaymentAllocationEngine.AllocationResult engineResult = allocationEngine.autoAllocateFIFO(
                tenantId, payment, customerInvoices, allocatedBy);

        // Save payment (with version check via optimistic locking in repository)
        if (!engineResult.allocations().isEmpty()) {
            paymentRepository.save(tenantId, payment);
            
            // Update invoice balances (mark as partially/fully paid)
            for (PaymentAllocation alloc : engineResult.allocations()) {
                invoiceRepository.findByTenantAndId(tenantId, alloc.getInvoiceId())
                        .ifPresent(invoice -> {
                            // Update invoice payment status
                            boolean fullyPaid = invoice.getBalanceDue().getAmount().signum() <= 0;
                            invoice.applyPaymentStatus(fullyPaid);
                            invoiceRepository.save(tenantId, invoice);
                        });
            }
        }

        // Build result
        AllocationResult result = toResult(payment, engineResult);

        // Cache for idempotency
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

        // Check idempotency
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String key = tenantId.getValue() + ":" + paymentId.getValue() + ":" + idempotencyKey;
            AllocationResult cached = idempotencyCache.get(key);
            if (cached != null) {
                return Optional.of(cached);
            }
        }

        // Load payment with optimistic locking
        Optional<Payment> paymentOpt = paymentRepository.findByTenantAndId(tenantId, paymentId);
        if (paymentOpt.isEmpty()) {
            return Optional.empty();
        }
        Payment payment = paymentOpt.get();

        // Convert requests to engine requests
        List<PaymentAllocationEngine.ManualAllocationRequest> engineRequests = requests.stream()
                .map(r -> new PaymentAllocationEngine.ManualAllocationRequest(r.invoiceId(), r.amount(), r.notes()))
                .toList();

        // Perform allocation
        PaymentAllocationEngine.AllocationResult engineResult = allocationEngine.manualAllocate(
                tenantId, payment, engineRequests, allocatedBy);

        // Save payment
        if (!engineResult.allocations().isEmpty()) {
            paymentRepository.save(tenantId, payment);
            
            // Update invoice balances
            for (PaymentAllocation alloc : engineResult.allocations()) {
                invoiceRepository.findByTenantAndId(tenantId, alloc.getInvoiceId())
                        .ifPresent(invoice -> {
                            boolean fullyPaid = invoice.getBalanceDue().getAmount().signum() <= 0;
                            invoice.applyPaymentStatus(fullyPaid);
                            invoiceRepository.save(tenantId, invoice);
                        });
            }
        }

        // Build result
        AllocationResult result = toResult(payment, engineResult);

        // Cache for idempotency
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
                            .map(a -> new AllocationResult.AllocationDetail(a.getInvoiceId(), a.getAmount(), a.getId()))
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
