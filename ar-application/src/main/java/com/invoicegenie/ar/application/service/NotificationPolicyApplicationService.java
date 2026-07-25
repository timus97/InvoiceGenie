package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.NotificationPolicyUseCase;
import com.invoicegenie.ar.domain.model.notification.NotificationPolicy;
import com.invoicegenie.ar.domain.model.notification.NotificationPolicyRepository;
import com.invoicegenie.shared.domain.TenantId;

/**
 * Application service: tenant notification policy.
 */
public class NotificationPolicyApplicationService implements NotificationPolicyUseCase {

    private final NotificationPolicyRepository policyRepository;

    public NotificationPolicyApplicationService(NotificationPolicyRepository policyRepository) {
        this.policyRepository = policyRepository;
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
                command.channelsDunningNotice()
        );
        policyRepository.save(policy);
        return policy;
    }
}
