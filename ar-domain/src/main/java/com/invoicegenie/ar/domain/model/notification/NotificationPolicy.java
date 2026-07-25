package com.invoicegenie.ar.domain.model.notification;

import com.invoicegenie.shared.domain.TenantId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tenant-level notification policy (channels, auto-send, pre-due days).
 */
public final class NotificationPolicy {

    private final UUID id;
    private final TenantId tenantId;
    private boolean enabled;
    private boolean emailEnabled;
    private boolean whatsappEnabled;
    private boolean autoSendOnIssue;
    private boolean preDueReminderEnabled;
    private int preDueDays;
    private boolean dunningNoticeEnabled;
    private String channelsInvoiceIssued;
    private String channelsPaymentReminder;
    private String channelsDunningNotice;
    private final Instant createdAt;
    private Instant updatedAt;

    public NotificationPolicy(UUID id, TenantId tenantId, boolean enabled, boolean emailEnabled,
                              boolean whatsappEnabled, boolean autoSendOnIssue,
                              boolean preDueReminderEnabled, int preDueDays,
                              boolean dunningNoticeEnabled,
                              String channelsInvoiceIssued, String channelsPaymentReminder,
                              String channelsDunningNotice,
                              Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.enabled = enabled;
        this.emailEnabled = emailEnabled;
        this.whatsappEnabled = whatsappEnabled;
        this.autoSendOnIssue = autoSendOnIssue;
        this.preDueReminderEnabled = preDueReminderEnabled;
        this.preDueDays = preDueDays >= 0 ? preDueDays : 3;
        this.dunningNoticeEnabled = dunningNoticeEnabled;
        this.channelsInvoiceIssued = channelsInvoiceIssued != null ? channelsInvoiceIssued : "EMAIL";
        this.channelsPaymentReminder = channelsPaymentReminder != null ? channelsPaymentReminder : "EMAIL";
        this.channelsDunningNotice = channelsDunningNotice != null ? channelsDunningNotice : "EMAIL";
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public static NotificationPolicy defaults(TenantId tenantId) {
        Instant now = Instant.now();
        return new NotificationPolicy(UUID.randomUUID(), tenantId, true, true, false,
                true, true, 3, true, "EMAIL", "EMAIL", "EMAIL", now, now);
    }

    public List<NotificationChannel> channelsFor(NotificationEventType eventType) {
        String raw = switch (eventType) {
            case INVOICE_ISSUED -> channelsInvoiceIssued;
            case PAYMENT_REMINDER -> channelsPaymentReminder;
            case DUNNING_NOTICE -> channelsDunningNotice;
        };
        return parseChannels(raw);
    }

    public boolean isEventEnabled(NotificationEventType eventType) {
        return switch (eventType) {
            case INVOICE_ISSUED -> autoSendOnIssue;
            case PAYMENT_REMINDER -> preDueReminderEnabled;
            case DUNNING_NOTICE -> dunningNoticeEnabled;
        };
    }

    public boolean isChannelGloballyEnabled(NotificationChannel channel) {
        return switch (channel) {
            case EMAIL -> emailEnabled;
            case WHATSAPP -> whatsappEnabled;
        };
    }

    public void update(boolean enabled, boolean emailEnabled, boolean whatsappEnabled,
                       boolean autoSendOnIssue, boolean preDueReminderEnabled, int preDueDays,
                       boolean dunningNoticeEnabled,
                       String channelsInvoiceIssued, String channelsPaymentReminder,
                       String channelsDunningNotice) {
        this.enabled = enabled;
        this.emailEnabled = emailEnabled;
        this.whatsappEnabled = whatsappEnabled;
        this.autoSendOnIssue = autoSendOnIssue;
        this.preDueReminderEnabled = preDueReminderEnabled;
        this.preDueDays = Math.max(0, Math.min(90, preDueDays));
        this.dunningNoticeEnabled = dunningNoticeEnabled;
        if (channelsInvoiceIssued != null) this.channelsInvoiceIssued = channelsInvoiceIssued;
        if (channelsPaymentReminder != null) this.channelsPaymentReminder = channelsPaymentReminder;
        if (channelsDunningNotice != null) this.channelsDunningNotice = channelsDunningNotice;
        this.updatedAt = Instant.now();
    }

    private static List<NotificationChannel> parseChannels(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<NotificationChannel> list = new ArrayList<>();
        for (String part : raw.split("[,|]")) {
            String t = part.trim().toUpperCase();
            if (t.isEmpty()) continue;
            try {
                list.add(NotificationChannel.valueOf(t));
            } catch (IllegalArgumentException ignored) {
                // skip unknown
            }
        }
        return list.stream().distinct().collect(Collectors.toList());
    }

    public UUID getId() { return id; }
    public TenantId getTenantId() { return tenantId; }
    public boolean isEnabled() { return enabled; }
    public boolean isEmailEnabled() { return emailEnabled; }
    public boolean isWhatsappEnabled() { return whatsappEnabled; }
    public boolean isAutoSendOnIssue() { return autoSendOnIssue; }
    public boolean isPreDueReminderEnabled() { return preDueReminderEnabled; }
    public int getPreDueDays() { return preDueDays; }
    public boolean isDunningNoticeEnabled() { return dunningNoticeEnabled; }
    public String getChannelsInvoiceIssued() { return channelsInvoiceIssued; }
    public String getChannelsPaymentReminder() { return channelsPaymentReminder; }
    public String getChannelsDunningNotice() { return channelsDunningNotice; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
