package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.domain.model.ledger.Account;
import com.invoicegenie.ar.domain.model.ledger.LedgerEntry;
import com.invoicegenie.ar.domain.model.ledger.LedgerRepository;
import com.invoicegenie.shared.domain.TenantId;

import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of LedgerRepository for development.
 * In production, use a JPA-backed implementation.
 */
@ApplicationScoped
public class LedgerRepositoryAdapter implements LedgerRepository {

    // In-memory storage: tenantId -> List<LedgerEntry>
    private final ConcurrentMap<UUID, List<LedgerEntry>> storage = new ConcurrentHashMap<>();

    @Override
    public void save(TenantId tenantId, LedgerEntry entry) {
        storage.computeIfAbsent(tenantId.getValue(), k -> new ArrayList<>()).add(entry);
    }

    @Override
    public void saveAll(TenantId tenantId, List<LedgerEntry> entries) {
        storage.computeIfAbsent(tenantId.getValue(), k -> new ArrayList<>()).addAll(entries);
    }

    @Override
    public Optional<LedgerEntry> findByTenantAndId(TenantId tenantId, UUID id) {
        return storage.getOrDefault(tenantId.getValue(), List.of()).stream()
                .filter(e -> e.getId().equals(id))
                .findFirst();
    }

    @Override
    public List<LedgerEntry> findByTenantAndTransactionId(TenantId tenantId, UUID transactionId) {
        return storage.getOrDefault(tenantId.getValue(), List.of()).stream()
                .filter(e -> e.getTransactionId().equals(transactionId))
                .collect(Collectors.toList());
    }

    @Override
    public List<LedgerEntry> findByTenantAndReference(TenantId tenantId, String referenceType, UUID referenceId) {
        return storage.getOrDefault(tenantId.getValue(), List.of()).stream()
                .filter(e -> referenceType.equals(e.getReferenceType()) && referenceId.equals(e.getReferenceId()))
                .collect(Collectors.toList());
    }

    @Override
    public BigDecimal getAccountBalance(TenantId tenantId, Account account, String currencyCode) {
        List<LedgerEntry> entries = storage.getOrDefault(tenantId.getValue(), List.of()).stream()
                .filter(e -> e.getAccount() == account && e.getAmount().getCurrencyCode().equals(currencyCode))
                .collect(Collectors.toList());

        BigDecimal balance = BigDecimal.ZERO;
        for (LedgerEntry entry : entries) {
            if (entry.getEntryType().name().equals("DEBIT")) {
                balance = balance.add(entry.getAmount().getAmount());
            } else {
                balance = balance.subtract(entry.getAmount().getAmount());
            }
        }
        return balance;
    }
}
