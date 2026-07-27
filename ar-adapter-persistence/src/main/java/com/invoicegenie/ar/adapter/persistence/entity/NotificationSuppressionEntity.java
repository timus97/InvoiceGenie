package com.invoicegenie.ar.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ar_notification_suppression")
public class NotificationSuppressionEntity {

    @Id
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "channel", nullable = false, length = 32)
    private String channel;

    @Column(name = "destination_normalized", nullable = false, length = 320)
    private String destinationNormalized;

    @Column(name = "destination_hash", nullable = false, length = 64)
    private String destinationHash;

    @Column(name = "reason", length = 128)
    private String reason;

    @Column(name = "provider", length = 64)
    private String provider;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getDestinationNormalized() { return destinationNormalized; }
    public void setDestinationNormalized(String destinationNormalized) {
        this.destinationNormalized = destinationNormalized;
    }
    public String getDestinationHash() { return destinationHash; }
    public void setDestinationHash(String destinationHash) { this.destinationHash = destinationHash; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
