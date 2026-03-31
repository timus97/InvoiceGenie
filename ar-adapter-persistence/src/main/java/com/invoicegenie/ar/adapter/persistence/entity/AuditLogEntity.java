package com.invoicegenie.ar.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for audit log entries.
 */
@Entity
@Table(name = "ar_audit_log")
public class AuditLogEntity {

    @Id
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "entity_type", nullable = false, updatable = false)
    private String entityType;

    @Column(name = "entity_id", updatable = false)
    private UUID entityId;

    @Column(name = "entity_ref", updatable = false)
    private String entityRef;

    @Column(name = "action", nullable = false, updatable = false)
    private String action;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Column(name = "actor_type", updatable = false)
    private String actorType;

    @Column(name = "before_state", columnDefinition = "TEXT", updatable = false)
    private String beforeState;

    @Column(name = "after_state", columnDefinition = "TEXT", updatable = false)
    private String afterState;

    @Column(name = "ip_address", updatable = false)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT", updatable = false)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Default constructor for JPA
    public AuditLogEntity() {}

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public UUID getEntityId() { return entityId; }
    public void setEntityId(UUID entityId) { this.entityId = entityId; }
    public String getEntityRef() { return entityRef; }
    public void setEntityRef(String entityRef) { this.entityRef = entityRef; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public UUID getActorId() { return actorId; }
    public void setActorId(UUID actorId) { this.actorId = actorId; }
    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }
    public String getBeforeState() { return beforeState; }
    public void setBeforeState(String beforeState) { this.beforeState = beforeState; }
    public String getAfterState() { return afterState; }
    public void setAfterState(String afterState) { this.afterState = afterState; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
