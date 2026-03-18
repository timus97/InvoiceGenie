package com.invoicegenie.ar.domain.event;

import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import java.time.Instant;

/**
 * Domain event published when an invoice is issued. Consumed by GL (and others).
 */
public record InvoiceIssued(
        TenantId tenantId,
        InvoiceId invoiceId,
        String customerRef,
        Money total,
        Instant dueDate,
        Instant occurredAt
) {
    public InvoiceIssued {
        occurredAt = occurredAt != null ? occurredAt : Instant.now();
    }
}
