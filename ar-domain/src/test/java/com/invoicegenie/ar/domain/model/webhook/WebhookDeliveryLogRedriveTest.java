package com.invoicegenie.ar.domain.model.webhook;

import com.invoicegenie.shared.domain.TenantId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WebhookDeliveryLogRedriveTest {

    @Test
    void redriveDead() {
        TenantId tid = TenantId.of(UUID.randomUUID());
        WebhookDeliveryLog log = WebhookDeliveryLog.start(tid, UUID.randomUUID(), null, "E", "https://x", "{}");
        log.markDead(5, 500, "fail");
        assertEquals(WebhookDeliveryStatus.DEAD, log.getStatus());
        log.redrive();
        assertEquals(WebhookDeliveryStatus.RETRY, log.getStatus());
        assertNotNull(log.getNextAttemptAt());
    }

    @Test
    void redriveRejectsSuccess() {
        TenantId tid = TenantId.of(UUID.randomUUID());
        WebhookDeliveryLog log = WebhookDeliveryLog.start(tid, UUID.randomUUID(), null, "E", "https://x", "{}");
        log.markSuccess(200, "ok");
        assertThrows(IllegalStateException.class, log::redrive);
    }
}
