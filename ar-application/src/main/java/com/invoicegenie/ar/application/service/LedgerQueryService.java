package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.LedgerQueryUseCase;
import com.invoicegenie.ar.domain.model.ledger.Account;
import com.invoicegenie.ar.domain.model.ledger.LedgerEntry;
import com.invoicegenie.ar.domain.model.ledger.LedgerRepository;
import com.invoicegenie.ar.domain.service.LedgerService;
import com.invoicegenie.shared.domain.TenantId;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Application service: ledger query operations.
 */
public class LedgerQueryService implements LedgerQueryUseCase {

    private final LedgerService ledgerService;
    private final LedgerRepository ledgerRepository;

    public LedgerQueryService(LedgerService ledgerService, LedgerRepository ledgerRepository) {
        this.ledgerService = ledgerService;
        this.ledgerRepository = ledgerRepository;
    }

    @Override
    public List<Account> listAccounts() {
        return Arrays.asList(Account.values());
    }

    @Override
    public BigDecimal getAccountBalance(TenantId tenantId, Account account, String currencyCode) {
        return ledgerRepository.getAccountBalance(tenantId, account, currencyCode);
    }

    @Override
    public List<LedgerEntry> getTransaction(TenantId tenantId, UUID transactionId) {
        return ledgerRepository.findByTenantAndTransactionId(tenantId, transactionId);
    }

    @Override
    public List<LedgerEntry> getByReference(TenantId tenantId, String referenceType, UUID referenceId) {
        return ledgerRepository.findByTenantAndReference(tenantId, referenceType, referenceId);
    }

    @Override
    public boolean validateBalanced(List<LedgerEntry> entries) {
        return ledgerService.validateBalanced(entries);
    }
}
