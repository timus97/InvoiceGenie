package com.invoicegenie.ar.domain.model.notification;

import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.shared.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Customer notification record (queue + history).
 */
public final class Notification {

    private final UUID id;
    private final TenantId tenantId;
    private final CustomerId customerId;
    private final InvoiceId invoiceId;
    private final NotificationEventType eventType;
    private final NotificationChannel channel;
    private NotificationStatus status;
    private final String idempotencyKey;
    private String destination;
    private String subject;
    private String body;
    private UUID templateId;
    private NotificationSkipReason skipReason;
    private String errorMessage;
    private int attemptCount;
    private int maxAttempts;
    private Instant nextAttemptAt;
    private Instant sentAt;
    private String providerMessageId;
    private String metadataJson;
    private final Instant createdAt;
    private Instant updatedAt;

    public Notification(UUID id, TenantId tenantId, CustomerId customerId, InvoiceId invoiceId,
                        NotificationEventType eventType, NotificationChannel channel,
                        NotificationStatus status, String idempotencyKey, String destination,
                        String subject, String body, UUID templateId,
                        NotificationSkipReason skipReason, String errorMessage,
                        int attemptCount, int maxAttempts, Instant nextAttemptAt,
                        Instant sentAt, String providerMessageId, String metadataJson,
                        Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.customerId = customerId;
        this.invoiceId = invoiceId;
        this.eventType = Objects.requireNonNull(eventType);
        this.channel = Objects.requireNonNull(channel);
        this.status = Objects.requireNonNull(status);
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey);
        this.destination = destination;
        this.subject = subject;
        this.body = body;
        this.templateId = templateId;
        this.skipReason = skipReason;
        this.errorMessage = errorMessage;
        this.attemptCount = attemptCount;
        this.maxAttempts = maxAttempts > 0 ? maxAttempts : 5;
        this.nextAttemptAt = nextAttemptAt;
        this.sentAt = sentAt;
        this.providerMessageId = providerMessageId;
        this.metadataJson = metadataJson;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public static Notification enqueue(TenantId tenantId, CustomerId customerId, InvoiceId invoiceId,
                                       NotificationEventType eventType, NotificationChannel channel,
                                       String idempotencyKey, String destination, String subject,
                                       String body, UUID templateId, String metadataJson, int maxAttempts) {
        Instant now = Instant.now();
        return new Notification(UUID.randomUUID(), tenantId, customerId, invoiceId, eventType, channel,
                NotificationStatus.PENDING, idempotencyKey, destination, subject, body, templateId,
                null, null, 0, maxAttempts, now, null, null, metadataJson, now, now);
    }

    public static Notification skipped(TenantId tenantId, CustomerId customerId, InvoiceId invoiceId,
                                       NotificationEventType eventType, NotificationChannel channel,
                                       String idempotencyKey, NotificationSkipReason reason,
                                       String metadataJson) {
        Instant now = Instant.now();
        return new Notification(UUID.randomUUID(), tenantId, customerId, invoiceId, eventType, channel,
                NotificationStatus.SKIPPED, idempotencyKey, null, null, null, null,
                reason, null, 0, 0, null, null, null, metadataJson, now, now);
    }

    public void markSending() {
        this.status = NotificationStatus.SENDING;
        this.updatedAt = Instant.now();
    }

    public void markSent(String providerMessageId) {
        this.status = NotificationStatus.SENT;
        this.providerMessageId = providerMessageId;
        this.sentAt = Instant.now();
        this.nextAttemptAt = null;
        this.errorMessage = null;
        this.updatedAt = Instant.now();
    }

    public void markRetry(int attempt, String error, Instant nextAt) {
        this.status = NotificationStatus.QUEUED;
        this.attemptCount = attempt;
        this.errorMessage = truncate(error);
        this.nextAttemptAt = nextAt;
        this.updatedAt = Instant.now();
    }

    public void markFailed(int attempt, String error) {
        this.status = NotificationStatus.FAILED;
        this.attemptCount = attempt;
        this.errorMessage = truncate(error);
        this.nextAttemptAt = null;
        this.updatedAt = Instant.now();
    }

    public void markCancelled(String reason) {
        this.status = NotificationStatus.CANCELLED;
        this.errorMessage = truncate(reason);
        this.nextAttemptAt = null;
        this.updatedAt = Instant.now();
    }

    /**
     * Defer dispatch without consuming an attempt (e.g. quiet hours).
     * Restores QUEUED status after a claim that had set SENDING.
     */
    public void deferUntil(Instant nextAt) {
        this.status = NotificationStatus.QUEUED;
        this.nextAttemptAt = Objects.requireNonNull(nextAt);
        this.updatedAt = Instant.now();
    }

    public void appendBody(String suffix) {
        if (suffix == null || suffix.isEmpty()) {
            return;
        }
        this.body = (this.body != null ? this.body : "") + suffix;
        this.updatedAt = Instant.now();
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
        this.updatedAt = Instant.now();
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= 2000 ? s : s.substring(0, 2000);
    }

    public UUID getId() { return id; }
    public TenantId getTenantId() { return tenantId; }
    public CustomerId getCustomerId() { return customerId; }
    public InvoiceId getInvoiceId() { return invoiceId; }
    public NotificationEventType getEventType() { return eventType; }
    public NotificationChannel getChannel() { return channel; }
    public NotificationStatus getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getDestination() { return destination; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public UUID getTemplateId() { return templateId; }
    public NotificationSkipReason getSkipReason() { return skipReason; }
    public String getErrorMessage() { return errorMessage; }
    public int getAttemptCount() { return attemptCount; }
    public int getMaxAttempts() { return maxAttempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getSentAt() { return sentAt; }
    public String getProviderMessageId() { return providerMessageId; }
    public String getMetadataJson() { return metadataJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
