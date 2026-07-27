package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.NotificationUseCase;
import com.invoicegenie.ar.domain.model.notification.NotificationAttemptRepository;
import com.invoicegenie.ar.domain.model.notification.NotificationChannel;
import com.invoicegenie.ar.domain.model.notification.NotificationEventType;
import com.invoicegenie.ar.domain.model.notification.NotificationRepository;
import com.invoicegenie.ar.domain.model.notification.NotificationTemplate;
import com.invoicegenie.ar.domain.model.notification.NotificationTemplateRepository;
import com.invoicegenie.shared.domain.TenantId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@DisplayName("Notification template preview (PP-014)")
@ExtendWith(MockitoExtension.class)
class NotificationPreviewTest {

    @Mock NotificationRepository notificationRepository;
    @Mock NotificationAttemptRepository attemptRepository;
    @Mock NotificationEnqueueService enqueueService;
    @Mock NotificationTemplateRepository templateRepository;

    private final TenantId tenantId = TenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Test
    @DisplayName("renders subject and body variables")
    void previewRenders() {
        NotificationTemplate template = new NotificationTemplate(
                UUID.randomUUID(), null, NotificationEventType.INVOICE_ISSUED,
                NotificationChannel.EMAIL, "en",
                "Invoice {{invoiceNumber}}",
                "Hello {{customerName}}, total {{total}}",
                null, true, Instant.now(), Instant.now());
        when(templateRepository.findActive(eq(tenantId), eq(NotificationEventType.INVOICE_ISSUED),
                eq(NotificationChannel.EMAIL), eq("en")))
                .thenReturn(Optional.of(template));

        NotificationApplicationService service = new NotificationApplicationService(
                notificationRepository, attemptRepository, enqueueService, templateRepository, null);

        NotificationUseCase.PreviewResult r = service.preview(tenantId,
                NotificationEventType.INVOICE_ISSUED, NotificationChannel.EMAIL,
                Map.of("invoiceNumber", "INV-9", "customerName", "Bob", "total", "50.00"));

        assertEquals("Invoice INV-9", r.subject());
        assertEquals("Hello Bob, total 50.00", r.body());
        assertEquals("INVOICE_ISSUED", r.eventType());
        assertEquals("EMAIL", r.channel());
    }

    @Test
    @DisplayName("missing template throws NO_TEMPLATE")
    void missingTemplate() {
        when(templateRepository.findActive(any(), any(), any(), any())).thenReturn(Optional.empty());
        NotificationApplicationService service = new NotificationApplicationService(
                notificationRepository, attemptRepository, enqueueService, templateRepository, null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.preview(tenantId, NotificationEventType.DUNNING_NOTICE,
                        NotificationChannel.EMAIL, Map.of()));
        assertEquals("NO_TEMPLATE", ex.getMessage());
    }
}
