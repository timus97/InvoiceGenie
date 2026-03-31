package com.invoicegenie.ar.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA entity for Credit Note (discounts and adjustments).
 * tenant_id on every table — never query without it.
 */
@Entity
@Table(name = "ar_credit_note")
public class CreditNoteEntity {

    @Id
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "credit_note_number", nullable = false)
    private String creditNoteNumber;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private CreditNoteType type;

    @Column(name = "reference_invoice_id")
    private UUID referenceInvoiceId;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CreditNoteStatus status;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "applied_date")
    private LocalDate appliedDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "applied_to_payment_id")
    private UUID appliedToPaymentId;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Enums for JPA
    public enum CreditNoteType {
        EARLY_PAYMENT_DISCOUNT,
        ADJUSTMENT,
        REFUND
    }

    public enum CreditNoteStatus {
        ISSUED,
        APPLIED,
        EXPIRED,
        VOIDED
    }

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getCreditNoteNumber() { return creditNoteNumber; }
    public void setCreditNoteNumber(String creditNoteNumber) { this.creditNoteNumber = creditNoteNumber; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public CreditNoteType getType() { return type; }
    public void setType(CreditNoteType type) { this.type = type; }
    public UUID getReferenceInvoiceId() { return referenceInvoiceId; }
    public void setReferenceInvoiceId(UUID referenceInvoiceId) { this.referenceInvoiceId = referenceInvoiceId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public CreditNoteStatus getStatus() { return status; }
    public void setStatus(CreditNoteStatus status) { this.status = status; }
    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }
    public LocalDate getAppliedDate() { return appliedDate; }
    public void setAppliedDate(LocalDate appliedDate) { this.appliedDate = appliedDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public UUID getAppliedToPaymentId() { return appliedToPaymentId; }
    public void setAppliedToPaymentId(UUID appliedToPaymentId) { this.appliedToPaymentId = appliedToPaymentId; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
