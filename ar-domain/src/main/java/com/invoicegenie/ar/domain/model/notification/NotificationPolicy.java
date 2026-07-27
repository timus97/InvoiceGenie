package com.invoicegenie.ar.domain.model.notification;

import com.invoicegenie.shared.domain.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tenant-level notification policy (channels, auto-send, pre-due days, quiet hours).
 *
 * <p>Quiet hours are minutes-from-midnight (0–1439) in the policy timezone (default UTC).
 * When start &gt; end, the window spans midnight. Null start or end disables quiet hours.
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
    /** Minutes from midnight 0–1439; null = quiet hours disabled. */
    private Integer quietHoursStart;
    /** Minutes from midnight 0–1439; null = quiet hours disabled. */
    private Integer quietHoursEnd;
    /** IANA timezone id for quiet-hours evaluation; default UTC. */
    private String timezone;
    /** When true, attach invoice PDF on INVOICE_ISSUED email enqueue. */
    private boolean attachPdfOnIssue;
    private final Instant createdAt;
    private Instant updatedAt;

    public NotificationPolicy(UUID id, TenantId tenantId, boolean enabled, boolean emailEnabled,
                              boolean whatsappEnabled, boolean autoSendOnIssue,
                              boolean preDueReminderEnabled, int preDueDays,
                              boolean dunningNoticeEnabled,
                              String channelsInvoiceIssued, String channelsPaymentReminder,
                              String channelsDunningNotice,
                              Integer quietHoursStart, Integer quietHoursEnd, String timezone,
                              boolean attachPdfOnIssue,
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
        this.quietHoursStart = normalizeMinute(quietHoursStart);
        this.quietHoursEnd = normalizeMinute(quietHoursEnd);
        this.timezone = (timezone == null || timezone.isBlank()) ? "UTC" : timezone.trim();
        this.attachPdfOnIssue = attachPdfOnIssue;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public static NotificationPolicy defaults(TenantId tenantId) {
        Instant now = Instant.now();
        return new NotificationPolicy(UUID.randomUUID(), tenantId, true, true, false,
                true, true, 3, true, "EMAIL", "EMAIL", "EMAIL",
                null, null, "UTC", false, now, now);
    }

    public List<NotificationChannel> channelsFor(NotificationEventType eventType) {
        String raw = switch (eventType) {
            case INVOICE_ISSUED -> channelsInvoiceIssued;
            case PAYMENT_REMINDER -> channelsPaymentReminder;
            case DUNNING_NOTICE -> channelsDunningNotice;
            case STATEMENT_SEND -> "EMAIL";
        };
        return parseChannels(raw);
    }

    public boolean isEventEnabled(NotificationEventType eventType) {
        return switch (eventType) {
            case INVOICE_ISSUED -> autoSendOnIssue;
            case PAYMENT_REMINDER -> preDueReminderEnabled;
            case DUNNING_NOTICE -> dunningNoticeEnabled;
            case STATEMENT_SEND -> true; // manual workflow only
        };
    }

    public boolean isChannelGloballyEnabled(NotificationChannel channel) {
        return switch (channel) {
            case EMAIL -> emailEnabled;
            case WHATSAPP -> whatsappEnabled;
        };
    }

    /**
     * True when quiet hours are configured and {@code now} falls inside the quiet window
     * in the policy timezone.
     */
    public boolean isInQuietHours(Instant now) {
        if (quietHoursStart == null || quietHoursEnd == null) {
            return false;
        }
        if (quietHoursStart.equals(quietHoursEnd)) {
            return false;
        }
        int minutes = minutesOfDay(now);
        int start = quietHoursStart;
        int end = quietHoursEnd;
        if (start < end) {
            return minutes >= start && minutes < end;
        }
        // spans midnight: e.g. 22:00–06:00
        return minutes >= start || minutes < end;
    }

    /**
     * Next Instant when quiet hours end (for rescheduling). If not currently in quiet hours,
     * returns {@code now}.
     */
    public Instant nextQuietHoursEnd(Instant now) {
        if (!isInQuietHours(now)) {
            return now;
        }
        ZoneId zone = resolveZone();
        ZonedDateTime zdt = now.atZone(zone);
        LocalDate date = zdt.toLocalDate();
        int end = quietHoursEnd;
        LocalTime endTime = LocalTime.of(end / 60, end % 60);
        ZonedDateTime candidate = ZonedDateTime.of(date, endTime, zone);
        // When window spans midnight and we are still after start (evening), end is tomorrow
        if (quietHoursStart > quietHoursEnd && minutesOfDay(now) >= quietHoursStart) {
            candidate = candidate.plusDays(1);
        } else if (quietHoursStart < quietHoursEnd && !candidate.isAfter(zdt)) {
            // safety: if clock is past end somehow, next day
            candidate = candidate.plusDays(1);
        }
        // If end is at midnight (0) and we're in post-start span, end is tomorrow midnight
        if (!candidate.isAfter(zdt)) {
            candidate = candidate.plusDays(1);
        }
        return candidate.toInstant();
    }

    public void update(boolean enabled, boolean emailEnabled, boolean whatsappEnabled,
                       boolean autoSendOnIssue, boolean preDueReminderEnabled, int preDueDays,
                       boolean dunningNoticeEnabled,
                       String channelsInvoiceIssued, String channelsPaymentReminder,
                       String channelsDunningNotice,
                       Integer quietHoursStart, Integer quietHoursEnd, String timezone,
                       Boolean attachPdfOnIssue) {
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
        this.quietHoursStart = normalizeMinute(quietHoursStart);
        this.quietHoursEnd = normalizeMinute(quietHoursEnd);
        if (timezone != null && !timezone.isBlank()) {
            // validate zone id
            ZoneId.of(timezone.trim());
            this.timezone = timezone.trim();
        }
        if (attachPdfOnIssue != null) {
            this.attachPdfOnIssue = attachPdfOnIssue;
        }
        this.updatedAt = Instant.now();
    }

    private int minutesOfDay(Instant now) {
        LocalTime t = LocalTime.ofInstant(now, resolveZone());
        return t.getHour() * 60 + t.getMinute();
    }

    private ZoneId resolveZone() {
        try {
            return ZoneId.of(timezone != null ? timezone : "UTC");
        } catch (Exception e) {
            return ZoneId.of("UTC");
        }
    }

    private static Integer normalizeMinute(Integer m) {
        if (m == null) {
            return null;
        }
        if (m < 0 || m > 1439) {
            throw new IllegalArgumentException("quiet hours minute must be 0–1439");
        }
        return m;
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
    public Integer getQuietHoursStart() { return quietHoursStart; }
    public Integer getQuietHoursEnd() { return quietHoursEnd; }
    public String getTimezone() { return timezone; }
    public boolean isAttachPdfOnIssue() { return attachPdfOnIssue; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
