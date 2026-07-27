package com.invoicegenie.ar.domain.model.notification;

import com.invoicegenie.shared.domain.TenantId;

import java.util.Optional;

/**
 * Outbound port: notification destination suppressions (bounce / complaint).
 */
public interface NotificationSuppressionRepository {

    void save(NotificationSuppression suppression);

    Optional<NotificationSuppression> findByTenantChannelAndHash(TenantId tenantId,
                                                                 NotificationChannel channel,
                                                                 String destinationHash);

    boolean isSuppressed(TenantId tenantId, NotificationChannel channel, String destinationHash);
}
