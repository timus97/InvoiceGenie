package com.invoicegenie.ar.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ar_notification_policy")
public class NotificationPolicyEntity {

    @Id
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled;

    @Column(name = "whatsapp_enabled", nullable = false)
    private boolean whatsappEnabled;

    @Column(name = "auto_send_on_issue", nullable = false)
    private boolean autoSendOnIssue;

    @Column(name = "pre_due_reminder_enabled", nullable = false)
    private boolean preDueReminderEnabled;

    @Column(name = "pre_due_days", nullable = false)
    private int preDueDays;

    @Column(name = "dunning_notice_enabled", nullable = false)
    private boolean dunningNoticeEnabled;

    @Column(name = "channels_invoice_issued", nullable = false, length = 64)
    private String channelsInvoiceIssued;

    @Column(name = "channels_payment_reminder", nullable = false, length = 64)
    private String channelsPaymentReminder;

    @Column(name = "channels_dunning_notice", nullable = false, length = 64)
    private String channelsDunningNotice;

    @Column(name = "quiet_hours_start")
    private Integer quietHoursStart;

    @Column(name = "quiet_hours_end")
    private Integer quietHoursEnd;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone = "UTC";

    @Column(name = "attach_pdf_on_issue", nullable = false)
    private boolean attachPdfOnIssue;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEmailEnabled() { return emailEnabled; }
    public void setEmailEnabled(boolean emailEnabled) { this.emailEnabled = emailEnabled; }
    public boolean isWhatsappEnabled() { return whatsappEnabled; }
    public void setWhatsappEnabled(boolean whatsappEnabled) { this.whatsappEnabled = whatsappEnabled; }
    public boolean isAutoSendOnIssue() { return autoSendOnIssue; }
    public void setAutoSendOnIssue(boolean autoSendOnIssue) { this.autoSendOnIssue = autoSendOnIssue; }
    public boolean isPreDueReminderEnabled() { return preDueReminderEnabled; }
    public void setPreDueReminderEnabled(boolean preDueReminderEnabled) { this.preDueReminderEnabled = preDueReminderEnabled; }
    public int getPreDueDays() { return preDueDays; }
    public void setPreDueDays(int preDueDays) { this.preDueDays = preDueDays; }
    public boolean isDunningNoticeEnabled() { return dunningNoticeEnabled; }
    public void setDunningNoticeEnabled(boolean dunningNoticeEnabled) { this.dunningNoticeEnabled = dunningNoticeEnabled; }
    public String getChannelsInvoiceIssued() { return channelsInvoiceIssued; }
    public void setChannelsInvoiceIssued(String channelsInvoiceIssued) { this.channelsInvoiceIssued = channelsInvoiceIssued; }
    public String getChannelsPaymentReminder() { return channelsPaymentReminder; }
    public void setChannelsPaymentReminder(String channelsPaymentReminder) { this.channelsPaymentReminder = channelsPaymentReminder; }
    public String getChannelsDunningNotice() { return channelsDunningNotice; }
    public void setChannelsDunningNotice(String channelsDunningNotice) { this.channelsDunningNotice = channelsDunningNotice; }
    public Integer getQuietHoursStart() { return quietHoursStart; }
    public void setQuietHoursStart(Integer quietHoursStart) { this.quietHoursStart = quietHoursStart; }
    public Integer getQuietHoursEnd() { return quietHoursEnd; }
    public void setQuietHoursEnd(Integer quietHoursEnd) { this.quietHoursEnd = quietHoursEnd; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public boolean isAttachPdfOnIssue() { return attachPdfOnIssue; }
    public void setAttachPdfOnIssue(boolean attachPdfOnIssue) { this.attachPdfOnIssue = attachPdfOnIssue; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
