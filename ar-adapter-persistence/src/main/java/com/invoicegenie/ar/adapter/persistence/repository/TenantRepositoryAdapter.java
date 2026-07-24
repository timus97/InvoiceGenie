package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.adapter.persistence.entity.TenantEntity;
import com.invoicegenie.ar.domain.model.tenant.Tenant;
import com.invoicegenie.ar.domain.model.tenant.TenantRepository;
import com.invoicegenie.ar.domain.model.tenant.TenantStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TenantRepositoryAdapter implements TenantRepository {

    @PersistenceContext
    EntityManager em;

    @Override
    @Transactional
    public void save(Tenant tenant) {
        TenantEntity e = toEntity(tenant);
        em.merge(e);
    }

    @Override
    public Optional<Tenant> findById(UUID id) {
        TenantEntity e = em.find(TenantEntity.class, id);
        return e == null ? Optional.empty() : Optional.of(toDomain(e));
    }

    @Override
    public Optional<Tenant> findByCode(String code) {
        return em.createQuery("SELECT t FROM TenantEntity t WHERE t.code = :code", TenantEntity.class)
                .setParameter("code", code == null ? null : code.trim().toUpperCase())
                .getResultStream()
                .findFirst()
                .map(this::toDomain);
    }

    @Override
    public List<Tenant> findAll() {
        return em.createQuery("SELECT t FROM TenantEntity t ORDER BY t.code", TenantEntity.class)
                .getResultStream().map(this::toDomain).toList();
    }

    @Override
    public List<Tenant> findByStatus(TenantStatus status) {
        return em.createQuery("SELECT t FROM TenantEntity t WHERE t.status = :status ORDER BY t.code", TenantEntity.class)
                .setParameter("status", status)
                .getResultStream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsByCode(String code) {
        Long count = em.createQuery("SELECT COUNT(t) FROM TenantEntity t WHERE t.code = :code", Long.class)
                .setParameter("code", code == null ? null : code.trim().toUpperCase())
                .getSingleResult();
        return count != null && count > 0;
    }

    private TenantEntity toEntity(Tenant t) {
        TenantEntity e = new TenantEntity();
        e.setId(t.getId());
        e.setCode(t.getCode());
        e.setName(t.getName());
        e.setBaseCurrency(t.getBaseCurrency());
        e.setStatus(t.getStatus());
        e.setSettings(t.getSettingsJson());
        e.setCreatedAt(t.getCreatedAt());
        e.setUpdatedAt(t.getUpdatedAt());
        return e;
    }

    private Tenant toDomain(TenantEntity e) {
        return new Tenant(e.getId(), e.getCode(), e.getName(), e.getBaseCurrency(),
                e.getStatus(), e.getSettings(), e.getCreatedAt(), e.getUpdatedAt());
    }
}