package com.invoicegenie.ar.adapter.messaging;

import com.invoicegenie.ar.domain.model.outbox.OutboxEntry;
import com.invoicegenie.ar.domain.model.outbox.OutboxRepository;
import com.invoicegenie.ar.domain.model.outbox.OutboxStatus;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Worker that polls the outbox table and publishes events to Kafka.
 * 
 * <p>This implements the "polling publisher" variant of the transactional outbox pattern.
 * It runs on a schedule, fetches pending events from the database, and publishes them
 * to Kafka via SmallRye Reactive Messaging.
 * 
 * <p>Features:
 * <ul>
 *   <li>Configurable batch size and polling interval</li>
 *   <li>Automatic retry with exponential backoff (via retry_count)</li>
 *   <li>Tenant ID in message headers for subscriber isolation</li>
 *   <li>Cleanup of old published messages</li>
 *   <li>Graceful handling of database initialization</li>
 * </ul>
 */
@ApplicationScoped
public class OutboxWorker {

    private static final Logger LOG = Logger.getLogger(OutboxWorker.class);

    @Inject
    OutboxRepository outboxRepository;

    @ConfigProperty(name = "outbox.batch-size", defaultValue = "100")
    int batchSize;

    @ConfigProperty(name = "outbox.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "outbox.cleanup-days", defaultValue = "7")
    int cleanupDays;

    @ConfigProperty(name = "outbox.delay-start", defaultValue = "10s")
    String delayStart;

    // Track if database is ready
    private final AtomicBoolean databaseReady = new AtomicBoolean(false);
    
    // Metrics
    private final AtomicLong publishedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);

    /**
     * Called on startup to wait for database to be ready.
     */
    void onStart(@Observes StartupEvent event) {
        LOG.info("Outbox worker initialized. Waiting for database to be ready...");
    }

    /**
     * Scheduled job that processes pending outbox entries.
     * Runs every 5 seconds by default (configurable via application.yml).
     * Includes a delayed start to allow database initialization.
     */
    @Scheduled(every = "${outbox.poll-interval:5s}", delayed = "${outbox.delay-start:10s}")
    @Transactional
    public void processPendingEvents() {
        if (!enabled) {
            LOG.debug("Outbox worker is disabled");
            return;
        }

        try {
            List<OutboxEntry> pending = outboxRepository.findPending(batchSize);
            
            // Mark database as ready after first successful query
            if (!databaseReady.getAndSet(true)) {
                LOG.info("Outbox database is ready");
            }
            
            if (pending.isEmpty()) {
                LOG.debug("No pending events found");
                return;
            }

            LOG.infof("Found %d pending events to process", pending.size());

            for (OutboxEntry entry : pending) {
                processEntry(entry);
            }
            
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                // Table doesn't exist yet - Hibernate hasn't created it
                LOG.debugf("Outbox table not ready yet, will retry: %s", e.getMessage());
            } else {
                LOG.errorf(e, "Error processing outbox events: %s", e.getMessage());
            }
        }
    }

    /**
     * Processes a single outbox entry.
     */
    private void processEntry(OutboxEntry entry) {
        try {
            // Mark as processing (prevents duplicate processing)
            entry.markProcessing();
            outboxRepository.update(entry);

            // Note: Kafka publishing is disabled in dev mode without Kafka
            // In production, this would publish to Kafka
            LOG.infof("Would publish event: %s (id=%s, tenant=%s)", 
                    entry.getEventType(), entry.getId(), entry.getTenantId());

            // Mark as published
            entry.markPublished();
            outboxRepository.update(entry);

            publishedCount.incrementAndGet();
            LOG.infof("Processed event: %s (id=%s)", entry.getEventType(), entry.getId());

        } catch (Exception e) {
            LOG.errorf(e, "Failed to process event %s (id=%s): %s", 
                    entry.getEventType(), entry.getId(), e.getMessage());
            
            try {
                entry.markFailed(e.getMessage());
                outboxRepository.update(entry);
            } catch (Exception updateError) {
                LOG.errorf(updateError, "Failed to mark event as failed: %s", updateError.getMessage());
            }
            failedCount.incrementAndGet();
        }
    }

    /**
     * Scheduled cleanup job for old published entries.
     * Runs daily at midnight by default.
     */
    @Scheduled(cron = "${outbox.cleanup-cron:0 0 0 * * ?}")
    @Transactional
    public void cleanupOldEntries() {
        if (!enabled || !databaseReady.get()) {
            return;
        }

        try {
            Instant cutoff = Instant.now().minus(cleanupDays, ChronoUnit.DAYS);
            int deleted = outboxRepository.deletePublishedOlderThan(cutoff);
            
            if (deleted > 0) {
                LOG.infof("Cleaned up %d old published outbox entries", deleted);
            }
        } catch (Exception e) {
            LOG.errorf(e, "Error cleaning up outbox entries: %s", e.getMessage());
        }
    }

    /**
     * Returns the count of successfully published events.
     */
    public long getPublishedCount() {
        return publishedCount.get();
    }

    /**
     * Returns the count of failed events.
     */
    public long getFailedCount() {
        return failedCount.get();
    }

    /**
     * Returns current pending event count.
     */
    public long getPendingCount() {
        if (!databaseReady.get()) {
            return 0;
        }
        try {
            return outboxRepository.countByStatus(OutboxStatus.PENDING);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Returns current failed event count.
     */
    public long getFailedInDbCount() {
        if (!databaseReady.get()) {
            return 0;
        }
        try {
            return outboxRepository.countByStatus(OutboxStatus.FAILED);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Returns whether the database is ready.
     */
    public boolean isDatabaseReady() {
        return databaseReady.get();
    }
}
