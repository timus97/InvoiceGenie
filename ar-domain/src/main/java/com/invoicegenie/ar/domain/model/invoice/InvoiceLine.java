package com.invoicegenie.ar.domain.model.invoice;

import com.invoicegenie.shared.domain.Money;

import java.util.Objects;

public final class InvoiceLine {

    private final int sequence;
    private final String description;
    private final Money amount;

    public InvoiceLine(int sequence, String description, Money amount) {
        this.sequence = sequence;
        this.description = Objects.requireNonNull(description);
        this.amount = Objects.requireNonNull(amount);
    }

    public int getSequence() {
        return sequence;
    }

    public String getDescription() {
        return description;
    }

    public Money getAmount() {
        return amount;
    }
}
