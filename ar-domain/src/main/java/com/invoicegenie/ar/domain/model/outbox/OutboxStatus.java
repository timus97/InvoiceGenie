package com.invoicegenie.ar.domain.model.outbox;

/**
 * Status of an outbox entry in its lifecycle.
 */
public enum OutboxStatus {

    /**
     * Entry is waiting to be picked up by the worker.
     */
    PENDING,

    /**
     * Entry is currently being processed by the worker.
     * Used for optimistic locking / preventing duplicate processing.
     */
    PROCESSING,

    /**
     * Entry has been successfully published to Kafka.
     */
    PUBLISHED,

    /**
     * Entry failed after maximum retries.
     * Requires manual intervention or dead-letter handling.
     */
    FAILED
}
