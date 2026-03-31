package com.invoicegenie.ar.domain.model.outbox;

import com.invoicegenie.shared.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain model for an outbox entry.
 * Represents a domain event waiting to be published to Kafka.
 * 
 * <p>This is a pure domain object with no framework dependencies.
 */
public final class OutboxEntry {

    private final UUID id;
    private final TenantId tenantId;
    private final String aggregateType;  // INVOICE, PAYMENT, CUSTOMER
    private final UUID aggregateId;      // ID of the aggregate that produced the event
    private final String eventType;      // InvoiceIssued, PaymentRecorded, etc.
    private final String payload;        // JSON representation of the event
    private final Instant createdAt;
    private Instant publishedAt;
    private OutboxStatus status;
    private int retryCount;
    private String lastError;

    /**
     * Creates a new outbox entry for an event.
     */
    public OutboxEntry(TenantId tenantId, String aggregateType, UUID aggregateId,
                       String eventType, String payload) {
        this.id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.aggregateType = Objects.requireNonNull(aggregateType, "aggregateType must not be null");
        this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
        this.payload = Objects.requireNonNull(payload, "payload must not be null");
        this.createdAt = Instant.now();
        this.status = OutboxStatus.PENDING;
        this.retryCount = 0;
    }

    /**
     * Full constructor for reconstitution from persistence.
     */
    public OutboxEntry(UUID id, TenantId tenantId, String aggregateType, UUID aggregateId,
                       String eventType, String payload, Instant createdAt, Instant publishedAt,
                       OutboxStatus status, int retryCount, String lastError) {
        this.id = Objects.requireNonNull(id);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.aggregateType = Objects.requireNonNull(aggregateType);
        this.aggregateId = Objects.requireNonNull(aggregateId);
        this.eventType = Objects.requireNonNull(eventType);
        this.payload = Objects.requireNonNull(payload);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.publishedAt = publishedAt;
        this.status = status == null ? OutboxStatus.PENDING : status;
        this.retryCount = retryCount;
        this.lastError = lastError;
    }

    // ==================== Business Methods ====================

    /**
     * Marks this entry as being processed.
     */
    public OutboxEntry markProcessing() {
        this.status = OutboxStatus.PROCESSING;
        return this;
    }

    /**
     * Marks this entry as successfully published.
     */
    public OutboxEntry markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Instant.now();
        return this;
    }

    /**
     * Marks this entry as failed with an error message.
     */
    public OutboxEntry markFailed(String error) {
        this.retryCount++;
        this.lastError = error;
        if (this.retryCount >= getMaxRetries()) {
            this.status = OutboxStatus.FAILED;
        } else {
            this.status = OutboxStatus.PENDING; // Allow retry
        }
        return this;
    }

    /**
     * Returns whether this entry can be retried.
     */
    public boolean canRetry() {
        return retryCount < getMaxRetries();
    }

    /**
     * Maximum number of retry attempts.
     */
    public static int getMaxRetries() {
        return 5;
    }

    /**
     * Returns the Kafka topic name for this event type.
     */
    public String getTopicName() {
        // Convention: ar.{aggregateType}.{eventType} in kebab-case
        String eventName = eventType
                .replaceAll("([a-z])([A-Z])", "$1-$2")
                .toLowerCase();
        return "ar." + aggregateType.toLowerCase() + "." + eventName;
    }

    // ==================== Getters ====================

    public UUID getId() {
        return id;
    }

    public TenantId getTenantId() {
        return tenantId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public String getLastError() {
        return lastError;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OutboxEntry that = (OutboxEntry) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "OutboxEntry{" +
                "id=" + id +
                ", tenantId=" + tenantId +
                ", aggregateType='" + aggregateType + '\'' +
                ", eventType='" + eventType + '\'' +
                ", status=" + status +
                ", retryCount=" + retryCount +
                '}';
    }
}
