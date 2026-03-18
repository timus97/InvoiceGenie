package com.invoicegenie.ar.application.port.outbound;

import com.invoicegenie.ar.domain.model.invoice.InvoiceId;

import java.util.UUID;

/**
 * Outbound port: generate IDs. Use UUID v7 for time-ordered, partition-friendly ids at scale.
 */
public interface IdGenerator {

    InvoiceId newInvoiceId();

    default UUID newUuid() {
        return UUID.randomUUID();
    }
}
