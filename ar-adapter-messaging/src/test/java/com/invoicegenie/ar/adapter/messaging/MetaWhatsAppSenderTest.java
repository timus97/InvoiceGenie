package com.invoicegenie.ar.adapter.messaging;

import com.invoicegenie.ar.application.port.outbound.WhatsAppSender;
import com.invoicegenie.ar.domain.model.notification.Notification;
import com.invoicegenie.ar.domain.model.notification.NotificationChannel;
import com.invoicegenie.ar.domain.model.notification.NotificationEventType;
import com.invoicegenie.ar.domain.model.notification.NotificationStatus;
import com.invoicegenie.shared.domain.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("MetaWhatsAppSender")
@ExtendWith(MockitoExtension.class)
class MetaWhatsAppSenderTest {

    @Mock MetaGraphHttpClient httpClient;

    MetaWhatsAppSender sender;
    Notification notification;

    @BeforeEach
    void setUp() {
        sender = new MetaWhatsAppSender();
        sender.accessToken = "EAAB-test-token";
        sender.phoneNumberId = "1234567890";
        sender.apiVersion = "v18.0";
        sender.languageCode = "en";
        sender.httpClientOverride = httpClient;

        UUID id = UUID.randomUUID();
        TenantId tenant = TenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        notification = new Notification(
                id, tenant, null, null,
                NotificationEventType.INVOICE_ISSUED, NotificationChannel.WHATSAPP,
                NotificationStatus.PENDING, "key-wa-1",
                "+15551234567", "Invoice", "Invoice INV-1 is due", null,
                null, null, 0, 5, Instant.now(), null, null, null,
                Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("fails closed without token")
    void failMissingToken() {
        sender.accessToken = "none";
        WhatsAppSender.SendResult r = sender.send(notification);
        assertFalse(r.success());
        assertTrue(r.errorMessage().toLowerCase().contains("not configured"));
        verifyNoInteractions(httpClient);
    }

    @Test
    @DisplayName("fails closed without phone-number-id")
    void failMissingPhoneNumberId() {
        sender.phoneNumberId = "none";
        WhatsAppSender.SendResult r = sender.send(notification);
        assertFalse(r.success());
        verifyNoInteractions(httpClient);
    }

    @Test
    @DisplayName("success path maps provider message id")
    void successPath() throws Exception {
        when(httpClient.postJson(anyString(), anyString(), anyString()))
                .thenReturn(new MetaGraphHttpClient.HttpResult(200,
                        "{\"messages\":[{\"id\":\"wamid.ABC123\"}]}"));

        WhatsAppSender.SendResult r = sender.send(notification);

        assertTrue(r.success());
        assertEquals("wamid.ABC123", r.providerMessageId());

        ArgumentCaptor<String> urlCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCap = ArgumentCaptor.forClass(String.class);
        verify(httpClient).postJson(urlCap.capture(), eq("EAAB-test-token"), bodyCap.capture());
        assertTrue(urlCap.getValue().contains("graph.facebook.com/v18.0/1234567890/messages"));
        assertTrue(bodyCap.getValue().contains("\"type\":\"template\""));
        assertTrue(bodyCap.getValue().contains("15551234567"));
    }

    @Test
    @DisplayName("HTTP error maps to failure with status")
    void httpError() throws Exception {
        when(httpClient.postJson(anyString(), anyString(), anyString()))
                .thenReturn(new MetaGraphHttpClient.HttpResult(401, "{\"error\":{\"message\":\"invalid token\"}}"));

        WhatsAppSender.SendResult r = sender.send(notification);

        assertFalse(r.success());
        assertEquals(401, r.httpStatus());
    }
}
