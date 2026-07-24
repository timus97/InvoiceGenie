package com.invoicegenie.ar.domain.model.ledger;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant chart of accounts (ar_account rows). Bridges domain {@link Account} enum to persisted system accounts.
 */
public interface ChartOfAccountsRepository {

    /**
     * Seeds system accounts for a tenant from the domain {@link Account} enum.
     * Idempotent: skips codes that already exist. No-op if the COA table is unavailable.
     *
     * @return number of rows inserted
     */
    int seedSystemAccounts(UUID tenantId);

    /**
     * Resolves a seeded account row id by enum/code (e.g. {@code AR}, {@code BANK}).
     */
    Optional<UUID> findAccountIdByCode(UUID tenantId, String code);

    /**
     * Lists seeded account codes for a tenant (empty if table missing / unseeded).
     */
    List<SeededAccount> listByTenant(UUID tenantId);

    record SeededAccount(UUID id, String code, String name, String type, boolean system, boolean active) {}
}
