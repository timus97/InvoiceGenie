package com.invoicegenie.ar.application.port.inbound;

import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.payment.PaymentAllocation;
import com.invoicegenie.ar.domain.model.payment.PaymentId;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Inbound port: payment allocation use cases.
 * 
 * <p>Supports:
 * <ul>
 *   <li>FIFO auto-allocation: allocates to oldest open invoices first</li>
 *   <li>Manual allocation: allocates specific amounts to specific invoices</li>
 * </ul>
 * 
 * <p>Features:
 * <ul>
 *   <li>Idempotency: same idempotency key returns same result</li>
 *   <li>Concurrency safe: uses optimistic locking (version)</li>
 *   <li>One payment can be allocated to multiple invoices</li>
 * </ul>
 */
public interface PaymentAllocationUseCase {

    /**
     * Result of an allocation operation.
     */
    record AllocationResult(
            PaymentId paymentId,
            List<AllocationDetail> allocations,
            Money totalAllocated,
            Money remainingUnallocated,
            List<String> errors,
            long paymentVersion
    ) {
        public record AllocationDetail(InvoiceId invoiceId, Money amount, UUID allocationId) {}
        public boolean hasErrors() { return !errors.isEmpty(); }
        public boolean isFullyAllocated() { return remainingUnallocated.getAmount().signum() == 0; }
    }

    /**
     * Request for manual allocation.
     */
    record ManualAllocationRequest(
            InvoiceId invoiceId,
            Money amount,
            String notes
    ) {}

    /**
     * Auto-allocates payment to invoices using FIFO (oldest first).
     *
     * @param tenantId the tenant
     * @param paymentId the payment to allocate from
     * @param allocatedBy user performing allocation
     * @param idempotencyKey optional idempotency key for duplicate prevention
     * @return allocation result
     */
    Optional<AllocationResult> autoAllocateFIFO(
            TenantId tenantId,
            PaymentId paymentId,
            UUID allocatedBy,
            String idempotencyKey);

    /**
     * Manually allocates payment to specific invoices.
     *
     * @param tenantId the tenant
     * @param paymentId the payment to allocate from
     * @param requests list of manual allocation requests
     * @param allocatedBy user performing allocation
     * @param idempotencyKey optional idempotency key for duplicate prevention
     * @return allocation result
     */
    Optional<AllocationResult> manualAllocate(
            TenantId tenantId,
            PaymentId paymentId,
            List<ManualAllocationRequest> requests,
            UUID allocatedBy,
            String idempotencyKey);

    /**
     * Gets all allocations for a payment.
     */
    Optional<AllocationResult> getAllocations(TenantId tenantId, PaymentId paymentId);

    /**
     * Gets all allocations for an invoice.
     */
    List<AllocationResult.AllocationDetail> getInvoiceAllocations(TenantId tenantId, InvoiceId invoiceId);
}
