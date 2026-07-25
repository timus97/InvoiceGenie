package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.adapter.persistence.entity.RefreshTokenEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class RefreshTokenRepositoryAdapter {

    @PersistenceContext
    EntityManager em;

    public Optional<RefreshTokenEntity> findByTokenHash(String tokenHash) {
        if (tokenHash == null || tokenHash.isBlank()) {
            return Optional.empty();
        }
        return em.createQuery(
                        "SELECT t FROM RefreshTokenEntity t WHERE t.tokenHash = :h",
                        RefreshTokenEntity.class)
                .setParameter("h", tokenHash)
                .getResultStream()
                .findFirst();
    }

    @Transactional
    public RefreshTokenEntity save(RefreshTokenEntity token) {
        return em.merge(token);
    }

    @Transactional
    public void revoke(UUID id, Instant at, UUID replacedBy) {
        RefreshTokenEntity t = em.find(RefreshTokenEntity.class, id);
        if (t != null && t.getRevokedAt() == null) {
            t.setRevokedAt(at);
            t.setReplacedBy(replacedBy);
        }
    }

    @Transactional
    public int revokeFamily(UUID familyId, Instant at) {
        return em.createQuery(
                        "UPDATE RefreshTokenEntity t SET t.revokedAt = :at "
                                + "WHERE t.familyId = :fid AND t.revokedAt IS NULL")
                .setParameter("at", at)
                .setParameter("fid", familyId)
                .executeUpdate();
    }

    @Transactional
    public int revokeAllForUser(UUID userId, Instant at) {
        return em.createQuery(
                        "UPDATE RefreshTokenEntity t SET t.revokedAt = :at "
                                + "WHERE t.userId = :uid AND t.revokedAt IS NULL")
                .setParameter("at", at)
                .setParameter("uid", userId)
                .executeUpdate();
    }

    @Transactional
    public void markUsed(UUID id, Instant at) {
        RefreshTokenEntity t = em.find(RefreshTokenEntity.class, id);
        if (t != null) {
            t.setLastUsedAt(at);
        }
    }
}