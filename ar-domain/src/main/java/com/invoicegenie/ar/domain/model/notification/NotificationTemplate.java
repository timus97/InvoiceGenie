package com.invoicegenie.ar.domain.model.notification;

import com.invoicegenie.shared.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Message template for a notification event + channel.
 * tenant_id null means system default.
 */
public final class NotificationTemplate {

    private final UUID id;
    private final TenantId tenantId; // null = system
    private final NotificationEventType eventType;
    private final NotificationChannel channel;
    private final String locale;
    private String subject;
    private String body;
    private String whatsappTemplateName;
    private boolean active;
    private final Instant createdAt;
    private Instant updatedAt;

    public NotificationTemplate(UUID id, TenantId tenantId, NotificationEventType eventType,
                                NotificationChannel channel, String locale, String subject, String body,
                                String whatsappTemplateName, boolean active,
                                Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.tenantId = tenantId;
        this.eventType = Objects.requireNonNull(eventType);
        this.channel = Objects.requireNonNull(channel);
        this.locale = locale == null || locale.isBlank() ? "en" : locale;
        this.subject = subject;
        this.body = Objects.requireNonNull(body);
        this.whatsappTemplateName = whatsappTemplateName;
        this.active = active;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public UUID getId() { return id; }
    public TenantId getTenantId() { return tenantId; }
    public NotificationEventType getEventType() { return eventType; }
    public NotificationChannel getChannel() { return channel; }
    public String getLocale() { return locale; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public String getWhatsappTemplateName() { return whatsappTemplateName; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateContent(String subject, String body, String whatsappTemplateName) {
        this.subject = subject;
        if (body != null && !body.isBlank()) {
            this.body = body;
        }
        this.whatsappTemplateName = whatsappTemplateName;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }
}
