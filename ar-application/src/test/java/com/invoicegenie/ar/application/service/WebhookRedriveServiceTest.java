package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.domain.model.webhook.WebhookDeliveryLog;
import com.invoicegenie.ar.domain.model.webhook.WebhookDeliveryRepository;
import com.invoicegenie.ar.domain.model.webhook.WebhookDeliveryStatus;
import com.invoicegenie.shared.domain.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("WebhookRedriveService")
@ExtendWith(MockitoExtension.class)
class WebhookRedriveServiceTest {

    @Mock WebhookDeliveryRepository repo;
    WebhookRedriveService service;
    TenantId tenantId;

    @BeforeEach
    void setUp() {
        service = new WebhookRedriveService(repo);
        tenantId = TenantId.of(UUID.randomUUID());
    }

    private WebhookDeliveryLog deadLog() {
        Instant now = Instant.now();
        WebhookDeliveryLog log = new WebhookDeliveryLog(
                UUID.randomUUID(), tenantId, UUID.randomUUID(), UUID.randomUUID(),
                "InvoiceIssued", "https://example.com/hook", "{}", WebhookDeliveryStatus.DEAD,
                5, 500, null, "gave up", null, now, now);
        return log;
    }

    @Test
    @DisplayName("redrives DEAD delivery to RETRY")
    void redriveDead() {
        WebhookDeliveryLog log = deadLog();
        when(repo.findById(log.getId())).thenReturn(Optional.of(log));

        Optional<WebhookDeliveryLog> result = service.redrive(tenantId, log.getId());

        assertTrue(result.isPresent());
        assertEquals(WebhookDeliveryStatus.RETRY, result.get().getStatus());
        assertNotNull(result.get().getNextAttemptAt());
        verify(repo).save(log);
    }

    @Test
    @DisplayName("rejects SUCCESS")
    void rejectsSuccess() {
        Instant now = Instant.now();
        WebhookDeliveryLog log = new WebhookDeliveryLog(
                UUID.randomUUID(), tenantId, UUID.randomUUID(), null,
                "InvoiceIssued", "https://example.com/hook", "{}", WebhookDeliveryStatus.SUCCESS,
                1, 200, "ok", null, null, now, now);
        when(repo.findById(log.getId())).thenReturn(Optional.of(log));

        assertThrows(IllegalStateException.class, () -> service.redrive(tenantId, log.getId()));
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("empty on tenant mismatch")
    void tenantMismatch() {
        WebhookDeliveryLog log = deadLog();
        when(repo.findById(log.getId())).thenReturn(Optional.of(log));
        assertTrue(service.redrive(TenantId.of(UUID.randomUUID()), log.getId()).isEmpty());
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("empty when not found")
    void notFound() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());
        assertTrue(service.redrive(tenantId, id).isEmpty());
    }
}
