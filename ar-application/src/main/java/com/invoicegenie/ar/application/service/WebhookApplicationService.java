package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.WebhookUseCase;
import com.invoicegenie.ar.domain.model.webhook.WebhookRepository;
import com.invoicegenie.ar.domain.model.webhook.WebhookSubscription;
import com.invoicegenie.shared.domain.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service: webhook subscription management.
 */
public class WebhookApplicationService implements WebhookUseCase {

    private final WebhookRepository webhookRepository;

    public WebhookApplicationService(WebhookRepository webhookRepository) {
        this.webhookRepository = webhookRepository;
    }

    @Override
    public WebhookSubscription create(TenantId tenantId, CreateWebhookCommand command) {
        WebhookSubscription sub = WebhookSubscription.create(
                command.url(), command.secret(), command.eventTypes());
        webhookRepository.save(tenantId, sub);
        return sub;
    }

    @Override
    public List<WebhookSubscription> list(TenantId tenantId) {
        return webhookRepository.findAllByTenant(tenantId);
    }

    @Override
    public Optional<WebhookSubscription> get(TenantId tenantId, UUID id) {
        return webhookRepository.findById(tenantId, id);
    }

    @Override
    public Optional<WebhookSubscription> deactivate(TenantId tenantId, UUID id) {
        return webhookRepository.findById(tenantId, id).map(s -> {
            s.deactivate();
            webhookRepository.save(tenantId, s);
            return s;
        });
    }

    @Override
    public Optional<WebhookSubscription> activate(TenantId tenantId, UUID id) {
        return webhookRepository.findById(tenantId, id).map(s -> {
            s.activate();
            webhookRepository.save(tenantId, s);
            return s;
        });
    }

    @Override
    public boolean delete(TenantId tenantId, UUID id) {
        if (webhookRepository.findById(tenantId, id).isEmpty()) {
            return false;
        }
        webhookRepository.delete(tenantId, id);
        return true;
    }
}