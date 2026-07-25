package com.invoicegenie.ar.domain.model.notification;

import com.invoicegenie.shared.domain.TenantId;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port: notification templates.
 */
public interface NotificationTemplateRepository {

    void save(NotificationTemplate template);

    Optional<NotificationTemplate> findById(UUID id);

    /**
     * Tenant override first, then system (tenant_id null).
     */
    Optional<NotificationTemplate> findActive(TenantId tenantId, NotificationEventType eventType,
                                              NotificationChannel channel, String locale);
}
