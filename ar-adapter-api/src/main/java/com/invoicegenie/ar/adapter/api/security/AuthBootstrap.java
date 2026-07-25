package com.invoicegenie.ar.adapter.api.security;

import com.invoicegenie.ar.adapter.persistence.entity.AppUserEntity;
import com.invoicegenie.ar.adapter.persistence.repository.AppUserRepositoryAdapter;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Seeds a bootstrap TENANT_ADMIN when the users table is empty (local/prod first boot).
 */
@ApplicationScoped
public class AuthBootstrap {

    private static final Logger LOG = Logger.getLogger(AuthBootstrap.class);
    private static final UUID DEFAULT_TENANT =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Inject
    AppUserRepositoryAdapter users;

    @Inject
    PasswordHasher passwordHasher;

    @ConfigProperty(name = "invoicegenie.security.bootstrap.email", defaultValue = "admin@invoicegenie.local")
    String bootstrapEmail;

    @ConfigProperty(name = "invoicegenie.security.bootstrap.password", defaultValue = "Admin123!")
    String bootstrapPassword;

    @ConfigProperty(name = "invoicegenie.security.bootstrap.display-name", defaultValue = "System Admin")
    String bootstrapDisplayName;

    @ConfigProperty(
            name = "invoicegenie.security.bootstrap.tenant-id",
            defaultValue = "00000000-0000-0000-0000-000000000001")
    String bootstrapTenantId;

    void onStart(@Observes StartupEvent event) {
        seedIfEmpty();
    }

    @Transactional
    void seedIfEmpty() {
        try {
            if (users.countAll() > 0) {
                return;
            }
            UUID tenantId;
            try {
                tenantId = UUID.fromString(bootstrapTenantId.trim());
            } catch (Exception e) {
                tenantId = DEFAULT_TENANT;
            }
            AppUserEntity admin = new AppUserEntity();
            admin.setId(UUID.randomUUID());
            admin.setTenantId(tenantId);
            admin.setEmail(bootstrapEmail.trim().toLowerCase());
            admin.setPasswordHash(passwordHasher.hash(bootstrapPassword));
            admin.setDisplayName(bootstrapDisplayName);
            admin.setStatus("ACTIVE");
            Set<String> roles = new LinkedHashSet<>();
            roles.add(ArRoles.TENANT_ADMIN);
            roles.add(ArRoles.AR_CONTROLLER);
            roles.add(ArRoles.AR_CLERK);
            roles.add(ArRoles.AR_AUDITOR);
            admin.setRoles(roles);
            users.save(admin);
            LOG.infof("Seeded bootstrap admin user email=%s tenant=%s — change password after first login",
                    admin.getEmail(), tenantId);
        } catch (Exception e) {
            LOG.warnf(e, "Auth bootstrap seed skipped: %s", e.getMessage());
        }
    }
}