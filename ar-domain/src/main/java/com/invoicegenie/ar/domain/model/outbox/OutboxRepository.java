package com.invoicegenie.ar.domain.model.outbox;

import com.invoicegenie.shared.domain.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port (outbound): persistence for the transactional outbox.
 * 
 * <p>The outbox stores domain events that need to be published to Kafka.
 * Events are saved in the same transaction as aggregate changes,
 * ensuring reliable delivery.
 * 
 * <p>This interface is part of the domain layer and defines what
 * the application needs from the persistence layer.
 */
public interface OutboxRepository {

    /**
     * Saves a new outbox entry.
     * Called by the application service in the same transaction as the aggregate.
     */
    void save(TenantId tenantId, OutboxEntry entry);

    /**
     * Finds pending entries ready to be published.
     * Called by the OutboxWorker.
     * 
     * @param limit maximum number of entries to return
     * @return list of pending entries, ordered by creation time
     */
    List<OutboxEntry> findPending(int limit);

    /**
     * Finds a specific entry by ID.
     */
    Optional<OutboxEntry> findById(UUID id);

    /**
     * Updates an existing entry (e.g., mark as published or failed).
     */
    void update(OutboxEntry entry);

    /**
     * Deletes old published entries (cleanup job).
     * 
     * @param olderThan entries published before this timestamp
     * @return number of entries deleted
     */
    int deletePublishedOlderThan(java.time.Instant olderThan);

    /**
     * Counts entries by status.
     */
    long countByStatus(OutboxStatus status);
}
