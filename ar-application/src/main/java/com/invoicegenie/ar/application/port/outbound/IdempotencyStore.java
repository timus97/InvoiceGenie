package com.invoicegenie.ar.application.port.outbound;

import com.invoicegenie.shared.domain.TenantId;

import java.time.Instant;
import java.util.Optional;

/**
 * Outbound port: durable idempotency key store.
 *
 * <p>Keys are scoped by tenant. Callers own request-hash and response serialization.
 */
public interface IdempotencyStore {

    /**
     * Looks up a previously stored response for the given key.
     */
    Optional<IdempotencyRecord> find(TenantId tenantId, String key);

    /**
     * Persists a response for the given key. Overwrites if the key already exists
     * with the same request hash; callers should check first for conflicts.
     */
    void put(TenantId tenantId, String key, String requestHash, String responseJson);

    /**
     * Stored idempotency record.
     */
    record IdempotencyRecord(
            String key,
            String requestHash,
            String responseJson,
            Instant createdAt
    ) {}
}
