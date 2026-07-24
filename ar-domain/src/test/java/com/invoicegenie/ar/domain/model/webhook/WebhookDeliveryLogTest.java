package com.invoicegenie.ar.domain.model.webhook;

import com.invoicegenie.shared.domain.TenantId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WebhookDeliveryLogTest {

    @Test
    void startAndMarkSuccess() {
        TenantId tid = TenantId.of(UUID.randomUUID());
        WebhookDeliveryLog log = WebhookDeliveryLog.start(tid, UUID.randomUUID(), UUID.randomUUID(),
                "InvoiceIssued", "https://hooks.example.com/x", "{\"ok\":true}");
        assertEquals(WebhookDeliveryStatus.RETRY, log.getStatus());
        log.markSuccess(200, "ok");
        assertEquals(WebhookDeliveryStatus.SUCCESS, log.getStatus());
        assertEquals(200, log.getHttpStatus());
        assertNull(log.getNextAttemptAt());
    }

    @Test
    void markBlockedSsrf() {
        TenantId tid = TenantId.of(UUID.randomUUID());
        WebhookDeliveryLog log = WebhookDeliveryLog.start(tid, UUID.randomUUID(), null,
                "PaymentRecorded", "http://127.0.0.1/x", "{}");
        log.markBlockedSsrf("SSRF blocked");
        assertEquals(WebhookDeliveryStatus.BLOCKED_SSRF, log.getStatus());
    }
}