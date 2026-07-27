package com.invoicegenie.ar.domain.model.notification;

import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.shared.domain.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port: notification queue / history.
 */
public interface NotificationRepository {

    void save(Notification notification);

    Optional<Notification> findById(TenantId tenantId, UUID id);

    Optional<Notification> findByIdempotencyKey(TenantId tenantId, String idempotencyKey);

    List<Notification> findByTenant(TenantId tenantId, int limit);

    /**
     * Cursor page: created_at DESC, id DESC (PP-023).
     */
    Page findByTenant(TenantId tenantId, int limit, PageCursor cursor);

    List<Notification> findByInvoice(TenantId tenantId, InvoiceId invoiceId, int limit);

    record PageCursor(Instant createdAt, UUID id) {}

    record Page(List<Notification> items, Optional<PageCursor> nextCursor) {}

    /**
     * Cross-tenant poll for due PENDING/QUEUED notifications.
     * Requires DB role that bypasses RLS (table owner) or unset FORCE RLS.
     */
    List<Notification> findDue(Instant now, int limit);

    /**
     * Atomically claims due rows (mark SENDING) using row locks when supported.
     * Safe for multi-instance dispatch workers.
     */
    List<Notification> claimDue(Instant now, int limit);

    /**
     * Delete a SKIPPED row so a recoverable skip can be re-enqueued under the same key.
     */
    void delete(TenantId tenantId, UUID id);
}
