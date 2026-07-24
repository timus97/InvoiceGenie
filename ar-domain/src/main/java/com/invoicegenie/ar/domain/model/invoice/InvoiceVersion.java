package com.invoicegenie.ar.domain.model.invoice;

import com.invoicegenie.shared.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable snapshot of an invoice at a given version.
 */
public final class InvoiceVersion {

    private final UUID id;
    private final TenantId tenantId;
    private final InvoiceId invoiceId;
    private final long version;
    private final String snapshotJson;
    private final String changeReason;
    private final Instant createdAt;

    public InvoiceVersion(UUID id, TenantId tenantId, InvoiceId invoiceId, long version,
                          String snapshotJson, String changeReason, Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.invoiceId = Objects.requireNonNull(invoiceId);
        if (version < 1) {
            throw new IllegalArgumentException("version must be >= 1");
        }
        this.version = version;
        if (snapshotJson == null || snapshotJson.isBlank()) {
            throw new IllegalArgumentException("snapshotJson is required");
        }
        this.snapshotJson = snapshotJson;
        this.changeReason = changeReason;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public static InvoiceVersion of(TenantId tenantId, InvoiceId invoiceId, long version,
                                    String snapshotJson, String changeReason) {
        return new InvoiceVersion(UUID.randomUUID(), tenantId, invoiceId, version,
                snapshotJson, changeReason, Instant.now());
    }

    public UUID getId() { return id; }
    public TenantId getTenantId() { return tenantId; }
    public InvoiceId getInvoiceId() { return invoiceId; }
    public long getVersion() { return version; }
    public String getSnapshotJson() { return snapshotJson; }
    public String getChangeReason() { return changeReason; }
    public Instant getCreatedAt() { return createdAt; }
}
