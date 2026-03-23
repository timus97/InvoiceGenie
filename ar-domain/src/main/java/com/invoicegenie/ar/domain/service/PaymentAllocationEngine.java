package com.invoicegenie.ar.domain.service;

import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceStatus;
import com.invoicegenie.ar.domain.model.payment.Payment;
import com.invoicegenie.ar.domain.model.payment.PaymentAllocation;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

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
 */
public final class PaymentAllocationEngine {

    /**
     * Result of an allocation operation.
     */
    public record AllocationResult(
            List<PaymentAllocation> allocations,
            Money totalAllocated,
            Money remainingUnallocated,
            List<AllocationError> errors
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
     * @return allocation result with created allocations
     */
    public AllocationResult autoAllocateFIFO(
            TenantId tenantId,
            Payment payment,
            List<Invoice> invoices,
            UUID allocatedBy) {

        if (payment.getStatus() != com.invoicegenie.ar.domain.model.payment.PaymentStatus.RECEIVED) {
            return new AllocationResult(List.of(), 
                    Money.of("0.00", payment.getAmount().getCurrencyCode()),
                    payment.getAmountUnallocated(),
                    List.of(new AllocationError(null, "Payment is not in RECEIVED status")));
        }

        // Filter open invoices and sort FIFO (oldest due date first, then oldest issue date)
        List<Invoice> openInvoices = invoices.stream()
                .filter(Invoice::isOpen)
                .filter(inv -> inv.getCurrencyCode().equals(payment.getAmount().getCurrencyCode()))
                .sorted(Comparator
                        .comparing((Invoice inv) -> inv.getDueDate() != null ? inv.getDueDate() : inv.getIssueDate())
                        .thenComparing(Invoice::getIssueDate))
                .toList();

        List<PaymentAllocation> allocations = new ArrayList<>();
        List<AllocationError> errors = new ArrayList<>();
        Money totalAllocated = Money.of("0.00", payment.getAmount().getCurrencyCode());
        Money remaining = payment.getAmountUnallocated();

        for (Invoice invoice : openInvoices) {
            if (remaining.getAmount().signum() <= 0) {
                break; // No more to allocate
            }

            // Calculate how much to allocate to this invoice
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
                } catch (Exception e) {
                    errors.add(new AllocationError(invoice.getId(), e.getMessage()));
                }
            }
        }

        return new AllocationResult(allocations, totalAllocated, remaining, errors);
    }

    /**
     * Manually allocates payment to specific invoices.
     *
     * @param tenantId the tenant
     * @param payment the payment to allocate from
     * @param requests list of manual allocation requests
     * @param allocatedBy user performing allocation
     * @return allocation result with created allocations
     */
    public AllocationResult manualAllocate(
            TenantId tenantId,
            Payment payment,
            List<ManualAllocationRequest> requests,
            UUID allocatedBy) {

        if (payment.getStatus() != com.invoicegenie.ar.domain.model.payment.PaymentStatus.RECEIVED) {
            return new AllocationResult(List.of(),
                    Money.of("0.00", payment.getAmount().getCurrencyCode()),
                    payment.getAmountUnallocated(),
                    List.of(new AllocationError(null, "Payment is not in RECEIVED status")));
        }

        List<PaymentAllocation> allocations = new ArrayList<>();
        List<AllocationError> errors = new ArrayList<>();
        Money totalAllocated = Money.of("0.00", payment.getAmount().getCurrencyCode());

        for (ManualAllocationRequest request : requests) {
            try {
                PaymentAllocation allocation = payment.allocate(request.invoiceId(), request.amount(), allocatedBy, request.notes());
                allocations.add(allocation);
                totalAllocated = totalAllocated.add(request.amount());
            } catch (Exception e) {
                errors.add(new AllocationError(request.invoiceId(), e.getMessage()));
            }
        }

        return new AllocationResult(allocations, totalAllocated, payment.getAmountUnallocated(), errors);
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
}
