package com.invoicegenie.ar.application.port.inbound;

import com.invoicegenie.ar.domain.model.ledger.Account;
import com.invoicegenie.ar.domain.model.ledger.LedgerEntry;
import com.invoicegenie.shared.domain.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Inbound port: ledger query operations (read-side + balance validation).
 */
public interface LedgerQueryUseCase {

    List<Account> listAccounts();

    BigDecimal getAccountBalance(TenantId tenantId, Account account, String currencyCode);

    List<LedgerEntry> getTransaction(TenantId tenantId, UUID transactionId);

    List<LedgerEntry> getByReference(TenantId tenantId, String referenceType, UUID referenceId);

    boolean validateBalanced(List<LedgerEntry> entries);
}
