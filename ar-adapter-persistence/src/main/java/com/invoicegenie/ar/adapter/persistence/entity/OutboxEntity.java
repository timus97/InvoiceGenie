package com.invoicegenie.ar.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the transactional outbox pattern.
 * Stores domain events to be published to Kafka by the OutboxWorker.
 * 
 * <p>This table enables reliable event publishing by storing events
 * in the same database transaction as the aggregate changes.
 * A separate worker then polls this table and publishes to Kafka.
 */
@Entity
@Table(name = "ar_outbox")
public class OutboxEntity {

    @Id
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "aggregate_type", nullable = false, updatable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error")
    private String lastError;

    /**
     * Status of an outbox entry.
     */
    public enum OutboxStatus {
        PENDING,      // Ready to be published
        PROCESSING,   // Currently being processed by worker
        PUBLISHED,    // Successfully published to Kafka
        FAILED        // Failed after max retries
    }

    // Default constructor for JPA
    public OutboxEntity() {
        this.createdAt = Instant.now();
        this.status = OutboxStatus.PENDING;
        this.retryCount = 0;
    }

    /**
     * Creates a new outbox entry for an event.
     */
    public OutboxEntity(UUID id, UUID tenantId, String aggregateType, 
                        UUID aggregateId, String eventType, String payload) {
        this();
        this.id = id;
        this.tenantId = tenantId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
    }

    // ==================== Business Methods ====================

    /**
     * Marks this entry as being processed.
     */
    public void markProcessing() {
        this.status = OutboxStatus.PROCESSING;
    }

    /**
     * Marks this entry as successfully published.
     */
    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }

    /**
     * Marks this entry as failed with an error message.
     */
    public void markFailed(String error) {
        this.retryCount++;
        this.lastError = error;
        if (this.retryCount >= getMaxRetries()) {
            this.status = OutboxStatus.FAILED;
        } else {
            this.status = OutboxStatus.PENDING; // Allow retry
        }
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

    // ==================== Getters and Setters ====================

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(UUID aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public void setStatus(OutboxStatus status) {
        this.status = status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}
