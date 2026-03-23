package com.invoicegenie.ar.domain.model.ledger;

import com.invoicegenie.shared.domain.Money;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A single ledger entry in double-entry accounting.
 * 
 * <p>Each entry represents either a debit or credit to an account.
 * A complete transaction consists of at least two entries where:
 * - Total debits = Total credits
 * 
 * <p>Entry types:
 * <ul>
 *   <li>DEBIT: Increases asset/expense accounts, decreases liability/equity/revenue</li>
 *   <li>CREDIT: Increases liability/equity/revenue, decreases asset/expense</li>
 * </ul>
 * 
 * <p>Business rules:
 * <ul>
 *   <li>Amount must be positive</li>
 *   <li>Account must be valid</li>
 *   <li>Each entry must be part of a balanced transaction</li>
 * </ul>
 */
public final class LedgerEntry {

    private final UUID id;
    private final Account account;
    private final Money amount;
    private final EntryType entryType;
    private final String description;
    private final UUID transactionId; // Links debit/credit entries together
    private final String referenceType; // e.g., "INVOICE", "PAYMENT"
    private final UUID referenceId; // ID of the source document
    private final Instant createdAt;

    public LedgerEntry(Account account, Money amount, EntryType entryType, 
                       String description, UUID transactionId,
                       String referenceType, UUID referenceId) {
        this(UUID.randomUUID(), account, amount, entryType, description, 
             transactionId, referenceType, referenceId, Instant.now());
    }

    /** For reconstitution from persistence. */
    public LedgerEntry(UUID id, Account account, Money amount, EntryType entryType,
                       String description, UUID transactionId,
                       String referenceType, UUID referenceId, Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.account = Objects.requireNonNull(account);
        this.amount = Objects.requireNonNull(amount);
        if (amount.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("Ledger entry amount must be positive");
        }
        this.entryType = Objects.requireNonNull(entryType);
        this.description = description;
        this.transactionId = Objects.requireNonNull(transactionId);
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public UUID getId() { return id; }
    public Account getAccount() { return account; }
    public Money getAmount() { return amount; }
    public EntryType getEntryType() { return entryType; }
    public String getDescription() { return description; }
    public UUID getTransactionId() { return transactionId; }
    public String getReferenceType() { return referenceType; }
    public UUID getReferenceId() { return referenceId; }
    public Instant getCreatedAt() { return createdAt; }

    /**
     * Get the signed amount for balance calculation.
     * Debit increases asset/expense, Credit increases liability/equity/revenue.
     */
    public Money getSignedAmount() {
        if (entryType == EntryType.DEBIT) {
            return amount;
        } else {
            // Credit is negative for balance calculation
            return Money.of("-" + amount.getAmount().toString(), amount.getCurrencyCode());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LedgerEntry that = (LedgerEntry) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format("LedgerEntry[%s %s %s to %s: %s]",
                entryType, amount.getAmount(), amount.getCurrencyCode(),
                account, description);
    }
}
