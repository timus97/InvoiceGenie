package com.invoicegenie.ar.domain.model.invoice;

import com.invoicegenie.shared.domain.TenantId;

import java.util.Optional;

/**
 * Port (outbound): persistence for Invoice aggregate.
 * All methods require TenantId — enforced by application layer.
 */
public interface InvoiceRepository {

    void save(TenantId tenantId, Invoice invoice);

    Optional<Invoice> findByTenantAndId(TenantId tenantId, InvoiceId id);

    /**
     * Cursor-based page. Cursor is (createdAt, id) of last item from previous page.
     */
    Page<Invoice> findByTenant(TenantId tenantId, int limit, PageCursor cursor);

    record PageCursor(java.time.Instant createdAt, InvoiceId id) {}
    record Page(java.util.List<Invoice> items, Optional<PageCursor> nextCursor) {}
}
