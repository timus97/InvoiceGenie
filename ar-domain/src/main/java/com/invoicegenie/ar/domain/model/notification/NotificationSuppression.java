package com.invoicegenie.ar.domain.model.notification;

import com.invoicegenie.shared.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Destination suppression entry (hard bounce / complaint) — blocks future enqueue.
 */
public final class NotificationSuppression {

    private final UUID id;
    private final TenantId tenantId;
    private final NotificationChannel channel;
    private final String destinationNormalized;
    private final String destinationHash;
    private final String reason;
    private final String provider;
    private final Instant createdAt;

    public NotificationSuppression(UUID id, TenantId tenantId, NotificationChannel channel,
                                   String destinationNormalized, String destinationHash,
                                   String reason, String provider, Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.channel = Objects.requireNonNull(channel);
        this.destinationNormalized = Objects.requireNonNull(destinationNormalized);
        this.destinationHash = Objects.requireNonNull(destinationHash);
        this.reason = reason;
        this.provider = provider;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public static NotificationSuppression create(TenantId tenantId, NotificationChannel channel,
                                                 String destinationNormalized, String destinationHash,
                                                 String reason, String provider) {
        return new NotificationSuppression(UUID.randomUUID(), tenantId, channel,
                destinationNormalized, destinationHash, reason, provider, Instant.now());
    }

    public UUID getId() { return id; }
    public TenantId getTenantId() { return tenantId; }
    public NotificationChannel getChannel() { return channel; }
    public String getDestinationNormalized() { return destinationNormalized; }
    public String getDestinationHash() { return destinationHash; }
    public String getReason() { return reason; }
    public String getProvider() { return provider; }
    public Instant getCreatedAt() { return createdAt; }
}
