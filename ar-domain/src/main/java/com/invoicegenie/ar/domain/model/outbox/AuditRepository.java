package com.invoicegenie.ar.domain.model.outbox;

import com.invoicegenie.shared.domain.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port: persist audit entries.
 * 
 * <p>Implementations write to ar_audit_log table for compliance and debugging.
 */
public interface AuditRepository {

    /**
     * Saves an audit entry.
     */
    void save(TenantId tenantId, AuditEntry entry);

    /**
     * Finds an audit entry by ID.
     */
    Optional<AuditEntry> findByTenantAndId(TenantId tenantId, UUID id);

    /**
     * Finds all audit entries for a specific entity.
     */
    List<AuditEntry> findByTenantAndEntity(TenantId tenantId, String entityType, UUID entityId);

    /**
     * Finds recent audit entries for a tenant.
     */
    List<AuditEntry> findRecentByTenant(TenantId tenantId, int limit);
}
