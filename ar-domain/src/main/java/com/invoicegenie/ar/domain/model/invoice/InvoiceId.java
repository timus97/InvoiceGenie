package com.invoicegenie.ar.domain.model.invoice;

import com.invoicegenie.shared.domain.EntityId;

import java.util.UUID;

public final class InvoiceId extends EntityId {

    public InvoiceId(UUID value) {
        super(value);
    }

    public static InvoiceId generate() {
        return new InvoiceId(UUID.randomUUID());
    }

    public static InvoiceId of(UUID value) {
        return new InvoiceId(value);
    }
}
