package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.domain.model.webhook.WebhookDeliveryLog;
import com.invoicegenie.ar.domain.model.webhook.WebhookDeliveryRepository;
import com.invoicegenie.ar.domain.model.webhook.WebhookDeliveryStatus;
import com.invoicegenie.shared.domain.TenantId;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service: re-queue DEAD/RETRY webhook deliveries (PP-026).
 */
public class WebhookRedriveService {

    private final WebhookDeliveryRepository deliveryRepository;

    public WebhookRedriveService(WebhookDeliveryRepository deliveryRepository) {
        this.deliveryRepository = Objects.requireNonNull(deliveryRepository);
    }

    /**
     * Re-queues a delivery for the given tenant.
     *
     * @return empty if not found or tenant mismatch
     * @throws IllegalStateException if status cannot be redriven
     * @throws IllegalArgumentException if delivery is SUCCESS/BLOCKED (also IllegalStateException from domain)
     */
    public Optional<WebhookDeliveryLog> redrive(TenantId tenantId, UUID deliveryId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(deliveryId, "deliveryId");
        Optional<WebhookDeliveryLog> found = deliveryRepository.findById(deliveryId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        WebhookDeliveryLog log = found.get();
        if (!log.getTenantId().equals(tenantId)) {
            return Optional.empty();
        }
        WebhookDeliveryStatus before = log.getStatus();
        if (before != WebhookDeliveryStatus.DEAD && before != WebhookDeliveryStatus.RETRY) {
            throw new IllegalStateException(
                    "Only DEAD or RETRY deliveries can be redriven (status=" + before + ")");
        }
        log.redrive();
        deliveryRepository.save(log);
        return Optional.of(log);
    }
}
