package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.domain.model.notification.NotificationChannel;
import com.invoicegenie.ar.domain.model.notification.NotificationSuppression;
import com.invoicegenie.ar.domain.model.notification.NotificationSuppressionRepository;
import com.invoicegenie.shared.domain.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("NotificationSuppressionService")
@ExtendWith(MockitoExtension.class)
class NotificationSuppressionServiceTest {

    @Mock NotificationSuppressionRepository repository;
    NotificationSuppressionService service;
    TenantId tenantId;

    @BeforeEach
    void setUp() {
        service = new NotificationSuppressionService(repository);
        tenantId = TenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    }

    @Test
    @DisplayName("normalize + hash email case-insensitively")
    void emailNormalize() {
        String n1 = NotificationSuppressionService.normalize(NotificationChannel.EMAIL, "User@Example.COM");
        String n2 = NotificationSuppressionService.normalize(NotificationChannel.EMAIL, "user@example.com");
        assertEquals(n1, n2);
        assertEquals(NotificationSuppressionService.hashDestination(n1),
                NotificationSuppressionService.hashDestination(n2));
    }

    @Test
    @DisplayName("suppress records new entry")
    void suppressNew() {
        when(repository.findByTenantChannelAndHash(eq(tenantId), eq(NotificationChannel.EMAIL), anyString()))
                .thenReturn(Optional.empty());

        NotificationSuppression s = service.suppress(tenantId, NotificationChannel.EMAIL,
                "bounce@acme.test", "Permanent", "ses");

        assertNotNull(s.getId());
        assertEquals("bounce@acme.test", s.getDestinationNormalized());
        ArgumentCaptor<NotificationSuppression> cap = ArgumentCaptor.forClass(NotificationSuppression.class);
        verify(repository).save(cap.capture());
        assertEquals("ses", cap.getValue().getProvider());
    }

    @Test
    @DisplayName("isSuppressed delegates to repository")
    void isSuppressed() {
        when(repository.isSuppressed(eq(tenantId), eq(NotificationChannel.EMAIL), anyString()))
                .thenReturn(true);
        assertTrue(service.isSuppressed(tenantId, NotificationChannel.EMAIL, "x@y.com"));
    }
}
