package com.invoicegenie.ar.domain.model.webhook;

import com.invoicegenie.shared.domain.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port: webhook delivery attempt log (STORY-009).
 */
public interface WebhookDeliveryRepository {

    void save(WebhookDeliveryLog log);

    Optional<WebhookDeliveryLog> findById(UUID id);

    List<WebhookDeliveryLog> findDueRetries(Instant now, int limit);

    List<WebhookDeliveryLog> findRecentByTenant(TenantId tenantId, int limit);
}