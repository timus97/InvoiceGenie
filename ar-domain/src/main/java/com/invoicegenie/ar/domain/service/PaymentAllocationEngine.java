package com.invoicegenie.ar.domain.service;

import com.invoicegenie.ar.domain.event.PaymentAllocated;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.payment.Payment;
import com.invoicegenie.ar.domain.model.payment.PaymentAllocation;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Domain service for payment allocation engine.
 *
 * <p>Supports:
 * <ul>
 *   <li>FIFO auto-allocation: allocates payment to oldest open invoices first</li>
 *   <li>Manual allocation: allocates specific amounts to specific invoices</li>
 *   <li>Idempotency: caller should check for existing allocation by idempotency key</li>
 *   <li>Concurrency safety: caller should use optimistic locking via Payment version</li>
 * </ul>
 *
 * <p>Business rules:
 * <ul>
 *   <li>Payment must be in RECEIVED status</li>
 *   <li>Invoice must be open (ISSUED, PARTIALLY_PAID, OVERDUE)</li>
 *   <li>Currency must match between payment and invoice</li>
 *   <li>Allocation amount cannot exceed invoice outstanding or payment unallocated</li>
 *   <li>FIFO: invoices sorted by dueDate ASC, then issueDate ASC</li>
 * </ul>
 *
 * <p>Returns {@link PaymentAllocated} domain events for each successful allocation;
 * the application layer is responsible for publishing them.
 */
public final class PaymentAllocationEngine {

