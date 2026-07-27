package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.notification.NotificationChannel;
import com.invoicegenie.shared.domain.TenantId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UnsubscribeTokenService (PP-011)")
class UnsubscribeTokenServiceTest {

    private final UnsubscribeTokenService service = new UnsubscribeTokenService("test-secret-key");
    private final TenantId tenantId = TenantId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private final CustomerId customerId = CustomerId.of(UUID.fromString("22222222-2222-2222-2222-222222222222"));

    @Test
    @DisplayName("round-trip generate and parse")
    void roundTrip() {
        String token = service.generate(tenantId, customerId, NotificationChannel.EMAIL);
        assertNotNull(token);
        assertTrue(token.contains("."));

        var claims = service.parse(token);
        assertTrue(claims.isPresent());
        assertEquals(tenantId, claims.get().tenantId());
        assertEquals(customerId, claims.get().customerId());
        assertEquals(NotificationChannel.EMAIL, claims.get().channel());
    }

    @Test
    @DisplayName("tampered token rejected")
    void tampered() {
        String token = service.generate(tenantId, customerId, NotificationChannel.EMAIL);
        String bad = token.substring(0, token.length() - 2) + "xx";
        assertTrue(service.parse(bad).isEmpty());
    }

    @Test
    @DisplayName("wrong secret rejected")
    void wrongSecret() {
        String token = service.generate(tenantId, customerId, NotificationChannel.WHATSAPP);
        UnsubscribeTokenService other = new UnsubscribeTokenService("other-secret");
        assertTrue(other.parse(token).isEmpty());
    }

    @Test
    @DisplayName("blank token rejected")
    void blank() {
        assertTrue(service.parse(null).isEmpty());
        assertTrue(service.parse("").isEmpty());
        assertTrue(service.parse("not-a-token").isEmpty());
    }
}
