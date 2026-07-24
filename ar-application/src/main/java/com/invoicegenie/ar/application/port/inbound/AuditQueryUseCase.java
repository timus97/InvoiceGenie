package com.invoicegenie.ar.application.port.inbound;

import com.invoicegenie.ar.domain.model.outbox.AuditEntry;
import com.invoicegenie.shared.domain.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Inbound port: audit log query and export.
 */
public interface AuditQueryUseCase {

    List<AuditEntry> listRecent(TenantId tenantId, int limit);

    List<AuditEntry> listForEntity(TenantId tenantId, String entityType, UUID entityId);

    /**
     * CSV export of recent audit rows (header + rows).
     */
    String exportCsv(TenantId tenantId, int limit);
}