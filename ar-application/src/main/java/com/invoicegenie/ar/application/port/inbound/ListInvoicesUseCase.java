package com.invoicegenie.ar.application.port.inbound;

import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceStatus;
import com.invoicegenie.shared.domain.TenantId;

import java.util.List;
import java.util.Optional;

/**
 * Inbound port: list invoices with pagination and filtering.
 */
public interface ListInvoicesUseCase {

    /**
     * List invoices with optional cursor pagination and status filter.
     * @param tenantId tenant
     * @param limit max items (default 20, max 100)
     * @param cursor optional cursor (createdAt,id encoded as base64 or null for first page)
     * @param status optional status filter
     * @return page of invoices
     */
    PageResult list(TenantId tenantId, int limit, String cursor, InvoiceStatus status);

    record PageResult(List<Invoice> items, Optional<String> nextCursor, long total) {}
}
