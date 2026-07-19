package com.invoicegenie.ar.adapter.messaging;

import com.invoicegenie.ar.domain.model.outbox.OutboxEntry;
import com.invoicegenie.ar.domain.model.outbox.OutboxRepository;
import com.invoicegenie.ar.domain.model.outbox.OutboxStatus;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
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
 * when {@code outbox.kafka-enabled=true} and an {@link OutboxKafkaSender} bean is present.
 * Otherwise events are logged (safe default without Kafka).
 *
 * <p>Features:
 * <ul>
 *   <li>Configurable batch size and polling interval</li>
 *   <li>Optional Kafka emit gated by {@code outbox.kafka-enabled} (default false)</li>
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

    /**
     * Optional Kafka sender. Unsatisfied when no production Kafka bean is wired.
     */
    @Inject
    Instance<OutboxKafkaSender> kafkaSender;

    @ConfigProperty(name = "outbox.batch-size", defaultValue = "100")
    int batchSize;

    @ConfigProperty(name = "outbox.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "outbox.kafka-enabled", defaultValue = "false")
    boolean kafkaEnabled;

    @ConfigProperty(name = "outbox.cleanup-days", defaultValue = "7")
    int cleanupDays;

    @ConfigProperty(name = "outbox.delay-start", defaultValue = "10s")
    String delayStart;

    private final AtomicBoolean databaseReady = new AtomicBoolean(false);
    private final AtomicLong publishedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);

    void onStart(@Observes StartupEvent event) {
        LOG.infof("Outbox worker initialized (kafka-enabled=%s). Waiting for database to be ready...",
                kafkaEnabled);
    }

    @Scheduled(every = "${outbox.poll-interval:5s}", delayed = "${outbox.delay-start:10s}")
    @Transactional
    public void processPendingEvents() {
        if (!enabled) {
            LOG.debug("Outbox worker is disabled");
            return;
        }

        try {
            List<OutboxEntry> pending = outboxRepository.findPending(batchSize);

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
                LOG.debugf("Outbox table not ready yet, will retry: %s", e.getMessage());
            } else {
                LOG.errorf(e, "Error processing outbox events: %s", e.getMessage());
            }
        }
    }

    private void processEntry(OutboxEntry entry) {
        try {
            entry.markProcessing();
            outboxRepository.update(entry);

            publishEvent(entry);

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
     * Publishes an outbox entry: uses Kafka sender when enabled and present, else logs.
     */
    void publishEvent(OutboxEntry entry) {
        if (kafkaEnabled && kafkaSender != null && !kafkaSender.isUnsatisfied()) {
            kafkaSender.get().send(entry);
            LOG.debugf("Published event to Kafka: type=%s id=%s tenant=%s",
                    entry.getEventType(), entry.getId(), entry.getTenantId());
            return;
        }

        if (kafkaEnabled) {
            LOG.warnf("outbox.kafka-enabled=true but no OutboxKafkaSender bean present; logging event: %s (id=%s, tenant=%s)",
                    entry.getEventType(), entry.getId(), entry.getTenantId());
        } else {
            LOG.infof("Would publish event (kafka disabled): %s (id=%s, tenant=%s)",
                    entry.getEventType(), entry.getId(), entry.getTenantId());
        }
    }

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

    public long getPublishedCount() {
        return publishedCount.get();
    }

    public long getFailedCount() {
        return failedCount.get();
    }

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

    public boolean isDatabaseReady() {
        return databaseReady.get();
    }

    public boolean isKafkaEnabled() {
        return kafkaEnabled;
    }
}