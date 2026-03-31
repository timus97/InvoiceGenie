package com.invoicegenie.ar.domain.model.payment;

import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.shared.domain.Money;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Child entity within Payment aggregate. Represents an allocation of payment amount to an invoice.
 *
 * <p>Business rules:
 * <ul>
 *   <li>Allocation amount must be positive</li>
 *   <li>Currency must match both payment and invoice (enforced at creation)</li>
 *   <li>Sum of allocations across invoices cannot exceed payment amount (enforced by Payment)</li>
 *   <li>Once created, allocation is immutable (reversal via separate domain logic)</li>
 * </ul>
 */
public final class PaymentAllocation {

    private final UUID id; // not a full EntityId; internal to Payment aggregate
    private final InvoiceId invoiceId;
    private final Money amount;
    private final Instant allocatedAt;
    private final UUID allocatedBy; // user id or system; nullable
    private final String notes;

    public PaymentAllocation(UUID id, InvoiceId invoiceId, Money amount, UUID allocatedBy, String notes) {
        this(id, invoiceId, amount, Instant.now(), allocatedBy, notes);
    }

    /** For reconstitution from persistence. */
    public PaymentAllocation(UUID id, InvoiceId invoiceId, Money amount,
                             Instant allocatedAt, UUID allocatedBy, String notes) {
        this.id = Objects.requireNonNull(id);
        this.invoiceId = Objects.requireNonNull(invoiceId);
        this.amount = Objects.requireNonNull(amount);
        if (amount.getAmount().signum() <= 0)
            throw new IllegalArgumentException("allocation amount must be positive");
        this.allocatedAt = Objects.requireNonNull(allocatedAt);
        this.allocatedBy = allocatedBy;
        this.notes = notes;
    }

    public UUID getId() { return id; }
    public InvoiceId getInvoiceId() { return invoiceId; }
    public Money getAmount() { return amount; }
    public Instant getAllocatedAt() { return allocatedAt; }
    public UUID getAllocatedBy() { return allocatedBy; }
    public String getNotes() { return notes; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PaymentAllocation that = (PaymentAllocation) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
