package com.invoicegenie.ar.domain.model.notification;

import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.shared.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Per-customer channel preference / opt-out.
 */
public final class NotificationPreference {

    private final UUID id;
    private final TenantId tenantId;
    private final CustomerId customerId;
    private final NotificationChannel channel;
    private boolean enabled;
    private Instant optedOutAt;
    private String destinationOverride;
    private final Instant createdAt;
    private Instant updatedAt;

    public NotificationPreference(UUID id, TenantId tenantId, CustomerId customerId,
                                  NotificationChannel channel, boolean enabled, Instant optedOutAt,
                                  String destinationOverride, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.customerId = Objects.requireNonNull(customerId);
        this.channel = Objects.requireNonNull(channel);
        this.enabled = enabled;
        this.optedOutAt = optedOutAt;
        this.destinationOverride = destinationOverride;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public static NotificationPreference create(TenantId tenantId, CustomerId customerId,
                                                NotificationChannel channel, boolean enabled,
                                                String destinationOverride) {
        Instant now = Instant.now();
        return new NotificationPreference(UUID.randomUUID(), tenantId, customerId, channel,
                enabled, enabled ? null : now, destinationOverride, now, now);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.optedOutAt = enabled ? null : Instant.now();
        this.updatedAt = Instant.now();
    }

    public void setDestinationOverride(String destinationOverride) {
        this.destinationOverride = destinationOverride;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public TenantId getTenantId() { return tenantId; }
    public CustomerId getCustomerId() { return customerId; }
    public NotificationChannel getChannel() { return channel; }
    public boolean isEnabled() { return enabled; }
    public Instant getOptedOutAt() { return optedOutAt; }
    public String getDestinationOverride() { return destinationOverride; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
