package com.invoicegenie.ar.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Durable idempotency record: (tenant_id, key) unique.
 */
@Entity
@Table(name = "ar_idempotency")
@IdClass(IdempotencyEntity.Pk.class)
public class IdempotencyEntity {

    @Id
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Id
    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 255)
    private String key;

    @Column(name = "request_hash", nullable = false, length = 128)
    private String requestHash;

    @Column(name = "response_json", nullable = false, columnDefinition = "TEXT")
    private String responseJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public IdempotencyEntity() {}

    public IdempotencyEntity(UUID tenantId, String key, String requestHash, String responseJson, Instant createdAt) {
        this.tenantId = tenantId;
        this.key = key;
        this.requestHash = requestHash;
        this.responseJson = responseJson;
        this.createdAt = createdAt;
    }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String requestHash) { this.requestHash = requestHash; }
    public String getResponseJson() { return responseJson; }
    public void setResponseJson(String responseJson) { this.responseJson = responseJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public static final class Pk implements Serializable {
        private UUID tenantId;
        private String key;

        public Pk() {}

        public Pk(UUID tenantId, String key) {
            this.tenantId = tenantId;
            this.key = key;
        }

        public UUID getTenantId() { return tenantId; }
        public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk pk)) return false;
            return Objects.equals(tenantId, pk.tenantId) && Objects.equals(key, pk.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, key);
        }
    }
}
