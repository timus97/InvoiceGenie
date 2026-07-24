package com.invoicegenie.ar.domain.model.webhook;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Customer-facing webhook subscription for domain events.
 */
public final class WebhookSubscription {

    private final UUID id;
    private final String url;
    private final String secret;
    private final String eventTypes; // comma-separated or "*"
    private boolean active;
    private final Instant createdAt;
    private Instant updatedAt;

    public WebhookSubscription(UUID id, String url, String secret, String eventTypes, boolean active,
                               Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url is required");
        }
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            throw new IllegalArgumentException("url must be http(s)");
        }
        this.url = url.trim();
        this.secret = secret == null ? "" : secret;
        this.eventTypes = eventTypes == null || eventTypes.isBlank() ? "*" : eventTypes.trim();
        this.active = active;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public static WebhookSubscription create(String url, String secret, String eventTypes) {
        Instant now = Instant.now();
        return new WebhookSubscription(UUID.randomUUID(), url, secret, eventTypes, true, now, now);
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public void activate() {
        this.active = true;
        this.updatedAt = Instant.now();
    }

    public boolean matches(String eventType) {
        if ("*".equals(eventTypes)) {
            return true;
        }
        for (String t : eventTypes.split(",")) {
            if (t.trim().equalsIgnoreCase(eventType)) {
                return true;
            }
        }
        return false;
    }

    public UUID getId() { return id; }
    public String getUrl() { return url; }
    public String getSecret() { return secret; }
    public String getEventTypes() { return eventTypes; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
