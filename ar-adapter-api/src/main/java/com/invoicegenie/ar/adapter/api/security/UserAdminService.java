package com.invoicegenie.ar.adapter.api.security;

import com.invoicegenie.ar.adapter.persistence.entity.AppUserEntity;
import com.invoicegenie.ar.adapter.persistence.repository.AppUserRepositoryAdapter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@ApplicationScoped
public class UserAdminService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Set<String> ALLOWED_ROLES = Set.of(
            ArRoles.AR_CLERK, ArRoles.AR_CONTROLLER, ArRoles.AR_AUDITOR, ArRoles.TENANT_ADMIN);

    @Inject
    AppUserRepositoryAdapter users;

    @Inject
    PasswordHasher passwordHasher;

    @Inject
    RefreshTokenService refreshTokenService;

    public List<AppUserEntity> listUsers(UUID tenantId) {
        return users.findByTenantId(tenantId);
    }

    public Optional<AppUserEntity> get(UUID tenantId, UUID userId) {
        return users.findById(userId).filter(u -> u.getTenantId().equals(tenantId));
    }

    @Transactional
    public Result create(
            UUID tenantId,
            String email,
            String password,
            String displayName,
            Set<String> roles
    ) {
        if (tenantId == null) {
            return Result.fail("TENANT_REQUIRED", "Tenant is required");
        }
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null) {
            return Result.fail("VALIDATION_ERROR", "Valid email is required");
        }
        if (password == null || password.length() < 8) {
            return Result.fail("VALIDATION_ERROR", "Password must be at least 8 characters");
        }
        if (displayName == null || displayName.isBlank()) {
            return Result.fail("VALIDATION_ERROR", "Display name is required");
        }
        if (users.existsByEmail(normalizedEmail)) {
            return Result.fail("CONFLICT", "Email already registered");
        }
        Set<String> normalizedRoles = normalizeRoles(roles);
        if (normalizedRoles.isEmpty()) {
            return Result.fail("VALIDATION_ERROR", "At least one valid role is required");
        }

        AppUserEntity user = new AppUserEntity();
        user.setId(UUID.randomUUID());
        user.setTenantId(tenantId);
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordHasher.hash(password));
        user.setDisplayName(displayName.trim());
        user.setStatus("ACTIVE");
        user.setRoles(normalizedRoles);
        AppUserEntity saved = users.save(user);
        return Result.ok(saved);
    }

    @Transactional
    public Result update(
            UUID tenantId,
            UUID userId,
            String displayName,
            Set<String> roles,
            String status,
            String newPassword
    ) {
        Optional<AppUserEntity> found = get(tenantId, userId);
        if (found.isEmpty()) {
            return Result.fail("NOT_FOUND", "User not found");
        }
        AppUserEntity user = found.get();
        if (displayName != null && !displayName.isBlank()) {
            user.setDisplayName(displayName.trim());
        }
        if (roles != null) {
            Set<String> normalizedRoles = normalizeRoles(roles);
            if (normalizedRoles.isEmpty()) {
                return Result.fail("VALIDATION_ERROR", "At least one valid role is required");
            }
            user.setRoles(normalizedRoles);
        }
        if (status != null && !status.isBlank()) {
            String s = status.trim().toUpperCase(Locale.ROOT);
            if (!"ACTIVE".equals(s) && !"DISABLED".equals(s)) {
                return Result.fail("VALIDATION_ERROR", "Status must be ACTIVE or DISABLED");
            }
            user.setStatus(s);
            if ("DISABLED".equals(s)) {
                refreshTokenService.revokeAllForUser(user.getId());
            }
        }
        if (newPassword != null && !newPassword.isBlank()) {
            if (newPassword.length() < 8) {
                return Result.fail("VALIDATION_ERROR", "Password must be at least 8 characters");
            }
            user.setPasswordHash(passwordHasher.hash(newPassword));
            refreshTokenService.revokeAllForUser(user.getId());
        }
        return Result.ok(users.save(user));
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String e = email.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(e).matches()) {
            return null;
        }
        return e;
    }

    private static Set<String> normalizeRoles(Set<String> roles) {
        Set<String> out = new LinkedHashSet<>();
        if (roles == null) {
            return out;
        }
        for (String r : roles) {
            if (r == null || r.isBlank()) {
                continue;
            }
            String role = r.trim().toUpperCase(Locale.ROOT);
            if (ALLOWED_ROLES.contains(role)) {
                out.add(role);
            }
        }
        return out;
    }

    public record Result(boolean success, String code, String message, AppUserEntity user) {
        static Result ok(AppUserEntity u) {
            return new Result(true, null, null, u);
        }

        static Result fail(String code, String message) {
            return new Result(false, code, message, null);
        }
    }
}