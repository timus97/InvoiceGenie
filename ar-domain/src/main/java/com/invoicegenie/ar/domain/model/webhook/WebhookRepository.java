package com.invoicegenie.ar.domain.model.webhook;

import com.invoicegenie.shared.domain.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port: webhook subscription persistence.
 */
public interface WebhookRepository {

    void save(TenantId tenantId, WebhookSubscription subscription);

    Optional<WebhookSubscription> findById(TenantId tenantId, UUID id);

    List<WebhookSubscription> findAllByTenant(TenantId tenantId);

    List<WebhookSubscription> findActiveByTenant(TenantId tenantId);

    void delete(TenantId tenantId, UUID id);
}
