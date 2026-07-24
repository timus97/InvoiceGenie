package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.adapter.persistence.entity.AccountEntity;
import com.invoicegenie.ar.domain.model.ledger.Account;
import com.invoicegenie.ar.domain.model.ledger.ChartOfAccountsRepository;
import com.invoicegenie.shared.domain.UuidV7;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Seeds and reads {@code ar_account} rows. Safe no-op when the table is missing (legacy DBs).
 */
@ApplicationScoped
public class ChartOfAccountsRepositoryAdapter implements ChartOfAccountsRepository {

    private static final Logger LOG = Logger.getLogger(ChartOfAccountsRepositoryAdapter.class.getName());

    @PersistenceContext
    EntityManager em;

    @Override
    @Transactional
    public int seedSystemAccounts(UUID tenantId) {
        if (tenantId == null || !isTableAvailable()) {
            return 0;
        }
        int inserted = 0;
        Instant now = Instant.now();
        for (Account account : Account.values()) {
            if (existsCode(tenantId, account.name())) {
                continue;
            }
            AccountEntity row = new AccountEntity();
            row.setId(UuidV7.generate());
            row.setTenantId(tenantId);
            row.setCode(account.name());
            row.setName(account.getDisplayName());
            row.setType(account.getType().name());
            row.setCategory("SYSTEM");
            row.setSystem(true);
            row.setActive(true);
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            em.persist(row);
            inserted++;
        }
        if (inserted > 0) {
            LOG.info(String.format("Seeded %d system COA accounts for tenant %s", inserted, tenantId));
        }
        return inserted;
    }

    @Override
    public Optional<UUID> findAccountIdByCode(UUID tenantId, String code) {
        if (tenantId == null || code == null || code.isBlank() || !isTableAvailable()) {
            return Optional.empty();
        }
        try {
            List<UUID> ids = em.createQuery(
                            "SELECT a.id FROM AccountEntity a WHERE a.tenantId = :tid AND a.code = :code",
                            UUID.class)
                    .setParameter("tid", tenantId)
                    .setParameter("code", code.trim().toUpperCase())
                    .setMaxResults(1)
                    .getResultList();
            return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
        } catch (Exception e) {
            LOG.log(Level.FINE, "findAccountIdByCode failed", e);
            return Optional.empty();
        }
    }

    @Override
    public List<SeededAccount> listByTenant(UUID tenantId) {
        if (tenantId == null || !isTableAvailable()) {
            return List.of();
        }
        try {
            List<AccountEntity> rows = em.createQuery(
                            "SELECT a FROM AccountEntity a WHERE a.tenantId = :tid ORDER BY a.code",
                            AccountEntity.class)
                    .setParameter("tid", tenantId)
                    .getResultList();
            List<SeededAccount> out = new ArrayList<>();
            for (AccountEntity a : rows) {
                out.add(new SeededAccount(
                        a.getId(), a.getCode(), a.getName(), a.getType(), a.isSystem(), a.isActive()));
            }
            return out;
        } catch (Exception e) {
            LOG.log(Level.FINE, "listByTenant COA failed", e);
            return List.of();
        }
    }

    private boolean existsCode(UUID tenantId, String code) {
        Long n = em.createQuery(
                        "SELECT COUNT(a) FROM AccountEntity a WHERE a.tenantId = :tid AND a.code = :code",
                        Long.class)
                .setParameter("tid", tenantId)
                .setParameter("code", code)
                .getSingleResult();
        return n != null && n > 0;
    }

    /**
     * Detects whether ar_account is mapped / queryable. On first call failure we still return true
     * if EntityManager is open (H2 with generation=update creates the table from AccountEntity).
     */
    private boolean isTableAvailable() {
        if (em == null) {
            return false;
        }
        try {
            em.createQuery("SELECT COUNT(a) FROM AccountEntity a").setMaxResults(1).getResultList();
            return true;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "ar_account / AccountEntity not available — COA seed skipped", e);
            return false;
        }
    }
}
