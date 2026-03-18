package com.invoicegenie.ar.domain.model.invoice;

import com.invoicegenie.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InvoiceLifecycleTest {

    @Test
    void issuesFromDraft() {
        Invoice invoice = draftInvoice();
        invoice.issue();
        assertEquals(InvoiceStatus.ISSUED, invoice.getStatus());
        assertNotNull(invoice.getIssuedAt());
    }

    @Test
    void failsToIssueWhenNoLines() {
        Invoice invoice = new Invoice(InvoiceId.of(UUID.randomUUID()), "INV-EMPTY", "CUST-1", "USD",
                LocalDate.now(), LocalDate.now().plusDays(10), List.of());
        assertThrows(IllegalStateException.class, invoice::issue);
    }

    @Test
    void marksOverdueFromIssued() {
        Invoice invoice = draftInvoice();
        invoice.issue();
        invoice.markOverdue(LocalDate.now().plusDays(40));
        assertEquals(InvoiceStatus.OVERDUE, invoice.getStatus());
    }

    @Test
    void refusesOverdueWhenNotPastDue() {
        Invoice invoice = draftInvoice();
        invoice.issue();
        assertThrows(IllegalStateException.class, () -> invoice.markOverdue(LocalDate.now().plusDays(5)));
    }

    @Test
    void writeOffRequiresOverdue() {
        Invoice invoice = draftInvoice();
        invoice.issue();
        assertThrows(IllegalStateException.class, () -> invoice.writeOff("bad debt"));
    }

    @Test
    void writeOffRequiresReason() {
        Invoice invoice = draftInvoice();
        invoice.issue();
        invoice.markOverdue(LocalDate.now().plusDays(40));
        assertThrows(IllegalArgumentException.class, () -> invoice.writeOff(" "));
    }

    @Test
    void writeOffSetsTimestampAndStatus() {
        Invoice invoice = draftInvoice();
        invoice.issue();
        invoice.markOverdue(LocalDate.now().plusDays(40));
        invoice.writeOff("bad debt");
        assertEquals(InvoiceStatus.WRITTEN_OFF, invoice.getStatus());
        assertNotNull(invoice.getWrittenOffAt());
    }

    @Test
    void applyPaymentStatusTransitions() {
        Invoice invoice = draftInvoice();
        invoice.issue();
        invoice.applyPaymentStatus(false);
        assertEquals(InvoiceStatus.PARTIALLY_PAID, invoice.getStatus());
        invoice.applyPaymentStatus(true);
        assertEquals(InvoiceStatus.PAID, invoice.getStatus());
    }

    @Test
    void canIssueFlagsDraftOnly() {
        Invoice invoice = draftInvoice();
        assertEquals(true, invoice.canIssue());
        invoice.issue();
        assertEquals(false, invoice.canIssue());
    }

    @Test
    void canWriteOffOnlyWhenOverdue() {
        Invoice invoice = draftInvoice();
        invoice.issue();
        assertEquals(false, invoice.canWriteOff());
        invoice.markOverdue(LocalDate.now().plusDays(40));
        assertEquals(true, invoice.canWriteOff());
    }

    @Test
    void cannotApplyPaymentStatusFromDraft() {
        Invoice invoice = draftInvoice();
        assertThrows(IllegalStateException.class, () -> invoice.applyPaymentStatus(true));
    }

    @Test
    void cannotEditLinesAfterIssue() {
        Invoice invoice = draftInvoice();
        invoice.issue();
        assertThrows(IllegalStateException.class, () -> invoice.addLine(line(2, "Extra", "50.00")));
    }

    @Test
    void cannotSetDueDateBeforeIssueDate() {
        assertThrows(IllegalArgumentException.class, () -> new Invoice(InvoiceId.of(UUID.randomUUID()), "INV-3", "CUST-1", "USD",
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 3, 1), List.of(line(1, "Line", "10.00"))));
    }

    private Invoice draftInvoice() {
        return new Invoice(InvoiceId.of(UUID.randomUUID()), "INV-001", "CUST-1", "USD",
                LocalDate.now(), LocalDate.now().plusDays(30), List.of(line(1, "Design", "100.00")));
    }

    private InvoiceLine line(int sequence, String description, String amount) {
        return new InvoiceLine(sequence, description, BigDecimal.ONE, Money.of(amount, "USD"), Money.of("0.00", "USD"),
                BigDecimal.ZERO, Money.of("0.00", "USD"), Money.of(amount, "USD"));
    }
}
