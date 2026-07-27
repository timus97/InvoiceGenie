package com.invoicegenie.ar.domain.model.period;

import com.invoicegenie.shared.domain.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Tenant AR posting period (PP-025 period close MVP).
 *
 * <p>When any period rows exist for a tenant, invoice issue and payment record
 * are allowed only if {@code today} falls in an OPEN period.
 * Tenants with zero period rows remain unrestricted (backward compatible).
 */
public final class PostingPeriod {

    private final UUID id;
    private final TenantId tenantId;
    private final LocalDate periodStart;
    private final LocalDate periodEnd;
    private PostingPeriodStatus status;
    private Instant closedAt;
    private String closedBy;
    private String notes;
    private final Instant createdAt;
    private Instant updatedAt;

    public PostingPeriod(UUID id, TenantId tenantId, LocalDate periodStart, LocalDate periodEnd,
                         PostingPeriodStatus status, Instant closedAt, String closedBy, String notes,
                         Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.periodStart = Objects.requireNonNull(periodStart, "periodStart");
        this.periodEnd = Objects.requireNonNull(periodEnd, "periodEnd");
        if (periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException("periodEnd must be on or after periodStart");
        }
        this.status = status != null ? status : PostingPeriodStatus.OPEN;
        this.closedAt = closedAt;
        this.closedBy = closedBy;
        this.notes = notes;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public static PostingPeriod open(TenantId tenantId, LocalDate start, LocalDate end, String notes) {
        Instant now = Instant.now();
        return new PostingPeriod(UUID.randomUUID(), tenantId, start, end, PostingPeriodStatus.OPEN,
                null, null, notes, now, now);
    }

    public boolean contains(LocalDate day) {
        return day != null && !day.isBefore(periodStart) && !day.isAfter(periodEnd);
    }

    public boolean isOpen() {
        return status == PostingPeriodStatus.OPEN;
    }

    public void close(String closedBy) {
        if (status == PostingPeriodStatus.CLOSED) {
            throw new IllegalStateException("Period already CLOSED");
        }
        this.status = PostingPeriodStatus.CLOSED;
        this.closedAt = Instant.now();
        this.closedBy = closedBy;
        this.updatedAt = Instant.now();
    }

    public void reopen() {
        if (status == PostingPeriodStatus.OPEN) {
            throw new IllegalStateException("Period already OPEN");
        }
        this.status = PostingPeriodStatus.OPEN;
        this.closedAt = null;
        this.closedBy = null;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public TenantId getTenantId() { return tenantId; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public PostingPeriodStatus getStatus() { return status; }
    public Instant getClosedAt() { return closedAt; }
    public String getClosedBy() { return closedBy; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
