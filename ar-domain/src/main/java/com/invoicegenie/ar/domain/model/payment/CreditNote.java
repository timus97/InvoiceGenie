package com.invoicegenie.ar.domain.model.payment;

import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.shared.domain.Money;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Credit note for early payment discounts and other adjustments.
 * 
 * <p>Credit notes are generated when:
 * <ul>
 *   <li>Customer pays within 30 days (2% early payment discount)</li>
 *   <li>Other adjustments as needed</li>
 * </ul>
 * 
 * <p>Credit notes can be applied to:
 * <ul>
 *   <li>Short payments (e.g., invoice $1000, pay $980, apply $20 credit)</li>
 *   <li>Future invoices</li>
 * </ul>
 */
public final class CreditNote {

    public enum CreditNoteType {
        EARLY_PAYMENT_DISCOUNT("Early payment discount (2%)"),
        ADJUSTMENT("Manual adjustment"),
        REFUND("Refund");

        private final String description;

        CreditNoteType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum CreditNoteStatus {
        ISSUED,
        APPLIED,
        EXPIRED,
        VOIDED
    }

    private final UUID id;
    private final String creditNoteNumber;
    private final CustomerId customerId;
    private final Money amount;
    private final CreditNoteType type;
    private final UUID referenceInvoiceId; // Invoice that generated this credit note
    private final String description;
    private CreditNoteStatus status;
    private final LocalDate issueDate;
    private LocalDate appliedDate;
    private LocalDate expiryDate;
    private UUID appliedToPaymentId; // Payment where credit was applied
    private String notes;
    private final Instant createdAt;
    private Instant updatedAt;

    public CreditNote(UUID id, String creditNoteNumber, CustomerId customerId, Money amount,
                      CreditNoteType type, UUID referenceInvoiceId, String description) {
        this.id = Objects.requireNonNull(id);
        this.creditNoteNumber = Objects.requireNonNull(creditNoteNumber);
        if (creditNoteNumber.isBlank()) {
            throw new IllegalArgumentException("Credit note number is required");
        }
        this.customerId = Objects.requireNonNull(customerId);
        this.amount = Objects.requireNonNull(amount);
        if (amount.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("Credit note amount must be positive");
        }
        this.type = Objects.requireNonNull(type);
        this.referenceInvoiceId = referenceInvoiceId;
        this.description = description;
        this.status = CreditNoteStatus.ISSUED;
        this.issueDate = LocalDate.now();
        this.expiryDate = LocalDate.now().plusYears(1); // Default 1 year expiry
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /** For reconstitution from persistence. */
    public CreditNote(UUID id, String creditNoteNumber, CustomerId customerId, Money amount,
                      CreditNoteType type, UUID referenceInvoiceId, String description,
                      CreditNoteStatus status, LocalDate issueDate, LocalDate appliedDate,
                      LocalDate expiryDate, UUID appliedToPaymentId, String notes,
                      Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.creditNoteNumber = Objects.requireNonNull(creditNoteNumber);
        this.customerId = Objects.requireNonNull(customerId);
        this.amount = Objects.requireNonNull(amount);
        this.type = Objects.requireNonNull(type);
        this.referenceInvoiceId = referenceInvoiceId;
        this.description = description;
        this.status = status != null ? status : CreditNoteStatus.ISSUED;
        this.issueDate = Objects.requireNonNull(issueDate);
        this.appliedDate = appliedDate;
        this.expiryDate = expiryDate;
        this.appliedToPaymentId = appliedToPaymentId;
        this.notes = notes;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    // ==================== Accessors ====================

    public UUID getId() { return id; }
    public String getCreditNoteNumber() { return creditNoteNumber; }
    public CustomerId getCustomerId() { return customerId; }
    public Money getAmount() { return amount; }
    public CreditNoteType getType() { return type; }
    public UUID getReferenceInvoiceId() { return referenceInvoiceId; }
    public String getDescription() { return description; }
    public CreditNoteStatus getStatus() { return status; }
    public LocalDate getIssueDate() { return issueDate; }
    public LocalDate getAppliedDate() { return appliedDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public UUID getAppliedToPaymentId() { return appliedToPaymentId; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    // ==================== Business Logic ====================

    /**
     * Check if credit note can be applied.
     */
    public boolean canApply() {
        if (status != CreditNoteStatus.ISSUED) {
            return false;
        }
        if (expiryDate != null && LocalDate.now().isAfter(expiryDate)) {
            return false;
        }
        return true;
    }

    /**
     * Apply credit note to a payment.
     */
    public void apply(UUID paymentId) {
        if (!canApply()) {
            throw new IllegalStateException("Credit note cannot be applied. Status: " + status);
        }
        this.status = CreditNoteStatus.APPLIED;
        this.appliedDate = LocalDate.now();
        this.appliedToPaymentId = paymentId;
        touch();
    }

    /**
     * Void the credit note.
     */
    public void voidNote(String reason) {
        if (status == CreditNoteStatus.APPLIED) {
            throw new IllegalStateException("Cannot void applied credit note");
        }
        this.status = CreditNoteStatus.VOIDED;
        this.notes = (this.notes != null ? this.notes + "\n" : "") + "[VOIDED] " + reason;
        touch();
    }

    /**
     * Check if credit note is expired.
     */
    public boolean isExpired() {
        return expiryDate != null && LocalDate.now().isAfter(expiryDate);
    }

    /**
     * Get remaining balance (amount not yet applied).
     */
    public Money getRemainingBalance() {
        // Simplified - in full implementation would track partial applications
        return status == CreditNoteStatus.APPLIED ? Money.of("0.00", amount.getCurrencyCode()) : amount;
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    // ==================== Factory Methods ====================

    /**
     * Create a credit note for early payment discount.
     */
    public static CreditNote forEarlyPaymentDiscount(UUID id, CustomerId customerId, Money amount,
                                                      UUID referenceInvoiceId) {
        String noteNumber = "CN-EPD-" + System.currentTimeMillis();
        return new CreditNote(
                id,
                noteNumber,
                customerId,
                amount,
                CreditNoteType.EARLY_PAYMENT_DISCOUNT,
                referenceInvoiceId,
                "2% early payment discount for invoice " + referenceInvoiceId
        );
    }

    // ==================== Equals/HashCode ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CreditNote that = (CreditNote) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
