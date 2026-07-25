package com.invoicegenie.ar.application.port.inbound;

import com.invoicegenie.ar.domain.model.notification.NotificationPolicy;
import com.invoicegenie.shared.domain.TenantId;

/**
 * Inbound port: tenant notification policy.
 */
public interface NotificationPolicyUseCase {

    NotificationPolicy getOrDefault(TenantId tenantId);

    NotificationPolicy update(TenantId tenantId, UpdatePolicyCommand command);

    record UpdatePolicyCommand(
            boolean enabled,
            boolean emailEnabled,
            boolean whatsappEnabled,
            boolean autoSendOnIssue,
            boolean preDueReminderEnabled,
            int preDueDays,
            boolean dunningNoticeEnabled,
            String channelsInvoiceIssued,
            String channelsPaymentReminder,
            String channelsDunningNotice
    ) {}
}
