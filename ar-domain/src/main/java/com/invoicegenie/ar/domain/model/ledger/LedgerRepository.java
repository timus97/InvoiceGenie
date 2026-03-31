package com.invoicegenie.ar.domain.model.ledger;

import com.invoicegenie.shared.domain.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port (outbound): persistence for Ledger entries.
 */
public interface LedgerRepository {

    void save(TenantId tenantId, LedgerEntry entry);

    void saveAll(TenantId tenantId, List<LedgerEntry> entries);

    Optional<LedgerEntry> findByTenantAndId(TenantId tenantId, UUID id);

    /**
     * Find all entries for a transaction.
     */
    List<LedgerEntry> findByTenantAndTransactionId(TenantId tenantId, UUID transactionId);

    /**
     * Find all entries for a reference (invoice or payment).
     */
    List<LedgerEntry> findByTenantAndReference(TenantId tenantId, String referenceType, UUID referenceId);

    /**
     * Get account balance (sum of debits - credits for debit-normal, credits - debits for credit-normal).
     */
    java.math.BigDecimal getAccountBalance(TenantId tenantId, Account account, String currencyCode);
}
