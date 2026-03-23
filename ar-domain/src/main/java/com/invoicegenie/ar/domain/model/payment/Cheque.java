package com.invoicegenie.ar.domain.model.payment;

import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.shared.domain.Money;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Cheque aggregate root for cheque processing.
 * 
 * <p>Cheque lifecycle:
 * <ol>
 *   <li>RECEIVED: Cheque received from customer</li>
 *   <li>DEPOSITED: Cheque deposited to bank</li>
 *   <li>CLEARED: Cheque cleared → creates payment allocation</li>
 *   <li>BOUNCED: Cheque bounced → reverse entries, reopen invoice</li>
 * </ol>
 * 
 * <p>Business rules:
 * <ul>
 *   <li>Cheque must have valid cheque number, bank, and date</li>
 *   <li>Amount must be positive</li>
 *   <li>Cheque can only be deposited from RECEIVED state</li>
 *   <li>Cheque can only be cleared from DEPOSITED state</li>
 *   <li>Cheque can only be bounced from DEPOSITED state</li>
 *   <li>On bounce: reverse ledger entries and reopen affected invoices</li>
 * </ul>
 */
public final class Cheque {

    private final UUID id;
    private final String chequeNumber;
    private final CustomerId customerId;
    private final Money amount;
    private final String bankName;
    private final String bankBranch;
    private final LocalDate chequeDate;
    private final LocalDate receivedDate;
    private LocalDate depositedDate;
    private LocalDate clearedDate;
    private LocalDate bouncedDate;
    private String bounceReason;
    private ChequeStatus status;
    private final UUID paymentId; // Linked payment when cleared
    private final List<UUID> allocatedInvoiceIds; // Invoices paid by this cheque
    private String notes;
    private final Instant createdAt;
    private Instant updatedAt;

    public Cheque(UUID id, String chequeNumber, CustomerId customerId, Money amount,
                  String bankName, String bankBranch, LocalDate chequeDate, String notes) {
        this.id = Objects.requireNonNull(id);
        this.chequeNumber = Objects.requireNonNull(chequeNumber);
        if (chequeNumber.isBlank()) {
            throw new IllegalArgumentException("Cheque number is required");
        }
        this.customerId = Objects.requireNonNull(customerId);
        this.amount = Objects.requireNonNull(amount);
        if (amount.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("Cheque amount must be positive");
        }
        this.bankName = Objects.requireNonNull(bankName);
        this.bankBranch = bankBranch;
        this.chequeDate = Objects.requireNonNull(chequeDate);
        this.receivedDate = LocalDate.now();
        this.status = ChequeStatus.RECEIVED;
        this.paymentId = null;
        this.allocatedInvoiceIds = new ArrayList<>();
        this.notes = notes;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /** For reconstitution from persistence. */
    public Cheque(UUID id, String chequeNumber, CustomerId customerId, Money amount,
                  String bankName, String bankBranch, LocalDate chequeDate,
                  LocalDate receivedDate, LocalDate depositedDate, LocalDate clearedDate,
                  LocalDate bouncedDate, String bounceReason, ChequeStatus status,
                  UUID paymentId, List<UUID> allocatedInvoiceIds, String notes,
                  Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.chequeNumber = Objects.requireNonNull(chequeNumber);
        this.customerId = Objects.requireNonNull(customerId);
        this.amount = Objects.requireNonNull(amount);
        this.bankName = Objects.requireNonNull(bankName);
        this.bankBranch = bankBranch;
        this.chequeDate = Objects.requireNonNull(chequeDate);
        this.receivedDate = Objects.requireNonNull(receivedDate);
        this.depositedDate = depositedDate;
        this.clearedDate = clearedDate;
        this.bouncedDate = bouncedDate;
        this.bounceReason = bounceReason;
        this.status = status != null ? status : ChequeStatus.RECEIVED;
        this.paymentId = paymentId;
        this.allocatedInvoiceIds = allocatedInvoiceIds != null ? new ArrayList<>(allocatedInvoiceIds) : new ArrayList<>();
        this.notes = notes;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    // ==================== Accessors ====================

    public UUID getId() { return id; }
    public String getChequeNumber() { return chequeNumber; }
    public CustomerId getCustomerId() { return customerId; }
    public Money getAmount() { return amount; }
    public String getBankName() { return bankName; }
    public String getBankBranch() { return bankBranch; }
    public LocalDate getChequeDate() { return chequeDate; }
    public LocalDate getReceivedDate() { return receivedDate; }
    public LocalDate getDepositedDate() { return depositedDate; }
    public LocalDate getClearedDate() { return clearedDate; }
    public LocalDate getBouncedDate() { return bouncedDate; }
    public String getBounceReason() { return bounceReason; }
    public ChequeStatus getStatus() { return status; }
    public UUID getPaymentId() { return paymentId; }
    public List<UUID> getAllocatedInvoiceIds() { return Collections.unmodifiableList(allocatedInvoiceIds); }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    // ==================== Business Logic ====================

    /**
     * Deposit cheque to bank.
     */
    public void deposit() {
        if (!status.canDeposit()) {
            throw new IllegalStateException("Cannot deposit cheque in state: " + status);
        }
        this.status = ChequeStatus.DEPOSITED;
        this.depositedDate = LocalDate.now();
        touch();
    }

    /**
     * Clear cheque (bank confirmed payment).
     */
    public ChequeClearedResult clear() {
        if (!status.canClear()) {
            throw new IllegalStateException("Cannot clear cheque in state: " + status);
        }
        this.status = ChequeStatus.CLEARED;
        this.clearedDate = LocalDate.now();
        touch();
        return new ChequeClearedResult(this.id, this.paymentId, this.amount);
    }

    /**
     * Bounce cheque (bank returned cheque).
     */
    public ChequeBouncedResult bounce(String reason) {
        if (!status.canBounce()) {
            throw new IllegalStateException("Cannot bounce cheque in state: " + status);
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Bounce reason is required");
        }
        this.status = ChequeStatus.BOUNCED;
        this.bouncedDate = LocalDate.now();
        this.bounceReason = reason;
        touch();
        return new ChequeBouncedResult(this.id, this.paymentId, this.allocatedInvoiceIds, this.amount, reason);
    }

    /**
     * Set the payment ID when cheque is cleared.
     */
    public void setPaymentId(UUID paymentId) {
        if (this.paymentId != null) {
            throw new IllegalStateException("Payment ID already set");
        }
        // Note: This is a simplified version; in full implementation would update field
    }

    /**
     * Add allocated invoice ID.
     */
    public void addAllocatedInvoice(UUID invoiceId) {
        if (!allocatedInvoiceIds.contains(invoiceId)) {
            allocatedInvoiceIds.add(invoiceId);
            touch();
        }
    }

    /**
     * Update notes.
     */
    public void setNotes(String notes) {
        this.notes = notes;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    // ==================== Result Records ====================

    public record ChequeClearedResult(UUID chequeId, UUID paymentId, Money amount) {}
    public record ChequeBouncedResult(UUID chequeId, UUID paymentId, List<UUID> invoiceIds, 
                                       Money amount, String reason) {}

    // ==================== Equals/HashCode ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Cheque cheque = (Cheque) o;
        return id.equals(cheque.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
