package com.invoicegenie.ar.adapter.api.security;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Production fails closed if security is disabled or secrets are missing (STORY-003).
 */
@ApplicationScoped
public class ProdSecurityValidator {

    private static final Logger LOG = Logger.getLogger(ProdSecurityValidator.class);

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

    void onStart(@Observes StartupEvent event) {
        String p = profile == null ? "" : profile.toLowerCase();
        // Quarkus may list multiple active profiles; treat presence of "prod" as production.
        if (!p.contains("prod")) {
            return;
        }
        if (!securityEnabled) {
            throw new IllegalStateException(
                    "Production startup aborted: invoicegenie.security.enabled must be true in %prod");
        }
        String m = mode == null ? "api-key" : mode.trim().toLowerCase();
        if ("api-key".equals(m)) {
            if (isBlankSecret(apiKeys)) {
                throw new IllegalStateException(
                        "Production startup aborted: invoicegenie.security.api-keys required for api-key mode");
            }
        } else if ("jwt".equals(m)) {
            if (isBlankSecret(jwtSecret)) {
                throw new IllegalStateException(
                        "Production startup aborted: invoicegenie.security.jwt.secret required for jwt mode");
            }
            if (jwtSecret.trim().length() < 16) {
                throw new IllegalStateException(
                        "Production startup aborted: JWT secret must be at least 16 characters");
            }
        } else {
            throw new IllegalStateException(
                    "Production startup aborted: unknown invoicegenie.security.mode=" + mode);
        }
        LOG.info("Production security validation passed (mode=" + m + ")");
    }

    private static boolean isBlankSecret(String v) {
        if (v == null) {
            return true;
        }
        String t = v.trim();
        return t.isEmpty() || "none".equalsIgnoreCase(t) || "-".equals(t);
    }
}