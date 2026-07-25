package com.invoicegenie.ar.domain.model.notification;

import com.invoicegenie.shared.domain.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Outbound port: notification delivery attempts.
 */
public interface NotificationAttemptRepository {

    void save(NotificationAttempt attempt);

    List<NotificationAttempt> findByNotification(TenantId tenantId, UUID notificationId);
}
