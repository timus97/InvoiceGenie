package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.AuditQueryUseCase;
import com.invoicegenie.ar.domain.model.outbox.AuditEntry;
import com.invoicegenie.ar.domain.model.outbox.AuditRepository;
import com.invoicegenie.shared.domain.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Application service: audit log queries and CSV export.
 */
public class AuditQueryService implements AuditQueryUseCase {

    private final AuditRepository auditRepository;

    public AuditQueryService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    @Override
    public List<AuditEntry> listRecent(TenantId tenantId, int limit) {
        int safe = Math.min(Math.max(limit, 1), 1000);
        return auditRepository.findRecentByTenant(tenantId, safe);
    }

    @Override
    public List<AuditEntry> listForEntity(TenantId tenantId, String entityType, UUID entityId) {
        return auditRepository.findByTenantAndEntity(tenantId, entityType, entityId);
    }

    @Override
    public String exportCsv(TenantId tenantId, int limit) {
        List<AuditEntry> entries = listRecent(tenantId, limit);
        StringBuilder sb = new StringBuilder();
        sb.append("id,entityType,entityId,entityRef,action,actorType,createdAt\n");
        for (AuditEntry e : entries) {
            sb.append(csv(e.getId().toString())).append(',')
                    .append(csv(e.getEntityType())).append(',')
                    .append(csv(e.getEntityId() != null ? e.getEntityId().toString() : "")).append(',')
                    .append(csv(e.getEntityRef())).append(',')
                    .append(csv(e.getAction())).append(',')
                    .append(csv(e.getActorType())).append(',')
                    .append(csv(e.getCreatedAt() != null ? e.getCreatedAt().toString() : ""))
                    .append('\n');
        }
        return sb.toString();
    }

    private static String csv(String v) {
        if (v == null) return "";
        String s = v.replace("\"", "\"\"");
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s + "\"";
        }
        return s;
    }
}