    /**
     * Result of an allocation operation, including domain events to publish.
     */
    public record AllocationResult(
            List<PaymentAllocation> allocations,
            Money totalAllocated,
            Money remainingUnallocated,
            List<AllocationError> errors,
            List<PaymentAllocated> events
    ) {
        public boolean isFullyAllocated() {
            return remainingUnallocated.getAmount().signum() == 0;
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    /**
     * Represents an allocation error for a specific invoice.
     */
    public record AllocationError(InvoiceId invoiceId, String reason) {}

    /**
     * Request for manual allocation to specific invoices.
     */
    public record ManualAllocationRequest(
            InvoiceId invoiceId,
            Money amount,
            String notes
    ) {}

    /**
     * Auto-allocates payment to invoices using FIFO (oldest first).
     *
     * @param tenantId the tenant
     * @param payment the payment to allocate from
     * @param invoices list of open invoices for the customer
     * @param allocatedBy user performing allocation
     * @return allocation result with created allocations and events
     */
    public AllocationResult autoAllocateFIFO(
            TenantId tenantId,
            Payment payment,
            List<Invoice> invoices,
            UUID allocatedBy) {

        if (payment.getStatus() != com.invoicegenie.ar.domain.model.payment.PaymentStatus.RECEIVED) {
            return emptyErrorResult(payment, "Payment is not in RECEIVED status");
        }

        // Filter open invoices and matching currency; sort FIFO (oldest due date first, then oldest issue date)
        List<Invoice> openInvoices = invoices.stream()
                .filter(Invoice::isOpen)
                .filter(inv -> inv.getCurrencyCode().equals(payment.getAmount().getCurrencyCode()))
                .sorted(Comparator
                        .comparing((Invoice inv) -> inv.getDueDate() != null ? inv.getDueDate() : inv.getIssueDate())
                        .thenComparing(Invoice::getIssueDate))
                .toList();

        List<PaymentAllocation> allocations = new ArrayList<>();
        List<AllocationError> errors = new ArrayList<>();
        List<PaymentAllocated> events = new ArrayList<>();
        Money totalAllocated = Money.of("0.00", payment.getAmount().getCurrencyCode());
        Money remaining = payment.getAmountUnallocated();

        for (Invoice invoice : openInvoices) {
            if (remaining.getAmount().signum() <= 0) {
                break; // No more to allocate
            }

            // Outstanding uses Invoice.amountPaid (balance due = total - amountPaid)
            Money outstanding = invoice.getBalanceDue();
            Money toAllocate = remaining.getAmount().compareTo(outstanding.getAmount()) <= 0
                    ? remaining
                    : outstanding;

            if (toAllocate.getAmount().signum() > 0) {
                try {
                    PaymentAllocation allocation = payment.allocate(invoice.getId(), toAllocate, allocatedBy, "FIFO auto-allocation");
                    allocations.add(allocation);
                    totalAllocated = totalAllocated.add(toAllocate);
                    remaining = remaining.subtract(toAllocate);
                    events.add(new PaymentAllocated(tenantId, payment.getId(), invoice.getId(), toAllocate));
                } catch (Exception e) {
                    errors.add(new AllocationError(invoice.getId(), e.getMessage()));
                }
            }
        }

        return new AllocationResult(allocations, totalAllocated, remaining, errors, events);
    }

    /**
     * Manually allocates payment to specific invoices.
     *
     * <p>Validates that each target invoice is open and currency-matches the payment
     * (rules previously in {@code AllocationDomainService}).
     *
     * @param tenantId the tenant
     * @param payment the payment to allocate from
     * @param invoices invoices corresponding to the allocation requests (for validation)
     * @param requests list of manual allocation requests
     * @param allocatedBy user performing allocation
     * @return allocation result with created allocations and events
     */
    public AllocationResult manualAllocate(
            TenantId tenantId,
            Payment payment,
            List<Invoice> invoices,
            List<ManualAllocationRequest> requests,
            UUID allocatedBy) {

        if (payment.getStatus() != com.invoicegenie.ar.domain.model.payment.PaymentStatus.RECEIVED) {
            return emptyErrorResult(payment, "Payment is not in RECEIVED status");
        }

        Map<InvoiceId, Invoice> invoiceById = invoices.stream()
                .collect(Collectors.toMap(Invoice::getId, Function.identity(), (a, b) -> a));

        List<PaymentAllocation> allocations = new ArrayList<>();
        List<AllocationError> errors = new ArrayList<>();
        List<PaymentAllocated> events = new ArrayList<>();
        Money totalAllocated = Money.of("0.00", payment.getAmount().getCurrencyCode());

        for (ManualAllocationRequest request : requests) {
            Invoice invoice = invoiceById.get(request.invoiceId());
            if (invoice == null) {
                errors.add(new AllocationError(request.invoiceId(), "Invoice not found for allocation"));
                continue;
            }
            if (!invoice.isOpen()) {
                errors.add(new AllocationError(request.invoiceId(),
                        "Invoice is not open for allocation: " + invoice.getStatus()));
                continue;
            }
            if (!payment.getAmount().getCurrencyCode().equals(invoice.getCurrencyCode())) {
                errors.add(new AllocationError(request.invoiceId(),
                        "Currency mismatch: payment=" + payment.getAmount().getCurrencyCode()
                                + ", invoice=" + invoice.getCurrencyCode()));
                continue;
            }

            try {
                PaymentAllocation allocation = payment.allocate(
                        request.invoiceId(), request.amount(), allocatedBy, request.notes());
                allocations.add(allocation);
                totalAllocated = totalAllocated.add(request.amount());
                events.add(new PaymentAllocated(tenantId, payment.getId(), request.invoiceId(), request.amount()));
            } catch (Exception e) {
                errors.add(new AllocationError(request.invoiceId(), e.getMessage()));
            }
        }

        return new AllocationResult(allocations, totalAllocated, payment.getAmountUnallocated(), errors, events);
    }

    /**
     * Calculates the outstanding balance for an invoice based on allocations.
     * This is a helper for determining how much remains to be paid.
     */
    public Money calculateOutstanding(Money invoiceTotal, List<PaymentAllocation> allocations) {
        Money allocated = Money.of("0.00", invoiceTotal.getCurrencyCode());
        for (PaymentAllocation alloc : allocations) {
            allocated = allocated.add(alloc.getAmount());
        }
        return invoiceTotal.subtract(allocated);
    }

    private static AllocationResult emptyErrorResult(Payment payment, String reason) {
        return new AllocationResult(
                List.of(),
                Money.of("0.00", payment.getAmount().getCurrencyCode()),
                payment.getAmountUnallocated(),
                List.of(new AllocationError(null, reason)),
                List.of());
    }
}
