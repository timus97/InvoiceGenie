package com.invoicegenie.ar.application.port.inbound;

import com.invoicegenie.ar.domain.model.webhook.WebhookSubscription;
import com.invoicegenie.shared.domain.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Inbound port: customer webhook subscriptions.
 */
public interface WebhookUseCase {

    WebhookSubscription create(TenantId tenantId, CreateWebhookCommand command);

    List<WebhookSubscription> list(TenantId tenantId);

    Optional<WebhookSubscription> get(TenantId tenantId, UUID id);

    Optional<WebhookSubscription> deactivate(TenantId tenantId, UUID id);

    Optional<WebhookSubscription> activate(TenantId tenantId, UUID id);

    boolean delete(TenantId tenantId, UUID id);

    record CreateWebhookCommand(String url, String secret, String eventTypes) {
        public CreateWebhookCommand {
            if (url == null || url.isBlank()) throw new IllegalArgumentException("url is required");
        }
    }
}