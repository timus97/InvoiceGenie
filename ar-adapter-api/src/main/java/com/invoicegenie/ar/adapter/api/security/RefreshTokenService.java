package com.invoicegenie.ar.adapter.api.security;

import com.invoicegenie.ar.adapter.persistence.entity.RefreshTokenEntity;
import com.invoicegenie.ar.adapter.persistence.repository.RefreshTokenRepositoryAdapter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Inject
    RefreshTokenRepositoryAdapter refreshTokens;

    public record IssuedRefreshToken(String rawToken, RefreshTokenEntity entity) {}

    public IssuedRefreshToken issue(UUID userId, UUID tenantId, long ttlSeconds, String userAgent, String ip) {
        Instant now = Instant.now();
        String raw = generateRawToken();
        RefreshTokenEntity e = new RefreshTokenEntity();
        e.setId(UUID.randomUUID());
        e.setUserId(userId);
        e.setTenantId(tenantId);
        e.setTokenHash(hash(raw));
        e.setFamilyId(UUID.randomUUID());
        e.setExpiresAt(now.plusSeconds(Math.max(60, ttlSeconds)));
        e.setCreatedAt(now);
        e.setUserAgent(truncate(userAgent, 512));
        e.setIpAddress(truncate(ip, 64));
        refreshTokens.save(e);
        return new IssuedRefreshToken(raw, e);
    }

    /**
     * Rotate: revoke current token, issue replacement in same family.
     * On reuse of a revoked token, revoke the entire family (theft detection).
     */
    public Optional<IssuedRefreshToken> rotate(
            String rawToken, long ttlSeconds, String userAgent, String ip) {
        Instant now = Instant.now();
        Optional<RefreshTokenEntity> found = refreshTokens.findByTokenHash(hash(rawToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        RefreshTokenEntity current = found.get();

        // Reuse of revoked/expired token → revoke family
        if (current.getRevokedAt() != null || current.getExpiresAt().isBefore(now)) {
            refreshTokens.revokeFamily(current.getFamilyId(), now);
            return Optional.empty();
        }

        String newRaw = generateRawToken();
        RefreshTokenEntity next = new RefreshTokenEntity();
        next.setId(UUID.randomUUID());
        next.setUserId(current.getUserId());
        next.setTenantId(current.getTenantId());
        next.setTokenHash(hash(newRaw));
        next.setFamilyId(current.getFamilyId());
        next.setExpiresAt(now.plusSeconds(Math.max(60, ttlSeconds)));
        next.setCreatedAt(now);
        next.setUserAgent(truncate(userAgent, 512));
        next.setIpAddress(truncate(ip, 64));
        refreshTokens.save(next);

        refreshTokens.revoke(current.getId(), now, next.getId());
        refreshTokens.markUsed(current.getId(), now);
        return Optional.of(new IssuedRefreshToken(newRaw, next));
    }

    public boolean revoke(String rawToken) {
        Instant now = Instant.now();
        Optional<RefreshTokenEntity> found = refreshTokens.findByTokenHash(hash(rawToken));
        if (found.isEmpty()) {
            return false;
        }
        RefreshTokenEntity t = found.get();
        if (t.getRevokedAt() == null) {
            refreshTokens.revoke(t.getId(), now, null);
        }
        return true;
    }

    public int revokeAllForUser(UUID userId) {
        return refreshTokens.revokeAllForUser(userId, Instant.now());
    }

    public Optional<RefreshTokenEntity> findActive(String rawToken) {
        Instant now = Instant.now();
        return refreshTokens.findByTokenHash(hash(rawToken))
                .filter(t -> t.isActive(now));
    }

    public static String hash(String rawToken) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String generateRawToken() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }
}