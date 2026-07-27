package com.invoicegenie.ar.adapter.api.security;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Locale;
import java.util.Set;

/**
 * Production fails closed if security is disabled, secrets are missing,
 * or demo/default credentials are still in use (STORY-003, STORY-017).
 */
@ApplicationScoped
public class ProdSecurityValidator {

    private static final Logger LOG = Logger.getLogger(ProdSecurityValidator.class);

    /** Known demo/default passwords that must never ship in %prod. */
    private static final Set<String> FORBIDDEN_PASSWORDS = Set.of(
            "ar",
            "password",
            "postgres",
            "change-me",
            "change-me-strong-password",
            "changeme",
            "secret",
            "admin",
            "invoicegenie"
    );

    private static final Set<String> FORBIDDEN_API_KEY_PREFIXES = Set.of(
            "dev-local-key",
            "test-key",
            "demo"
    );

    @ConfigProperty(name = "quarkus.profile", defaultValue = "prod")
    String profile;

    @ConfigProperty(name = "invoicegenie.security.enabled", defaultValue = "false")
    boolean securityEnabled;

    @ConfigProperty(name = "invoicegenie.security.mode", defaultValue = "api-key")
    String mode;

    @ConfigProperty(name = "invoicegenie.security.api-keys", defaultValue = "none")
    String apiKeys;

    @ConfigProperty(name = "invoicegenie.security.jwt.secret", defaultValue = "none")
    String jwtSecret;

    @ConfigProperty(name = "quarkus.datasource.password", defaultValue = "ar")
    String datasourcePassword;

    @ConfigProperty(name = "quarkus.datasource.username", defaultValue = "ar")
    String datasourceUsername;

    @ConfigProperty(name = "invoicegenie.security.oidc.issuer", defaultValue = "none")
    String oidcIssuer;

    @ConfigProperty(name = "invoicegenie.security.oidc.jwks-uri", defaultValue = "none")
    String oidcJwksUri;

    void onStart(@Observes StartupEvent event) {
        String p = profile == null ? "" : profile.toLowerCase(Locale.ROOT);
        // Quarkus may list multiple active profiles; treat presence of "prod" as production.
        if (!p.contains("prod")) {
            return;
        }
        if (!securityEnabled) {
            throw new IllegalStateException(
                    "Production startup aborted: invoicegenie.security.enabled must be true in %prod");
        }
        rejectDefaultDatasourceCredentials();
        String m = mode == null ? "api-key" : mode.trim().toLowerCase(Locale.ROOT);
        switch (m) {
            case "api-key" -> {
                requireApiKeys(m);
            }
            case "jwt" -> requireLocalJwtSecret();
            case "hybrid" -> {
                requireApiKeys(m);
                requireLocalJwtSecret();
            }
            case "oidc" -> requireOidcConfig();
            case "hybrid-oidc", "oidc-hybrid" -> {
                // OIDC required; local JWT / API keys optional but validated when present
                requireOidcConfig();
                if (!isBlankSecret(apiKeys)) {
                    rejectDemoApiKeys(apiKeys);
                }
                if (!isBlankSecret(jwtSecret)) {
                    if (jwtSecret.trim().length() < 16) {
                        throw new IllegalStateException(
                                "Production startup aborted: JWT secret must be at least 16 characters");
                    }
                }
            }
            default -> throw new IllegalStateException(
                    "Production startup aborted: unknown invoicegenie.security.mode=" + mode
                            + " (expected api-key, jwt, hybrid, oidc, or hybrid-oidc)");
        }
        LOG.info("Production security validation passed (mode=" + m + ")");
    }

    private void requireApiKeys(String m) {
        if (isBlankSecret(apiKeys)) {
            throw new IllegalStateException(
                    "Production startup aborted: invoicegenie.security.api-keys required for " + m + " mode");
        }
        rejectDemoApiKeys(apiKeys);
    }

    private void requireLocalJwtSecret() {
        if (isBlankSecret(jwtSecret)) {
            throw new IllegalStateException(
                    "Production startup aborted: invoicegenie.security.jwt.secret required");
        }
        if (jwtSecret.trim().length() < 16) {
            throw new IllegalStateException(
                    "Production startup aborted: JWT secret must be at least 16 characters");
        }
        if (FORBIDDEN_PASSWORDS.contains(jwtSecret.trim().toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(
                    "Production startup aborted: JWT secret looks like a demo/default value");
        }
    }

    private void requireOidcConfig() {
        if (isBlankSecret(oidcIssuer)) {
            throw new IllegalStateException(
                    "Production startup aborted: invoicegenie.security.oidc.issuer required for OIDC mode");
        }
        if (isBlankSecret(oidcJwksUri)) {
            throw new IllegalStateException(
                    "Production startup aborted: invoicegenie.security.oidc.jwks-uri required for OIDC mode");
        }
    }

    private void rejectDefaultDatasourceCredentials() {
        String pass = datasourcePassword == null ? "" : datasourcePassword.trim();
        String user = datasourceUsername == null ? "" : datasourceUsername.trim();
        if (pass.isEmpty()) {
            throw new IllegalStateException(
                    "Production startup aborted: quarkus.datasource.password must not be empty");
        }
        if (FORBIDDEN_PASSWORDS.contains(pass.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(
                    "Production startup aborted: datasource password is a known demo/default value; set a strong secret");
        }
        // Classic compose default pair ar/ar
        if ("ar".equalsIgnoreCase(user) && "ar".equalsIgnoreCase(pass)) {
            throw new IllegalStateException(
                    "Production startup aborted: refuse default ar/ar datasource credentials in %prod");
        }
    }

    private static void rejectDemoApiKeys(String keys) {
        String[] parts = keys.split(",");
        for (String part : parts) {
            String entry = part == null ? "" : part.trim();
            if (entry.isEmpty()) {
                continue;
            }
            String keyOnly = entry.contains(":") ? entry.substring(0, entry.indexOf(':')).trim() : entry;
            String lower = keyOnly.toLowerCase(Locale.ROOT);
            for (String forbidden : FORBIDDEN_API_KEY_PREFIXES) {
                if (lower.equals(forbidden) || lower.startsWith(forbidden + "-") || lower.startsWith(forbidden + "_")) {
                    throw new IllegalStateException(
                            "Production startup aborted: demo/dev API key '" + keyOnly
                                    + "' is not allowed in %prod; use a strong secret key");
                }
            }
        }
    }

    private static boolean isBlankSecret(String v) {
        if (v == null) {
            return true;
        }
        String t = v.trim();
        return t.isEmpty() || "none".equalsIgnoreCase(t) || "-".equals(t);
    }
}