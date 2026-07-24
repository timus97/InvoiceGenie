package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.adapter.persistence.entity.ExchangeRateEntity;
import com.invoicegenie.ar.domain.model.fx.ExchangeRate;
import com.invoicegenie.ar.domain.model.fx.ExchangeRateRepository;
import com.invoicegenie.shared.domain.TenantId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ExchangeRateRepositoryAdapter implements ExchangeRateRepository {

    @PersistenceContext
    EntityManager em;

    @Override
    @Transactional
    public void save(TenantId tenantId, ExchangeRate rate) {
        ExchangeRateEntity e = new ExchangeRateEntity();
        e.setId(rate.getId());
        e.setTenantId(tenantId.getValue());
        e.setFromCurrency(rate.getFromCurrency());
        e.setToCurrency(rate.getToCurrency());
        e.setRate(rate.getRate());
        e.setEffectiveDate(rate.getEffectiveDate());
        e.setSource(rate.getSource());
        em.merge(e);
    }

    @Override
    public Optional<ExchangeRate> findById(TenantId tenantId, UUID id) {
        ExchangeRateEntity e = em.find(ExchangeRateEntity.class, id);
        if (e == null || !e.getTenantId().equals(tenantId.getValue())) {
            return Optional.empty();
        }
        return Optional.of(toDomain(e));
    }

    @Override
    public Optional<ExchangeRate> findLatest(TenantId tenantId, String fromCurrency, String toCurrency, LocalDate asOf) {
        return em.createQuery(
                        "SELECT e FROM ExchangeRateEntity e WHERE e.tenantId = :tid " +
                                "AND e.fromCurrency = :from AND e.toCurrency = :to " +
                                "AND e.effectiveDate <= :asOf ORDER BY e.effectiveDate DESC",
                        ExchangeRateEntity.class)
                .setParameter("tid", tenantId.getValue())
                .setParameter("from", fromCurrency.trim().toUpperCase())
                .setParameter("to", toCurrency.trim().toUpperCase())
                .setParameter("asOf", asOf)
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .map(this::toDomain);
    }

    @Override
    public List<ExchangeRate> findAllByTenant(TenantId tenantId) {
        return em.createQuery(
                        "SELECT e FROM ExchangeRateEntity e WHERE e.tenantId = :tid " +
                                "ORDER BY e.effectiveDate DESC, e.fromCurrency, e.toCurrency",
                        ExchangeRateEntity.class)
                .setParameter("tid", tenantId.getValue())
                .getResultStream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public void delete(TenantId tenantId, UUID id) {
        ExchangeRateEntity e = em.find(ExchangeRateEntity.class, id);
        if (e != null && e.getTenantId().equals(tenantId.getValue())) {
            em.remove(e);
        }
    }

    private ExchangeRate toDomain(ExchangeRateEntity e) {
        return new ExchangeRate(e.getId(), e.getFromCurrency(), e.getToCurrency(),
                e.getRate(), e.getEffectiveDate(), e.getSource());
    }
}