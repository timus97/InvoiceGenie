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

import com.invoicegenie.ar.domain.model.payment.ChequeStatus;

/**
 * JPA entity for Cheque processing.
 * tenant_id on every table — never query without it.
 */
@Entity
@Table(name = "ar_cheque")
public class ChequeEntity {

    @Id
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "cheque_number", nullable = false)
    private String chequeNumber;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "bank_name", nullable = false)
    private String bankName;

    @Column(name = "bank_branch")
    private String bankBranch;

    @Column(name = "cheque_date", nullable = false)
    private LocalDate chequeDate;

    @Column(name = "received_date", nullable = false)
    private LocalDate receivedDate;

    @Column(name = "deposited_date")
    private LocalDate depositedDate;

    @Column(name = "cleared_date")
    private LocalDate clearedDate;

    @Column(name = "bounced_date")
    private LocalDate bouncedDate;

    @Column(name = "bounce_reason")
    private String bounceReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ChequeStatus status;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getChequeNumber() { return chequeNumber; }
    public void setChequeNumber(String chequeNumber) { this.chequeNumber = chequeNumber; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public String getBankBranch() { return bankBranch; }
    public void setBankBranch(String bankBranch) { this.bankBranch = bankBranch; }
    public LocalDate getChequeDate() { return chequeDate; }
    public void setChequeDate(LocalDate chequeDate) { this.chequeDate = chequeDate; }
    public LocalDate getReceivedDate() { return receivedDate; }
    public void setReceivedDate(LocalDate receivedDate) { this.receivedDate = receivedDate; }
    public LocalDate getDepositedDate() { return depositedDate; }
    public void setDepositedDate(LocalDate depositedDate) { this.depositedDate = depositedDate; }
    public LocalDate getClearedDate() { return clearedDate; }
    public void setClearedDate(LocalDate clearedDate) { this.clearedDate = clearedDate; }
    public LocalDate getBouncedDate() { return bouncedDate; }
    public void setBouncedDate(LocalDate bouncedDate) { this.bouncedDate = bouncedDate; }
    public String getBounceReason() { return bounceReason; }
    public void setBounceReason(String bounceReason) { this.bounceReason = bounceReason; }
    public ChequeStatus getStatus() { return status; }
    public void setStatus(ChequeStatus status) { this.status = status; }
    public UUID getPaymentId() { return paymentId; }
    public void setPaymentId(UUID paymentId) { this.paymentId = paymentId; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
