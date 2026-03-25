package com.invoicegenie.ar.domain.model.outbox;

import com.invoicegenie.shared.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Audit entry for recording entity changes.
 * 
 * <p>Written to ar_audit_log table for compliance, debugging, and forensics.
 * Each mutation to domain aggregates should create an audit entry.
 */
public final class AuditEntry {

    private final UUID id;
    private final TenantId tenantId;
    private final String entityType;    // e.g., "INVOICE", "PAYMENT", "CUSTOMER"
    private final UUID entityId;
    private final String entityRef;     // Optional human-readable reference (e.g., invoice number)
    private final String action;        // "CREATE", "UPDATE", "DELETE", "TRANSITION"
    private final UUID actorId;         // User/system that performed the action
    private final String actorType;     // "USER", "SYSTEM", "API"
    private final String beforeState;   // JSONB snapshot before change (nullable)
    private final String afterState;    // JSONB snapshot after change (nullable)
    private final String ipAddress;     // Optional client IP
    private final String userAgent;     // Optional client user agent
    private final Instant createdAt;

    public AuditEntry(UUID id, TenantId tenantId, String entityType, UUID entityId,
                      String entityRef, String action, UUID actorId, String actorType,
                      String beforeState, String afterState, String ipAddress,
                      String userAgent, Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.entityType = Objects.requireNonNull(entityType);
        if (entityType.isBlank()) {
            throw new IllegalArgumentException("entityType is required");
        }
        this.entityId = entityId;
        this.entityRef = entityRef;
        this.action = Objects.requireNonNull(action);
        if (action.isBlank()) {
            throw new IllegalArgumentException("action is required");
        }
        this.actorId = actorId;
        this.actorType = actorType;
        this.beforeState = beforeState;
        this.afterState = afterState;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    // Factory methods for common actions
    public static AuditEntry create(TenantId tenantId, String entityType, UUID entityId,
                                    String entityRef, UUID actorId, String afterState) {
        return new AuditEntry(UUID.randomUUID(), tenantId, entityType, entityId,
                entityRef, "CREATE", actorId, "USER", null, afterState, null, null, null);
    }

    public static AuditEntry update(TenantId tenantId, String entityType, UUID entityId,
                                    String entityRef, UUID actorId, String beforeState, String afterState) {
        return new AuditEntry(UUID.randomUUID(), tenantId, entityType, entityId,
                entityRef, "UPDATE", actorId, "USER", beforeState, afterState, null, null, null);
    }

    public static AuditEntry delete(TenantId tenantId, String entityType, UUID entityId,
                                    String entityRef, UUID actorId, String beforeState) {
        return new AuditEntry(UUID.randomUUID(), tenantId, entityType, entityId,
                entityRef, "DELETE", actorId, "USER", beforeState, null, null, null, null);
    }

    public static AuditEntry transition(TenantId tenantId, String entityType, UUID entityId,
                                        String entityRef, UUID actorId, String action,
                                        String beforeState, String afterState) {
        return new AuditEntry(UUID.randomUUID(), tenantId, entityType, entityId,
                entityRef, action, actorId, "USER", beforeState, afterState, null, null, null);
    }

    // Accessors
    public UUID getId() { return id; }
    public TenantId getTenantId() { return tenantId; }
    public String getEntityType() { return entityType; }
    public UUID getEntityId() { return entityId; }
    public String getEntityRef() { return entityRef; }
    public String getAction() { return action; }
    public UUID getActorId() { return actorId; }
    public String getActorType() { return actorType; }
    public String getBeforeState() { return beforeState; }
    public String getAfterState() { return afterState; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuditEntry that = (AuditEntry) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "AuditEntry{" +
                "id=" + id +
                ", tenantId=" + tenantId +
                ", entityType='" + entityType + '\'' +
                ", entityId=" + entityId +
                ", action='" + action + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
