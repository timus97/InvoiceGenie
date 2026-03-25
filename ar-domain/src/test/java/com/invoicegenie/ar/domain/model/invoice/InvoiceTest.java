package com.invoicegenie.ar.domain.model.invoice;

import com.invoicegenie.shared.domain.Money;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Invoice Aggregate")
class InvoiceTest {

    private InvoiceId invoiceId;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        invoiceId = InvoiceId.generate();
        invoice = new Invoice(invoiceId, "INV-001", "CUST001", "USD",
                LocalDate.now(), LocalDate.now().plusDays(30), List.of());
    }

    private InvoiceLine createLine(int seq, Money amount) {
        return new InvoiceLine(seq, "Service", amount);
    }

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should create invoice with required fields")
        void shouldCreateWithRequiredFields() {
            assertNotNull(invoice.getId());
            assertEquals("INV-001", invoice.getInvoiceNumber());
            assertEquals("CUST001", invoice.getCustomerRef());
            assertEquals("USD", invoice.getCurrencyCode());
            assertEquals(InvoiceStatus.DRAFT, invoice.getStatus());
        }

        @Test
        @DisplayName("should throw when dueDate is before issueDate")
        void shouldThrowWhenDueDateBeforeIssueDate() {
            assertThrows(IllegalArgumentException.class, () ->
                    new Invoice(invoiceId, "INV-001", "CUST001", "USD",
                            LocalDate.now(), LocalDate.now().minusDays(1), List.of()));
        }

        @Test
        @DisplayName("should create invoice with all fields for reconstitution")
        void shouldCreateWithAllFields() {
            Instant now = Instant.now();
            InvoiceLine line = new InvoiceLine(1, "Service", Money.of("100.00", "EUR"));
            
            Invoice fullInvoice = new Invoice(
                    invoiceId, "INV-002", "CUST002", "EUR",
                    LocalDate.now(), LocalDate.now().plusDays(30),
                    LocalDate.now().minusDays(10), LocalDate.now().plusDays(20),
                    now, now, 2L, "Notes", "Terms", InvoiceStatus.ISSUED,
                    now, null, List.of(line)
            );
            
            assertEquals("INV-002", fullInvoice.getInvoiceNumber());
            assertEquals("EUR", fullInvoice.getCurrencyCode());
            assertEquals(InvoiceStatus.ISSUED, fullInvoice.getStatus());
            assertEquals("Notes", fullInvoice.getNotes());
            assertEquals("Terms", fullInvoice.getTerms());
        }
    }

    @Nested
    @DisplayName("Line Management")
    class LineManagement {

        @Test
        @DisplayName("should add line to draft invoice")
        void shouldAddLineToDraft() {
            InvoiceLine line = createLine(1, Money.of("100.00", "USD"));
            invoice.addLine(line);
            
            assertEquals(1, invoice.getLines().size());
            assertEquals(line, invoice.getLines().get(0));
        }

        @Test
        @DisplayName("should throw when adding line with duplicate sequence")
        void shouldThrowWhenDuplicateSequence() {
            invoice.addLine(createLine(1, Money.of("100.00", "USD")));
            
            assertThrows(IllegalArgumentException.class, () ->
                    invoice.addLine(createLine(1, Money.of("200.00", "USD"))));
        }

        @Test
        @DisplayName("should throw when adding line with different currency")
        void shouldThrowWhenDifferentCurrency() {
            InvoiceLine eurLine = new InvoiceLine(1, "Service", Money.of("100.00", "EUR"));
            
            assertThrows(IllegalArgumentException.class, () -> invoice.addLine(eurLine));
        }

        @Test
        @DisplayName("should remove line from draft invoice")
        void shouldRemoveLineFromDraft() {
            invoice.addLine(createLine(1, Money.of("100.00", "USD")));
            invoice.addLine(createLine(2, Money.of("200.00", "USD")));
            
            invoice.removeLine(1);
            
            assertEquals(1, invoice.getLines().size());
            assertEquals(2, invoice.getLines().get(0).getSequence());
        }

        @Test
        @DisplayName("should throw when removing non-existent line")
        void shouldThrowWhenRemovingNonExistent() {
            assertThrows(IllegalArgumentException.class, () -> invoice.removeLine(99));
        }

        @Test
        @DisplayName("should throw when modifying issued invoice")
        void shouldThrowWhenModifyingIssued() {
            invoice.addLine(createLine(1, Money.of("100.00", "USD")));
            invoice.issue();
            
            assertThrows(IllegalStateException.class, () ->
                    invoice.addLine(createLine(2, Money.of("200.00", "USD"))));
        }
    }

    @Nested
    @DisplayName("Totals")
    class Totals {

        @Test
        @DisplayName("should calculate subtotal")
        void shouldCalculateSubtotal() {
            invoice.addLine(createLine(1, Money.of("100.00", "USD")));
            invoice.addLine(createLine(2, Money.of("200.00", "USD")));
            
            Money subtotal = invoice.getSubtotal();
            
            assertEquals(0, new BigDecimal("300.00").compareTo(subtotal.getAmount()));
        }

        @Test
        @DisplayName("should calculate total")
        void shouldCalculateTotal() {
            invoice.addLine(createLine(1, Money.of("100.00", "USD")));
            invoice.addLine(createLine(2, Money.of("200.00", "USD")));
            
            Money total = invoice.getTotal();
            
            assertEquals(0, new BigDecimal("300.00").compareTo(total.getAmount()));
        }
    }

    @Nested
    @DisplayName("Lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("should issue invoice")
        void shouldIssueInvoice() {
            invoice.addLine(createLine(1, Money.of("100.00", "USD")));
            invoice.issue();
            
            assertEquals(InvoiceStatus.ISSUED, invoice.getStatus());
            assertNotNull(invoice.getIssuedAt());
        }

        @Test
        @DisplayName("should throw when issuing without lines")
        void shouldThrowWhenIssuingWithoutLines() {
            assertThrows(IllegalStateException.class, () -> invoice.issue());
        }

        @Test
        @DisplayName("should check canIssue")
        void shouldCheckCanIssue() {
            assertFalse(invoice.canIssue());
            invoice.addLine(createLine(1, Money.of("100.00", "USD")));
            assertTrue(invoice.canIssue());
        }

        @Test
        @DisplayName("should mark overdue")
        void shouldMarkOverdue() {
            // Create invoice with past due date
            Invoice overdueInvoice = new Invoice(
                    InvoiceId.generate(), "INV-OVR", "CUST001", "USD",
                    LocalDate.now().minusDays(60), LocalDate.now().minusDays(30), List.of()
            );
            overdueInvoice.addLine(createLine(1, Money.of("100.00", "USD")));
            overdueInvoice.issue();
            
            // Mark as overdue with today's date (due date was 30 days ago)
            overdueInvoice.markOverdue(LocalDate.now());
            
            assertEquals(InvoiceStatus.OVERDUE, overdueInvoice.getStatus());
        }

        @Test
        @DisplayName("should throw when marking not overdue invoice")
        void shouldThrowWhenMarkingNotOverdue() {
            invoice.addLine(createLine(1, Money.of("100.00", "USD")));
            invoice.issue();
            
            // Due date is 30 days from now, so it's not overdue today
            assertThrows(IllegalStateException.class, () ->
                    invoice.markOverdue(LocalDate.now()));
        }

        @Test
        @DisplayName("should write off overdue invoice")
        void shouldWriteOffOverdueInvoice() {
            // Create invoice with past due date
            Invoice overdueInvoice = new Invoice(
                    InvoiceId.generate(), "INV-OVR", "CUST001", "USD",
                    LocalDate.now().minusDays(60), LocalDate.now().minusDays(30), List.of()
            );
            overdueInvoice.addLine(createLine(1, Money.of("100.00", "USD")));
            overdueInvoice.issue();
            overdueInvoice.markOverdue(LocalDate.now());
            
            overdueInvoice.writeOff("Bad debt");
            
            assertEquals(InvoiceStatus.WRITTEN_OFF, overdueInvoice.getStatus());
            assertNotNull(overdueInvoice.getWrittenOffAt());
        }

        @Test
        @DisplayName("should throw when writing off without reason")
        void shouldThrowWhenWritingOffWithoutReason() {
            // Create invoice with past due date
            Invoice overdueInvoice = new Invoice(
                    InvoiceId.generate(), "INV-OVR", "CUST001", "USD",
                    LocalDate.now().minusDays(60), LocalDate.now().minusDays(30), List.of()
            );
            overdueInvoice.addLine(createLine(1, Money.of("100.00", "USD")));
            overdueInvoice.issue();
            overdueInvoice.markOverdue(LocalDate.now());
            
            assertThrows(IllegalArgumentException.class, () -> overdueInvoice.writeOff(""));
        }

        @Test
        @DisplayName("should apply payment status")
        void shouldApplyPaymentStatus() {
            invoice.addLine(createLine(1, Money.of("100.00", "USD")));
            invoice.issue();
            
            invoice.applyPaymentStatus(true);
            assertEquals(InvoiceStatus.PAID, invoice.getStatus());
            
            invoice.reopen("Payment reversed");
            invoice.applyPaymentStatus(false);
            assertEquals(InvoiceStatus.PARTIALLY_PAID, invoice.getStatus());
        }

        @Test
        @DisplayName("should reopen paid invoice")
        void shouldReopenPaidInvoice() {
            invoice.addLine(createLine(1, Money.of("100.00", "USD")));
            invoice.issue();
            invoice.applyPaymentStatus(true);
            
            invoice.reopen("Cheque bounced");
            
            assertEquals(InvoiceStatus.ISSUED, invoice.getStatus());
            assertTrue(invoice.getNotes().contains("[REOPENED]"));
        }
    }

    @Nested
    @DisplayName("Open Status")
    class OpenStatus {

        @Test
        @DisplayName("should check isOpen")
        void shouldCheckIsOpen() {
            assertFalse(invoice.isOpen()); // DRAFT
            
            invoice.addLine(createLine(1, Money.of("100.00", "USD")));
            invoice.issue();
            assertTrue(invoice.isOpen()); // ISSUED
            
            invoice.applyPaymentStatus(false);
            assertTrue(invoice.isOpen()); // PARTIALLY_PAID
        }

        @Test
        @DisplayName("should check canReceivePayments")
        void shouldCheckCanReceivePayments() {
            invoice.addLine(createLine(1, Money.of("100.00", "USD")));
            invoice.issue();
            
            assertTrue(invoice.canReceivePayments());
        }

        @Test
        @DisplayName("should check isOverdue")
        void shouldCheckIsOverdue() {
            // Create invoice with past due date
            Invoice overdueInvoice = new Invoice(
                    InvoiceId.generate(), "INV-OVR", "CUST001", "USD",
                    LocalDate.now().minusDays(60), LocalDate.now().minusDays(30), List.of()
            );
            overdueInvoice.addLine(createLine(1, Money.of("100.00", "USD")));
            overdueInvoice.issue();
            
            // Today is after due date (30 days ago)
            assertTrue(overdueInvoice.isOverdue(LocalDate.now()));
            
            // Create invoice with future due date
            Invoice futureInvoice = new Invoice(
                    InvoiceId.generate(), "INV-FUT", "CUST001", "USD",
                    LocalDate.now(), LocalDate.now().plusDays(30), List.of()
            );
            futureInvoice.addLine(createLine(1, Money.of("100.00", "USD")));
            futureInvoice.issue();
            
            // Today is before due date
            assertFalse(futureInvoice.isOverdue(LocalDate.now()));
        }
    }

    @Nested
    @DisplayName("Due Date and Period")
    class DueDateAndPeriod {

        @Test
        @DisplayName("should set due date")
        void shouldSetDueDate() {
            LocalDate newDueDate = LocalDate.now().plusDays(60);
            invoice.setDueDate(newDueDate);
            
            assertEquals(newDueDate, invoice.getDueDate());
        }

        @Test
        @DisplayName("should throw when setting due date before issue date")
        void shouldThrowWhenSettingDueDateBeforeIssue() {
            assertThrows(IllegalArgumentException.class, () ->
                    invoice.setDueDate(LocalDate.now().minusDays(1)));
        }

        @Test
        @DisplayName("should set billing period")
        void shouldSetBillingPeriod() {
            LocalDate start = LocalDate.now().minusDays(30);
            LocalDate end = LocalDate.now();
            
            invoice.setPeriod(start, end);
            
            assertEquals(start, invoice.getPeriodStart());
            assertEquals(end, invoice.getPeriodEnd());
        }

        @Test
        @DisplayName("should throw when period end is before start")
        void shouldThrowWhenPeriodEndBeforeStart() {
            assertThrows(IllegalArgumentException.class, () ->
                    invoice.setPeriod(LocalDate.now(), LocalDate.now().minusDays(1)));
        }
    }

    @Nested
    @DisplayName("Notes and Terms")
    class NotesAndTerms {

        @Test
        @DisplayName("should set notes and terms")
        void shouldSetNotesAndTerms() {
            invoice.setNotesAndTerms("Customer notes", "Payment terms");
            
            assertEquals("Customer notes", invoice.getNotes());
            assertEquals("Payment terms", invoice.getTerms());
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("should be equal by id")
        void shouldBeEqualById() {
            Invoice other = new Invoice(invoiceId, "INV-999", "OTHER", "EUR",
                    LocalDate.now(), LocalDate.now().plusDays(30), List.of());
            
            assertEquals(invoice, other);
        }

        @Test
        @DisplayName("should not be equal when different ids")
        void shouldNotBeEqualWhenDifferentIds() {
            Invoice other = new Invoice(InvoiceId.generate(), "INV-001", "CUST001", "USD",
                    LocalDate.now(), LocalDate.now().plusDays(30), List.of());
            
            assertNotEquals(invoice, other);
        }
    }
}
