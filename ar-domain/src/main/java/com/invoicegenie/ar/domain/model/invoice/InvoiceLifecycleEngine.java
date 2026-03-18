package com.invoicegenie.ar.domain.model.invoice;

import java.util.EnumMap;
import java.util.EnumSet;

/**
 * Centralized lifecycle rules for invoices.
 */
public final class InvoiceLifecycleEngine {

    private final EnumMap<InvoiceStatus, EnumSet<InvoiceStatus>> allowedTransitions;

    public InvoiceLifecycleEngine() {
        allowedTransitions = new EnumMap<>(InvoiceStatus.class);
        allowedTransitions.put(InvoiceStatus.DRAFT, EnumSet.of(InvoiceStatus.ISSUED));
        allowedTransitions.put(InvoiceStatus.ISSUED, EnumSet.of(
                InvoiceStatus.PARTIALLY_PAID,
                InvoiceStatus.PAID,
                InvoiceStatus.OVERDUE
        ));
        allowedTransitions.put(InvoiceStatus.PARTIALLY_PAID, EnumSet.of(
                InvoiceStatus.PAID,
                InvoiceStatus.OVERDUE
        ));
        allowedTransitions.put(InvoiceStatus.OVERDUE, EnumSet.of(
                InvoiceStatus.PARTIALLY_PAID,
                InvoiceStatus.PAID,
                InvoiceStatus.WRITTEN_OFF
        ));
        allowedTransitions.put(InvoiceStatus.PAID, EnumSet.noneOf(InvoiceStatus.class));
        allowedTransitions.put(InvoiceStatus.WRITTEN_OFF, EnumSet.noneOf(InvoiceStatus.class));
    }

    public Transition issue() {
        return new Transition(InvoiceStatus.ISSUED, "issue");
    }

    public Transition markPartiallyPaid() {
        return new Transition(InvoiceStatus.PARTIALLY_PAID, "partial-payment");
    }

    public Transition markPaid() {
        return new Transition(InvoiceStatus.PAID, "payment");
    }

    public Transition markOverdue() {
        return new Transition(InvoiceStatus.OVERDUE, "overdue");
    }

    public Transition writeOff() {
        return new Transition(InvoiceStatus.WRITTEN_OFF, "write-off");
    }

    public void assertTransitionAllowed(InvoiceStatus from, InvoiceStatus to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Invoice status cannot be null");
        }
        if (from == to) {
            throw new IllegalStateException("Invoice already in status: " + from);
        }
        EnumSet<InvoiceStatus> allowed = allowedTransitions.getOrDefault(from, EnumSet.noneOf(InvoiceStatus.class));
        if (!allowed.contains(to)) {
            throw new IllegalStateException("Invalid invoice status transition: " + from + " -> " + to);
        }
    }

    public record Transition(InvoiceStatus target, String action) {}
}
