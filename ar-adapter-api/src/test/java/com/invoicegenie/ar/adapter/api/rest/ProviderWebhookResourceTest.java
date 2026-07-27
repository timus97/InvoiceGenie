package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.application.service.NotificationSuppressionService;
import com.invoicegenie.ar.domain.model.notification.NotificationChannel;
import com.invoicegenie.ar.domain.model.notification.NotificationSuppression;
import com.invoicegenie.shared.domain.TenantId;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ProviderWebhookResource")
@ExtendWith(MockitoExtension.class)
class ProviderWebhookResourceTest {

    @Mock NotificationSuppressionService suppressionService;
    ProviderWebhookResource resource;

    @BeforeEach
    void setUp() throws Exception {
        resource = new ProviderWebhookResource(suppressionService);
        var f = ProviderWebhookResource.class.getDeclaredField("webhookSecret");
        f.setAccessible(true);
        f.set(resource, "test-secret");
    }

    @Test
    @DisplayName("rejects missing secret")
    void unauthorized() {
        Response r = resource.ingest("ses", null, null,
                "{\"tenantId\":\"00000000-0000-0000-0000-000000000001\",\"destination\":\"a@b.com\",\"type\":\"bounce\"}");
        assertEquals(401, r.getStatus());
        verifyNoInteractions(suppressionService);
    }

    @Test
    @DisplayName("records suppression for minimal ses payload")
    void suppressSes() {
        UUID id = UUID.randomUUID();
        TenantId tenant = TenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        when(suppressionService.suppress(eq(tenant), eq(NotificationChannel.EMAIL), eq("a@b.com"), anyString(), eq("ses")))
                .thenReturn(new NotificationSuppression(id, tenant, NotificationChannel.EMAIL,
                        "a@b.com", "hash", "bounce", "ses", Instant.now()));

        Response r = resource.ingest("ses", "test-secret", null,
                "{\"tenantId\":\"00000000-0000-0000-0000-000000000001\",\"destination\":\"a@b.com\",\"type\":\"bounce\",\"reason\":\"Permanent\"}");

        assertEquals(200, r.getStatus());
        verify(suppressionService).suppress(eq(tenant), eq(NotificationChannel.EMAIL), eq("a@b.com"), anyString(), eq("ses"));
    }

    @Test
    @DisplayName("meta uses WhatsApp channel")
    void suppressMeta() {
        UUID id = UUID.randomUUID();
        TenantId tenant = TenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        when(suppressionService.suppress(eq(tenant), eq(NotificationChannel.WHATSAPP), anyString(), anyString(), eq("meta")))
                .thenReturn(new NotificationSuppression(id, tenant, NotificationChannel.WHATSAPP,
                        "+15551234567", "hash", "complaint", "meta", Instant.now()));

        Response r = resource.ingest("meta", "test-secret",
                "00000000-0000-0000-0000-000000000001",
                "{\"phone\":\"+15551234567\",\"type\":\"complaint\"}");

        assertEquals(200, r.getStatus());
        verify(suppressionService).suppress(eq(tenant), eq(NotificationChannel.WHATSAPP), eq("+15551234567"), anyString(), eq("meta"));
    }
}
