package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.adapter.persistence.entity.LedgerEntryEntity;
import com.invoicegenie.ar.adapter.persistence.mapper.LedgerMapper;
import com.invoicegenie.ar.domain.model.ledger.Account;
import com.invoicegenie.ar.domain.model.ledger.ChartOfAccountsRepository;
import com.invoicegenie.ar.domain.model.ledger.EntryType;
import com.invoicegenie.ar.domain.model.ledger.LedgerEntry;
import com.invoicegenie.ar.domain.model.ledger.LedgerRepository;
import com.invoicegenie.shared.domain.TenantId;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed implementation of {@link LedgerRepository}.
 * Bridges domain Account enum → ar_account.id when COA is seeded (STORY-020).
 */
@ApplicationScoped
public class LedgerRepositoryAdapter implements LedgerRepository {

    @PersistenceContext
    EntityManager em;

    private final LedgerMapper mapper = new LedgerMapper();

    @Inject
    Instance<ChartOfAccountsRepository> chartOfAccounts;

    @Override
    @Transactional
    public void save(TenantId tenantId, LedgerEntry entry) {
        em.merge(toEntityWithCoa(tenantId, entry));
    }

    @Override
    @Transactional
    public void saveAll(TenantId tenantId, List<LedgerEntry> entries) {
        for (LedgerEntry entry : entries) {
            em.merge(toEntityWithCoa(tenantId, entry));
        }
    }

    private LedgerEntryEntity toEntityWithCoa(TenantId tenantId, LedgerEntry entry) {
        LedgerEntryEntity entity = mapper.toEntity(tenantId, entry);
        if (chartOfAccounts != null && chartOfAccounts.isResolvable() && entry.getAccount() != null) {
            chartOfAccounts.get()
                    .findAccountIdByCode(tenantId.getValue(), entry.getAccount().name())
                    .ifPresent(entity::setAccountId);
        }
        return entity;
    }

    @Override
    public Optional<LedgerEntry> findByTenantAndId(TenantId tenantId, UUID id) {
        LedgerEntryEntity e = em.find(LedgerEntryEntity.class, id);
        if (e == null || !e.getTenantId().equals(tenantId.getValue())) {
            return Optional.empty();
        }
        return Optional.of(mapper.toDomain(e));
    }

    @Override
    public List<LedgerEntry> findByTenantAndTransactionId(TenantId tenantId, UUID transactionId) {
        return em.createQuery(
                        "SELECT e FROM LedgerEntryEntity e WHERE e.tenantId = :tenantId AND e.transactionId = :txId",
                        LedgerEntryEntity.class)
                .setParameter("tenantId", tenantId.getValue())
                .setParameter("txId", transactionId)
                .getResultStream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<LedgerEntry> findByTenantAndReference(TenantId tenantId, String referenceType, UUID referenceId) {
        return em.createQuery(
                        "SELECT e FROM LedgerEntryEntity e WHERE e.tenantId = :tenantId "
                                + "AND e.referenceType = :refType AND e.referenceId = :refId",
                        LedgerEntryEntity.class)
                .setParameter("tenantId", tenantId.getValue())
                .setParameter("refType", referenceType)
                .setParameter("refId", referenceId)
                .getResultStream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public BigDecimal getAccountBalance(TenantId tenantId, Account account, String currencyCode) {
        List<LedgerEntryEntity> entries = em.createQuery(
                        "SELECT e FROM LedgerEntryEntity e WHERE e.tenantId = :tenantId "
                                + "AND e.account = :account AND e.currency = :currency",
                        LedgerEntryEntity.class)
                .setParameter("tenantId", tenantId.getValue())
                .setParameter("account", account.name())
                .setParameter("currency", currencyCode)
                .getResultList();

        BigDecimal balance = BigDecimal.ZERO;
        for (LedgerEntryEntity e : entries) {
            LedgerEntry domain = mapper.toDomain(e);
            if (domain.getEntryType() == EntryType.DEBIT) {
                balance = balance.add(domain.getAmount().getAmount());
            } else {
                balance = balance.subtract(domain.getAmount().getAmount());
            }
        }
        return balance;
    }
}
