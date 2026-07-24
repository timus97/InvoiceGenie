package com.invoicegenie.ar.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "ar_invoice_version")
@IdClass(InvoiceVersionEntity.Pk.class)
public class InvoiceVersionEntity {

    @Id
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Id
    @Column(name = "invoice_id", nullable = false, updatable = false)
    private UUID invoiceId;

    @Id
    @Column(name = "version", nullable = false, updatable = false)
    private long version;

    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "snapshot", nullable = false, columnDefinition = "TEXT")
    private String snapshot;

    @Column(name = "change_reason")
    private String changeReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static class Pk implements Serializable {
        public UUID tenantId;
        public UUID invoiceId;
        public long version;

        public Pk() {}
        public Pk(UUID tenantId, UUID invoiceId, long version) {
            this.tenantId = tenantId;
            this.invoiceId = invoiceId;
            this.version = version;
        }
        public UUID getTenantId() { return tenantId; }
        public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
        public UUID getInvoiceId() { return invoiceId; }
        public void setInvoiceId(UUID invoiceId) { this.invoiceId = invoiceId; }
        public long getVersion() { return version; }
        public void setVersion(long version) { this.version = version; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk pk)) return false;
            return version == pk.version && Objects.equals(tenantId, pk.tenantId) && Objects.equals(invoiceId, pk.invoiceId);
        }
        @Override public int hashCode() { return Objects.hash(tenantId, invoiceId, version); }
    }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getInvoiceId() { return invoiceId; }
    public void setInvoiceId(UUID invoiceId) { this.invoiceId = invoiceId; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getSnapshot() { return snapshot; }
    public void setSnapshot(String snapshot) { this.snapshot = snapshot; }
    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}