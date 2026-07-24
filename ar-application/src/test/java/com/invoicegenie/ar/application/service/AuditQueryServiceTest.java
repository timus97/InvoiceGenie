package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.domain.model.outbox.AuditEntry;
import com.invoicegenie.ar.domain.model.outbox.AuditRepository;
import com.invoicegenie.shared.domain.TenantId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditQueryServiceTest {

    @Mock
    AuditRepository auditRepository;

    @InjectMocks
    AuditQueryService service;

    @Test
    void exportCsv_includesActorIdIpAndUserAgent() {
        TenantId tenantId = TenantId.of(UUID.randomUUID());
        UUID actorId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        AuditEntry entry = new AuditEntry(
                UUID.randomUUID(), tenantId, "INVOICE", entityId, "INV-1", "CREATE",
                actorId, "API", null, "{}", "203.0.113.10", "InvoiceGenie-Smoke/1.0", Instant.parse("2026-07-24T12:00:00Z"));

        when(auditRepository.findRecentByTenant(any(), anyInt())).thenReturn(List.of(entry));

        String csv = service.exportCsv(tenantId, 10);

        assertTrue(csv.startsWith("id,entityType,entityId,entityRef,action,actorId,actorType,ipAddress,userAgent,createdAt"));
        assertTrue(csv.contains(actorId.toString()));
        assertTrue(csv.contains("API"));
        assertTrue(csv.contains("203.0.113.10"));
        assertTrue(csv.contains("InvoiceGenie-Smoke/1.0"));
    }
}