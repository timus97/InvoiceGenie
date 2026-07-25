package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.adapter.persistence.entity.AppUserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AppUserRepositoryAdapter {

    @PersistenceContext
    EntityManager em;

    public Optional<AppUserEntity> findById(UUID id) {
        return Optional.ofNullable(em.find(AppUserEntity.class, id));
    }

    public Optional<AppUserEntity> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return em.createQuery(
                        "SELECT u FROM AppUserEntity u WHERE lower(u.email) = :email",
                        AppUserEntity.class)
                .setParameter("email", normalized)
                .getResultStream()
                .findFirst();
    }

    public List<AppUserEntity> findByTenantId(UUID tenantId) {
        return em.createQuery(
                        "SELECT u FROM AppUserEntity u WHERE u.tenantId = :tid ORDER BY u.email",
                        AppUserEntity.class)
                .setParameter("tid", tenantId)
                .getResultList();
    }

    public boolean existsByEmail(String email) {
        return findByEmail(email).isPresent();
    }

    public long countAll() {
        return em.createQuery("SELECT COUNT(u) FROM AppUserEntity u", Long.class)
                .getSingleResult();
    }

    @Transactional
    public AppUserEntity save(AppUserEntity user) {
        if (user.getId() == null) {
            user.setId(UUID.randomUUID());
        }
        Instant now = Instant.now();
        if (user.getCreatedAt() == null) {
            user.setCreatedAt(now);
        }
        user.setUpdatedAt(now);
        return em.merge(user);
    }

    @Transactional
    public void touchLastLogin(UUID userId) {
        AppUserEntity u = em.find(AppUserEntity.class, userId);
        if (u != null) {
            u.setLastLoginAt(Instant.now());
            u.setUpdatedAt(Instant.now());
        }
    }
}