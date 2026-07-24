package com.invoicegenie.ar.domain.model.webhook;

/**
 * Delivery attempt outcome for customer webhooks (STORY-009).
 */
public enum WebhookDeliveryStatus {
    SUCCESS,
    RETRY,
    DEAD,
    BLOCKED_SSRF
}