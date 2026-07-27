package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.NotificationPolicyUseCase;
import com.invoicegenie.ar.domain.model.notification.NotificationPolicy;
import com.invoicegenie.ar.domain.model.notification.NotificationPolicyRepository;
import com.invoicegenie.ar.domain.model.outbox.AuditEntry;
import com.invoicegenie.ar.domain.model.outbox.AuditRepository;
import com.invoicegenie.shared.domain.TenantId;

/**
 * Application service: tenant notification policy.
 */
public class NotificationPolicyApplicationService implements NotificationPolicyUseCase {

    private final NotificationPolicyRepository policyRepository;
    private final AuditRepository auditRepository;

    public NotificationPolicyApplicationService(NotificationPolicyRepository policyRepository) {
        this(policyRepository, null);
    }

    public NotificationPolicyApplicationService(NotificationPolicyRepository policyRepository,
                                                AuditRepository auditRepository) {
        this.policyRepository = policyRepository;
        this.auditRepository = auditRepository;
    }

    @Override
    public NotificationPolicy getOrDefault(TenantId tenantId) {
        return policyRepository.findByTenant(tenantId)
                .orElseGet(() -> {
                    NotificationPolicy p = NotificationPolicy.defaults(tenantId);
                    policyRepository.save(p);
                    return p;
                });
    }

    @Override
    public NotificationPolicy update(TenantId tenantId, UpdatePolicyCommand command) {
        NotificationPolicy policy = policyRepository.findByTenant(tenantId)
                .orElseGet(() -> NotificationPolicy.defaults(tenantId));
        String before = snapshot(policy);
        try {
            policy.update(
                    command.enabled(),
                    command.emailEnabled(),
                    command.whatsappEnabled(),
                    command.autoSendOnIssue(),
                    command.preDueReminderEnabled(),
                    command.preDueDays(),
                    command.dunningNoticeEnabled(),
                    command.channelsInvoiceIssued(),
                    command.channelsPaymentReminder(),
                    command.channelsDunningNotice(),
                    command.quietHoursStart(),
                    command.quietHoursEnd(),
                    command.timezone(),
                    command.attachPdfOnIssue()
            );
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage() != null ? e.getMessage() : "Invalid policy", e);
        }
        policyRepository.save(policy);
        if (auditRepository != null) {
            auditRepository.save(tenantId, AuditEntry.update(
                    tenantId, "NOTIFICATION_POLICY", policy.getId(), "notification-policy",
                    null, before, snapshot(policy)));
        }
        return policy;
    }

    private static String snapshot(NotificationPolicy p) {
        return "{"
                + "\"enabled\":" + p.isEnabled()
                + ",\"emailEnabled\":" + p.isEmailEnabled()
                + ",\"whatsappEnabled\":" + p.isWhatsappEnabled()
                + ",\"autoSendOnIssue\":" + p.isAutoSendOnIssue()
                + ",\"preDueReminderEnabled\":" + p.isPreDueReminderEnabled()
                + ",\"preDueDays\":" + p.getPreDueDays()
                + ",\"dunningNoticeEnabled\":" + p.isDunningNoticeEnabled()
                + ",\"quietHoursStart\":" + p.getQuietHoursStart()
                + ",\"quietHoursEnd\":" + p.getQuietHoursEnd()
                + ",\"timezone\":\"" + p.getTimezone() + "\""
                + ",\"attachPdfOnIssue\":" + p.isAttachPdfOnIssue()
                + "}";
    }
}
