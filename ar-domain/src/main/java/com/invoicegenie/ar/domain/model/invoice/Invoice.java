package com.invoicegenie.ar.domain.model.invoice;

import com.invoicegenie.shared.domain.Money;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * AR Aggregate Root. Invariants: total = sum of lines; status transitions are controlled.
 */
public final class Invoice {

    private final InvoiceId id;
    private final String customerRef;
    private final String currencyCode;
    private final Instant dueDate;
    private final Instant createdAt;
    private final List<InvoiceLine> lines;
    private InvoiceStatus status;

    public Invoice(InvoiceId id, String customerRef, String currencyCode, Instant dueDate,
                   List<InvoiceLine> lines, InvoiceStatus status) {
        this(id, customerRef, currencyCode, dueDate, Instant.now(), lines, status);
    }

    /** For reconstitution from persistence. */
    public Invoice(InvoiceId id, String customerRef, String currencyCode, Instant dueDate,
                   Instant createdAt, List<InvoiceLine> lines, InvoiceStatus status) {
        this.id = Objects.requireNonNull(id);
        this.customerRef = Objects.requireNonNull(customerRef);
        this.currencyCode = Objects.requireNonNull(currencyCode);
        this.dueDate = Objects.requireNonNull(dueDate);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.lines = new ArrayList<>(Objects.requireNonNull(lines));
        this.status = status == null ? InvoiceStatus.DRAFT : status;
    }

    public InvoiceId getId() {
        return id;
    }

    public String getCustomerRef() {
        return customerRef;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public Instant getDueDate() {
        return dueDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<InvoiceLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public Money getTotal() {
        Money total = Money.of(java.math.BigDecimal.ZERO, currencyCode);
        for (InvoiceLine line : lines) {
            total = total.add(line.getAmount());
        }
        return total;
    }

    public void issue() {
        if (this.status != InvoiceStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT invoices can be issued");
        }
        if (lines.isEmpty()) {
            throw new IllegalStateException("Invoice must have at least one line");
        }
        this.status = InvoiceStatus.ISSUED;
    }
}
