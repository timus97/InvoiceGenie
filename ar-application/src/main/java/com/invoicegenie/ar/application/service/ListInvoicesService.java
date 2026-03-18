package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.ListInvoicesUseCase;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.domain.model.invoice.InvoiceStatus;
import com.invoicegenie.shared.domain.TenantId;

import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Application service: list invoices with pagination.
 */
public class ListInvoicesService implements ListInvoicesUseCase {

    private final InvoiceRepository invoiceRepository;

    public ListInvoicesService(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    @Override
    public PageResult list(TenantId tenantId, int limit, String cursor, InvoiceStatus status) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        InvoiceRepository.PageCursor pageCursor = decodeCursor(cursor);

        // Note: current repo doesn't filter by status; we filter post-fetch for now
        InvoiceRepository.Page page = invoiceRepository.findByTenant(tenantId, safeLimit, pageCursor);

        List<Invoice> items = page.items();
        if (status != null) {
            items = items.stream().filter(i -> i.getStatus() == status).toList();
        }

        String nextCursor = page.nextCursor().map(this::encodeCursor).orElse(null);
        return new PageResult(items, Optional.ofNullable(nextCursor), items.size());
    }

    private InvoiceRepository.PageCursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor));
            String[] parts = decoded.split("\\|", 2);
            if (parts.length == 2) {
                java.time.Instant createdAt = java.time.Instant.parse(parts[0]);
                com.invoicegenie.ar.domain.model.invoice.InvoiceId id = com.invoicegenie.ar.domain.model.invoice.InvoiceId.of(java.util.UUID.fromString(parts[1]));
                return new InvoiceRepository.PageCursor(createdAt, id);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String encodeCursor(InvoiceRepository.PageCursor c) {
        String raw = c.createdAt().toString() + "|" + c.id().getValue().toString();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes());
    }
}
