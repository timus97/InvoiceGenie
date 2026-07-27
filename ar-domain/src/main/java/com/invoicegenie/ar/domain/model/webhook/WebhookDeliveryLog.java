package com.invoicegenie.ar.domain.model.webhook;

import com.invoicegenie.shared.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Support-facing delivery attempt log for HTTP webhooks (STORY-009).
 */
public final class WebhookDeliveryLog {

    private final UUID id;
    private final TenantId tenantId;
    private final UUID subscriptionId;
    private final UUID outboxId;
    private final String eventType;
    private final String url;
    private final String payload;
    private WebhookDeliveryStatus status;
    private int attemptCount;
    private Integer httpStatus;
    private String responseSnippet;
    private String errorMessage;
    private Instant nextAttemptAt;
    private final Instant createdAt;
    private Instant updatedAt;

    public WebhookDeliveryLog(UUID id, TenantId tenantId, UUID subscriptionId, UUID outboxId,
                              String eventType, String url, String payload, WebhookDeliveryStatus status,
                              int attemptCount, Integer httpStatus, String responseSnippet,
                              String errorMessage, Instant nextAttemptAt,
                              Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.subscriptionId = Objects.requireNonNull(subscriptionId);
        this.outboxId = outboxId;
        this.eventType = Objects.requireNonNull(eventType);
        this.url = Objects.requireNonNull(url);
        this.payload = payload != null ? payload : "{}";
        this.status = Objects.requireNonNull(status);
        this.attemptCount = attemptCount;
        this.httpStatus = httpStatus;
        this.responseSnippet = responseSnippet;
        this.errorMessage = errorMessage;
        this.nextAttemptAt = nextAttemptAt;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public static WebhookDeliveryLog start(TenantId tenantId, UUID subscriptionId, UUID outboxId,
                                           String eventType, String url, String payload) {
        Instant now = Instant.now();
        return new WebhookDeliveryLog(UUID.randomUUID(), tenantId, subscriptionId, outboxId,
                eventType, url, payload, WebhookDeliveryStatus.RETRY, 0, null, null, null, now, now, now);
    }

    public void markSuccess(int httpStatus, String snippet) {
        this.status = WebhookDeliveryStatus.SUCCESS;
        this.httpStatus = httpStatus;
        this.responseSnippet = truncate(snippet);
        this.errorMessage = null;
        this.nextAttemptAt = null;
        this.updatedAt = Instant.now();
    }

    public void markRetry(int attempt, Integer httpStatus, String error, Instant nextAt) {
        this.status = WebhookDeliveryStatus.RETRY;
        this.attemptCount = attempt;
        this.httpStatus = httpStatus;
        this.errorMessage = truncate(error);
        this.nextAttemptAt = nextAt;
        this.updatedAt = Instant.now();
    }

    public void markDead(int attempt, Integer httpStatus, String error) {
        this.status = WebhookDeliveryStatus.DEAD;
        this.attemptCount = attempt;
        this.httpStatus = httpStatus;
        this.errorMessage = truncate(error);
        this.nextAttemptAt = null;
        this.updatedAt = Instant.now();
    }

    public void markBlockedSsrf(String reason) {
        this.status = WebhookDeliveryStatus.BLOCKED_SSRF;
        this.attemptCount = Math.max(this.attemptCount, 1);
        this.errorMessage = truncate(reason);
        this.nextAttemptAt = null;
        this.updatedAt = Instant.now();
    }

    /**
     * Re-queue a DEAD or RETRY delivery for another attempt (PP-026).
     * Resets status to RETRY and schedules next attempt immediately.
     *
     * @throws IllegalStateException if delivery is SUCCESS or BLOCKED_SSRF
     */
    public void redrive() {
        if (status == WebhookDeliveryStatus.SUCCESS) {
            throw new IllegalStateException("Cannot redrive a SUCCESS delivery");
        }
        if (status == WebhookDeliveryStatus.BLOCKED_SSRF) {
            throw new IllegalStateException("Cannot redrive a BLOCKED_SSRF delivery; fix the URL first");
        }
        if (status != WebhookDeliveryStatus.DEAD && status != WebhookDeliveryStatus.RETRY) {
            throw new IllegalStateException("Cannot redrive delivery in status " + status);
        }
        this.status = WebhookDeliveryStatus.RETRY;
        this.nextAttemptAt = Instant.now();
        this.errorMessage = truncate("Redriven at " + this.nextAttemptAt);
        this.updatedAt = Instant.now();
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= 2000 ? s : s.substring(0, 2000);
    }

    public UUID getId() { return id; }
    public TenantId getTenantId() { return tenantId; }
    public UUID getSubscriptionId() { return subscriptionId; }
    public UUID getOutboxId() { return outboxId; }
    public String getEventType() { return eventType; }
    public String getUrl() { return url; }
    public String getPayload() { return payload; }
    public WebhookDeliveryStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Integer getHttpStatus() { return httpStatus; }
    public String getResponseSnippet() { return responseSnippet; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}