package com.invoicegenie.ar.adapter.messaging;

import com.invoicegenie.ar.application.port.outbound.EmailSender;
import com.invoicegenie.ar.domain.model.notification.Notification;
import com.invoicegenie.ar.domain.model.notification.NotificationChannel;
import com.invoicegenie.ar.domain.model.notification.NotificationEventType;
import com.invoicegenie.ar.domain.model.notification.NotificationStatus;
import com.invoicegenie.shared.domain.TenantId;
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

@DisplayName("SmtpEmailSender")
@ExtendWith(MockitoExtension.class)
class SmtpEmailSenderTest {

    @Mock SmtpMailTransport transport;

    SmtpEmailSender sender;
    Notification notification;

    @BeforeEach
    void setUp() {
        sender = new SmtpEmailSender();
        sender.host = "smtp.example.com";
        sender.port = 587;
        sender.username = "user@example.com";
        sender.password = "secret";
        sender.from = "noreply@invoicegenie.local";
        sender.transportOverride = transport;

        UUID id = UUID.randomUUID();
        TenantId tenant = TenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        notification = new Notification(
                id, tenant, null, null,
                NotificationEventType.INVOICE_ISSUED, NotificationChannel.EMAIL,
                NotificationStatus.PENDING, "key-1",
                "billing@acme.test", "Subject", "Body text", null,
                null, null, 0, 5, Instant.now(), null, null, null,
                Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("fails closed when host is none")
    void failMissingHost() {
        sender.host = "none";
        EmailSender.SendResult r = sender.send(notification);
        assertFalse(r.success());
        assertTrue(r.errorMessage().contains("host"));
        verifyNoInteractions(transport);
    }

    @Test
    @DisplayName("fails closed when credentials missing")
    void failMissingCredentials() {
        sender.username = "none";
        sender.password = "none";
        EmailSender.SendResult r = sender.send(notification);
        assertFalse(r.success());
        assertTrue(r.errorMessage().toLowerCase().contains("credential"));
        verifyNoInteractions(transport);
    }

    @Test
    @DisplayName("success after transport accepts")
    void successPath() throws Exception {
        when(transport.send(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("<msg-123@smtp.example.com>");

        EmailSender.SendResult r = sender.send(notification);

        assertTrue(r.success());
        assertEquals("<msg-123@smtp.example.com>", r.providerMessageId());
        verify(transport).send(
                eq("noreply@invoicegenie.local"),
                eq("billing@acme.test"),
                eq("Subject"),
                eq("Body text"));
    }

    @Test
    @DisplayName("maps transport exception to failure")
    void transportFailure() throws Exception {
        when(transport.send(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("connection refused"));

        EmailSender.SendResult r = sender.send(notification);

        assertFalse(r.success());
        assertTrue(r.errorMessage().contains("connection refused"));
    }
}
