package com.invoicegenie.ar.domain.model.payment;

import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.shared.domain.Money;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Payment aggregate root. Represents money received from a customer.
 *
 * <p>Business rules in this aggregate:
 * <ul>
 *   <li>Payment has a currency and amount; amount is immutable</li>
 *   <li>Allocations distribute amount across invoices (many-to-many with Invoice)</li>
 *   <li>Sum of allocations cannot exceed payment amount</li>
 *   <li>amountUnallocated = amount - sum(allocations)</li>
 *   <li>Reversal/refund only on RECEIVED status; creates reversal logic at application layer</li>
 *   <li>Allocations may be unallocated (STORY-013) while payment stays RECEIVED; cash remains unapplied</li>
 *   <li>{@code originalVersion} is the version at load time for optimistic concurrency checks</li>
 * </ul>
 */
public final class Payment {

    private final PaymentId id;
    private final String paymentNumber; // human-readable, tenant-scoped
    private final CustomerId customerId;
    private final Money amount;
    private final LocalDate paymentDate;
    private final Instant receivedAt;
    private final PaymentMethod method;
    private final String reference;
    private final UUID bankAccountId; // optional
    private String notes;
    private PaymentStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;
    /** Version loaded from persistence (0 for brand-new aggregates). Used for optimistic lock. */
    private final long originalVersion;

    private final List<PaymentAllocation> allocations = new ArrayList<>();

    public Payment(PaymentId id, String paymentNumber, CustomerId customerId, Money amount,
                   LocalDate paymentDate, PaymentMethod method) {
        this(id, paymentNumber, customerId, amount, paymentDate, Instant.now(), method, null, null,
                null, PaymentStatus.RECEIVED, Instant.now(), Instant.now(), 1L, List.of());
    }

    /** For reconstitution from persistence. */
    public Payment(PaymentId id, String paymentNumber, CustomerId customerId, Money amount,
                   LocalDate paymentDate, Instant receivedAt, PaymentMethod method,
                   String reference, UUID bankAccountId, String notes,
                   PaymentStatus status, Instant createdAt, Instant updatedAt, long version,
                   List<PaymentAllocation> allocations) {
        this.id = Objects.requireNonNull(id);
        this.paymentNumber = Objects.requireNonNull(paymentNumber);
        this.customerId = Objects.requireNonNull(customerId);
        this.amount = Objects.requireNonNull(amount);
        this.paymentDate = Objects.requireNonNull(paymentDate);
        this.receivedAt = Objects.requireNonNull(receivedAt);
        this.method = Objects.requireNonNull(method);
        this.reference = reference;
        this.bankAccountId = bankAccountId;
        this.notes = notes;
        this.status = status == null ? PaymentStatus.RECEIVED : status;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.version = version;
        this.originalVersion = version;
        if (allocations != null) {
            for (PaymentAllocation a : allocations) {
                requireSameCurrency(a.getAmount());
                this.allocations.add(a);
            }
        }
    }

    // ==================== Accessors ====================

    public PaymentId getId() { return id; }
    public String getPaymentNumber() { return paymentNumber; }
    public CustomerId getCustomerId() { return customerId; }
    public Money getAmount() { return amount; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public Instant getReceivedAt() { return receivedAt; }
    public PaymentMethod getMethod() { return method; }
    public String getReference() { return reference; }
    public UUID getBankAccountId() { return bankAccountId; }
    public String getNotes() { return notes; }
    public PaymentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    /**
     * Version as loaded from persistence (before in-memory mutations).
     * Repository conditional updates use this as the expected DB version.
     */
    public long getOriginalVersion() { return originalVersion; }

    public List<PaymentAllocation> getAllocations() {
        return Collections.unmodifiableList(allocations);
    }

    /**
     * Returns unallocated amount (amount - sum of allocations).
     */
    public Money getAmountUnallocated() {
        Money allocated = Money.of("0.00", amount.getCurrencyCode());
        for (PaymentAllocation a : allocations) {
            allocated = allocated.add(a.getAmount());
        }
        return amount.subtract(allocated);
    }

    /**
     * Checks if fully allocated.
     */
    public boolean isFullyAllocated() {
        return getAmountUnallocated().getAmount().signum() == 0;
    }

    // ==================== Business Logic ====================

    /**
     * Allocates part of this payment to an invoice.
     * @return the created allocation
     */
    public PaymentAllocation allocate(InvoiceId invoiceId, Money amountToAllocate, UUID allocatedBy, String notes) {
        assertReceived();
        requireSameCurrency(amountToAllocate);

        if (amountToAllocate.getAmount().compareTo(getAmountUnallocated().getAmount()) > 0) {
            throw new IllegalStateException("Allocation exceeds unallocated amount");
        }

        PaymentAllocation alloc = new PaymentAllocation(UUID.randomUUID(), invoiceId, amountToAllocate, allocatedBy, notes);
        allocations.add(alloc);
        touch();
        return alloc;
    }

    /**
     * Adds an existing allocation (for reconstitution or from domain service).
     */
    public void addAllocation(PaymentAllocation allocation) {
        requireSameCurrency(allocation.getAmount());
        allocations.add(allocation);
    }

    /**
     * Removes the allocation to the given invoice (STORY-013 unallocate).
     * Payment remains RECEIVED; cash returns to unallocated.
     *
     * @return the removed allocation
     * @throws IllegalStateException if no allocation exists for the invoice
     */
    public PaymentAllocation unallocate(InvoiceId invoiceId) {
        assertReceived();
        Objects.requireNonNull(invoiceId, "invoiceId");
        for (int i = 0; i < allocations.size(); i++) {
            PaymentAllocation a = allocations.get(i);
            if (a.getInvoiceId().equals(invoiceId)) {
                allocations.remove(i);
                touch();
                return a;
            }
        }
        throw new IllegalStateException("No allocation for invoice: " + invoiceId.getValue());
    }

    /**
     * Finds allocation for an invoice if present.
     */
    public Optional<PaymentAllocation> findAllocation(InvoiceId invoiceId) {
        Objects.requireNonNull(invoiceId, "invoiceId");
        return allocations.stream().filter(a -> a.getInvoiceId().equals(invoiceId)).findFirst();
    }

    /**
     * Updates notes on the payment.
     */
    public void setNotes(String notes) {
        assertReceived();
        this.notes = notes;
        touch();
    }

    /**
     * Marks payment as reversed. Application layer should create offsetting ledger entries.
     */
    public void reverse() {
        if (status != PaymentStatus.RECEIVED)
            throw new IllegalStateException("Only RECEIVED payments can be reversed");
        this.status = PaymentStatus.REVERSED;
        touch();
    }

    /**
     * Marks payment as refunded. Application layer should create offsetting ledger entries.
     */
    public void refund() {
        if (status != PaymentStatus.RECEIVED)
            throw new IllegalStateException("Only RECEIVED payments can be refunded");
        this.status = PaymentStatus.REFUNDED;
        touch();
    }

    // ==================== Helpers ====================

    private void assertReceived() {
        if (status != PaymentStatus.RECEIVED)
            throw new IllegalStateException("Payment is not in RECEIVED state: " + status);
    }

    private void requireSameCurrency(Money m) {
        if (!amount.getCurrencyCode().equals(m.getCurrencyCode())) {
            throw new IllegalArgumentException("Currency mismatch: " + amount.getCurrencyCode() + " vs " + m.getCurrencyCode());
        }
    }

    private void touch() {
        this.updatedAt = Instant.now();
        this.version++;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Payment payment = (Payment) o;
        return id.equals(payment.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
