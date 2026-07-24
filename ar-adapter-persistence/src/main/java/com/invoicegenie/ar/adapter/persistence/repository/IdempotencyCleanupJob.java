package com.invoicegenie.ar.adapter.persistence.repository;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * TTL retention job for {@code ar_idempotency} rows (P2-06).
 */
@ApplicationScoped
public class IdempotencyCleanupJob {

    private static final Logger LOG = Logger.getLogger(IdempotencyCleanupJob.class);

    @PersistenceContext
    EntityManager em;

    @ConfigProperty(name = "idempotency.retention-days", defaultValue = "7")
    int retentionDays;

    @ConfigProperty(name = "idempotency.cleanup-enabled", defaultValue = "true")
    boolean enabled;

    @Scheduled(cron = "${idempotency.cleanup-cron:0 30 0 * * ?}")
    @Transactional
    public void cleanup() {
        if (!enabled) {
            return;
        }
        try {
            Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
            int deleted = em.createQuery("DELETE FROM IdempotencyEntity i WHERE i.createdAt < :cutoff")
                    .setParameter("cutoff", cutoff)
                    .executeUpdate();
            if (deleted > 0) {
                LOG.infof("Idempotency cleanup removed %d rows older than %d days", deleted, retentionDays);
            }
        } catch (Exception e) {
            LOG.debugf(e, "Idempotency cleanup skipped: %s", e.getMessage());
        }
    }
}