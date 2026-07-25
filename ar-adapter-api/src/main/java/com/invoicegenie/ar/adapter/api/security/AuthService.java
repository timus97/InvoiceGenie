package com.invoicegenie.ar.adapter.api.security;

import com.invoicegenie.ar.adapter.persistence.entity.AppUserEntity;
import com.invoicegenie.ar.adapter.persistence.entity.RefreshTokenEntity;
import com.invoicegenie.ar.adapter.persistence.repository.AppUserRepositoryAdapter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class AuthService {

    @Inject
    AppUserRepositoryAdapter users;

    @Inject
    PasswordHasher passwordHasher;

    @Inject
    RefreshTokenService refreshTokenService;

    @ConfigProperty(name = "invoicegenie.security.jwt.secret", defaultValue = "none")
    String jwtSecret;

    @ConfigProperty(name = "invoicegenie.security.jwt.access-ttl-seconds", defaultValue = "900")
    long accessTtlSeconds;

    @ConfigProperty(name = "invoicegenie.security.jwt.refresh-ttl-seconds", defaultValue = "604800")
    long refreshTtlSeconds;

    @ConfigProperty(name = "invoicegenie.security.api-keys", defaultValue = "none")
    String apiKeysConfig;

    public record AuthTokens(
            String accessToken,
            String refreshToken,
            long expiresInSeconds,
            long refreshExpiresInSeconds,
            UUID userId,
            String email,
            String displayName,
            UUID tenantId,
            Set<String> roles,
            String method
    ) {}

    public Optional<AuthTokens> loginWithEmail(String email, String password, String userAgent, String ip) {
        if (email == null || email.isBlank() || password == null) {
            return Optional.empty();
        }
        Optional<AppUserEntity> found = users.findByEmail(email);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        AppUserEntity user = found.get();
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            return Optional.empty();
        }
        if (!passwordHasher.matches(password, user.getPasswordHash())) {
            return Optional.empty();
        }
        String secret = normalize(jwtSecret);
        if (secret.isEmpty()) {
            throw new IllegalStateException("JWT secret not configured");
        }
        Set<String> roles = rolesOf(user);
        String access = ApiKeyRegistry.signHs256Jwt(
                secret, user.getTenantId().toString(), user.getEmail(), roles, accessTtlSeconds);
        RefreshTokenService.IssuedRefreshToken refresh =
                refreshTokenService.issue(user.getId(), user.getTenantId(), refreshTtlSeconds, userAgent, ip);
        users.touchLastLogin(user.getId());
        return Optional.of(new AuthTokens(
                access,
                refresh.rawToken(),
                accessTtlSeconds,
                refreshTtlSeconds,
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getTenantId(),
                roles,
                "jwt"
        ));
    }

    /** M2M API-key login (no refresh token). */
    public Optional<AuthTokens> loginWithApiKey(String apiKey) {
        ApiKeyRegistry registry = new ApiKeyRegistry(normalize(apiKeysConfig));
        Optional<String> tenant = registry.resolveTenant(apiKey == null ? null : apiKey.trim());
        if (tenant.isEmpty()) {
            return Optional.empty();
        }
        String secret = normalize(jwtSecret);
        Set<String> roles = new LinkedHashSet<>(ArRoles.M2M_ROLES);
        String access = null;
        if (!secret.isEmpty()) {
            access = ApiKeyRegistry.signHs256Jwt(secret, tenant.get(), "api-key", roles, accessTtlSeconds);
        }
        return Optional.of(new AuthTokens(
                access,
                null,
                accessTtlSeconds,
                0,
                null,
                "api-key",
                "API Key",
                UUID.fromString(tenant.get()),
                roles,
                access != null ? "jwt" : "api-key"
        ));
    }

    @Transactional
    public Optional<AuthTokens> refresh(String rawRefreshToken, String userAgent, String ip) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return Optional.empty();
        }
        Optional<RefreshTokenService.IssuedRefreshToken> rotated =
                refreshTokenService.rotate(rawRefreshToken, refreshTtlSeconds, userAgent, ip);
        if (rotated.isEmpty()) {
            return Optional.empty();
        }
        RefreshTokenEntity token = rotated.get().entity();
        Optional<AppUserEntity> userOpt = users.findById(token.getUserId());
        if (userOpt.isEmpty() || !"ACTIVE".equalsIgnoreCase(userOpt.get().getStatus())) {
            refreshTokenService.revokeAllForUser(token.getUserId());
            return Optional.empty();
        }
        AppUserEntity user = userOpt.get();
        String secret = normalize(jwtSecret);
        if (secret.isEmpty()) {
            throw new IllegalStateException("JWT secret not configured");
        }
        Set<String> roles = rolesOf(user);
        String access = ApiKeyRegistry.signHs256Jwt(
                secret, user.getTenantId().toString(), user.getEmail(), roles, accessTtlSeconds);
        return Optional.of(new AuthTokens(
                access,
                rotated.get().rawToken(),
                accessTtlSeconds,
                refreshTtlSeconds,
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getTenantId(),
                roles,
                "jwt"
        ));
    }

    public boolean logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return false;
        }
        return refreshTokenService.revoke(rawRefreshToken);
    }

    public Optional<AppUserEntity> findUser(UUID id) {
        return users.findById(id);
    }

    private static Set<String> rolesOf(AppUserEntity user) {
        Set<String> roles = new LinkedHashSet<>();
        if (user.getRoles() != null) {
            for (String r : user.getRoles()) {
                if (r != null && !r.isBlank()) {
                    roles.add(r.trim().toUpperCase(Locale.ROOT));
                }
            }
        }
        if (roles.isEmpty()) {
            roles.add(ArRoles.AR_CLERK);
        }
        return roles;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String v = value.trim();
        if (v.isEmpty() || "none".equalsIgnoreCase(v) || "-".equals(v)) {
            return "";
        }
        return v;
    }
}