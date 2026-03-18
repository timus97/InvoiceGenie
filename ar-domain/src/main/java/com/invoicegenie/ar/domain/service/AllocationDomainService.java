package com.invoicegenie.ar.domain.service;

import com.invoicegenie.ar.domain.event.PaymentAllocated;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.payment.Payment;
import com.invoicegenie.ar.domain.model.payment.PaymentAllocation;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import java.util.UUID;

/**
 * Domain service for cross-aggregate allocation logic.
 *
 * <p><b>Why a domain service:</b> Allocation involves two aggregates (Payment and Invoice).
 * Payment knows about its own unallocated amount; Invoice knows about its open status.
 * Neither should know about the other. This service orchestrates the rules.
 *
 * <p>Business rules:
 * <ul>
 *   <li>Invoice must be open (ISSUED, PARTIALLY_PAID, OVERDUE)</li>
 *   <li>Payment must have unallocated amount >= allocation amount</li>
 *   <li>Currency must match between payment and invoice</li>
 *   <li>Allocation creates PaymentAllocation child entity on Payment</li>
 *   <li>Emits PaymentAllocated event for GL consumption</li>
 * </ul>
 */
public final class AllocationDomainService {

    /**
     * Allocates payment amount to invoice. Mutates both aggregates in-memory.
     * Persistence and event publishing handled by application layer.
     *
     * @return the created allocation and event
     */
    public AllocationResult allocate(TenantId tenantId, Payment payment, Invoice invoice,
                                     Money amount, UUID allocatedBy, String notes) {

        // Validate invoice is open
        if (!invoice.isOpen()) {
            throw new IllegalStateException("Invoice is not open for allocation: " + invoice.getStatus());
        }

        // Validate currency match
        if (!payment.getAmount().getCurrencyCode().equals(invoice.getCurrencyCode())) {
            throw new IllegalArgumentException("Currency mismatch: payment=" + payment.getAmount().getCurrencyCode() +
                    ", invoice=" + invoice.getCurrencyCode());
        }

        // Perform allocation on Payment (enforces unallocated check)
        PaymentAllocation allocation = payment.allocate(invoice.getId(), amount, allocatedBy, notes);

        // Update invoice status (application layer will persist and publish events)
        // Note: Full paid check requires external knowledge of total allocations; app layer handles

        // Create event
        PaymentAllocated event = new PaymentAllocated(tenantId, payment.getId(), invoice.getId(), amount);

        return new AllocationResult(allocation, event);
    }

    public record AllocationResult(PaymentAllocation allocation, PaymentAllocated event) {}
}
