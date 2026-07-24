package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.WebhookUseCase;
import com.invoicegenie.ar.domain.model.webhook.WebhookRepository;
import com.invoicegenie.ar.domain.model.webhook.WebhookSubscription;
import com.invoicegenie.shared.domain.TenantId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("WebhookApplicationService")
@ExtendWith(MockitoExtension.class)
class WebhookApplicationServiceTest {

    @Mock private WebhookRepository webhookRepository;

    private WebhookApplicationService service;
    private TenantId tenantId;

    @BeforeEach
    void setUp() {
        service = new WebhookApplicationService(webhookRepository);
        tenantId = TenantId.of(UUID.randomUUID());
    }

    @Test
    @DisplayName("create saves subscription")
    void create() {
        var sub = service.create(tenantId, new WebhookUseCase.CreateWebhookCommand(
                "https://example.com/hook", "secret", "InvoiceIssued,PaymentRecorded"));

        assertTrue(sub.isActive());
        assertEquals("https://example.com/hook", sub.getUrl());
        verify(webhookRepository).save(eq(tenantId), any(WebhookSubscription.class));
    }

    @Test
    @DisplayName("list returns all")
    void list() {
        when(webhookRepository.findAllByTenant(tenantId)).thenReturn(List.of(
                WebhookSubscription.create("https://a.com", "s", "*")));

        assertEquals(1, service.list(tenantId).size());
    }

    @Test
    @DisplayName("get returns optional")
    void get() {
        UUID id = UUID.randomUUID();
        when(webhookRepository.findById(tenantId, id)).thenReturn(Optional.empty());
        assertTrue(service.get(tenantId, id).isEmpty());
    }

    @Nested
    @DisplayName("activate / deactivate / delete")
    class Lifecycle {
        @Test
        @DisplayName("deactivate active webhook")
        void deactivate() {
            WebhookSubscription sub = WebhookSubscription.create("https://b.com", "s", "*");
            when(webhookRepository.findById(tenantId, sub.getId())).thenReturn(Optional.of(sub));

            var result = service.deactivate(tenantId, sub.getId());

            assertTrue(result.isPresent());
            assertFalse(result.get().isActive());
            verify(webhookRepository).save(tenantId, sub);
        }

        @Test
        @DisplayName("activate inactive webhook")
        void activate() {
            WebhookSubscription sub = WebhookSubscription.create("https://c.com", "s", "*");
            sub.deactivate();
            when(webhookRepository.findById(tenantId, sub.getId())).thenReturn(Optional.of(sub));

            var result = service.activate(tenantId, sub.getId());

            assertTrue(result.isPresent());
            assertTrue(result.get().isActive());
            verify(webhookRepository).save(tenantId, sub);
        }

        @Test
        @DisplayName("deactivate returns empty when missing")
        void deactivateMissing() {
            when(webhookRepository.findById(any(), any())).thenReturn(Optional.empty());
            assertTrue(service.deactivate(tenantId, UUID.randomUUID()).isEmpty());
        }

        @Test
        @DisplayName("delete returns true when found")
        void deleteFound() {
            UUID id = UUID.randomUUID();
            when(webhookRepository.findById(tenantId, id))
                    .thenReturn(Optional.of(WebhookSubscription.create("https://d.com", "s", "*")));

            assertTrue(service.delete(tenantId, id));
            verify(webhookRepository).delete(tenantId, id);
        }

        @Test
        @DisplayName("delete returns false when missing")
        void deleteMissing() {
            when(webhookRepository.findById(any(), any())).thenReturn(Optional.empty());
            assertFalse(service.delete(tenantId, UUID.randomUUID()));
            verify(webhookRepository, never()).delete(any(), any());
        }
    }
}