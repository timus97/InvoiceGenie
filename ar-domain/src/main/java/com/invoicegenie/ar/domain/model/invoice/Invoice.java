package com.invoicegenie.ar.domain.model.invoice;

import com.invoicegenie.shared.domain.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Invoice aggregate root. Core of AR; owns InvoiceLines.
 *
 * <p><b>Aggregate boundary:</b> Invoice owns its lines completely. Lines are created/modified
 * only through Invoice methods. External references use InvoiceId + CustomerId only.
 *
 * <p>Business rules in this aggregate:
 * <ul>
 *   <li>Invoice number is immutable business key (tenant-scoped unique)</li>
 *   <li>Lines: at least one required to issue; each line amount = qty * unit_price - discount + tax</li>
 *   <li>Totals: subtotal = sum(line totals); total = subtotal + tax_total (discount already applied per line)</li>
 *   <li>Status transitions: DRAFT → ISSUED → (PARTIALLY_PAID|PAID|OVERDUE), OVERDUE → WRITTEN_OFF</li>
 *   <li>Cannot modify lines after ISSUED (create credit memo for corrections)</li>
 *   <li>amountDue is managed by application layer based on allocations (not stored here)</li>
 * </ul>
 */
public final class Invoice {

    private final InvoiceId id;
    private final String invoiceNumber; // human-readable, tenant-scoped
    private final String customerRef; // denormalized for display
    private final String currencyCode; // ISO 4217
    private final LocalDate issueDate;
    private LocalDate dueDate;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    private String notes;
    private String terms;
    private InvoiceStatus status;
    private Instant issuedAt;
    private Instant writtenOffAt;

    private final List<InvoiceLine> lines = new ArrayList<>();

    private static final InvoiceLifecycleEngine LIFECYCLE = new InvoiceLifecycleEngine();

    /** For new invoice creation (DRAFT). */
    public Invoice(InvoiceId id, String invoiceNumber, String customerRef, String currencyCode,
                   LocalDate issueDate, LocalDate dueDate, List<InvoiceLine> lines) {
        this(id, invoiceNumber, customerRef, currencyCode, issueDate, dueDate, null, null,
                Instant.now(), Instant.now(), 1L, null, null, InvoiceStatus.DRAFT, null, null,
                lines != null ? lines : List.of());
        if (dueDate.isBefore(issueDate)) {
            throw new IllegalArgumentException("dueDate cannot be before issueDate");
        }
    }

    /** For reconstitution from persistence. */
    public Invoice(InvoiceId id, String invoiceNumber, String customerRef, String currencyCode,
                   LocalDate issueDate, LocalDate dueDate, LocalDate periodStart, LocalDate periodEnd,
                   Instant createdAt, Instant updatedAt, long version,
                   String notes, String terms, InvoiceStatus status,
                   Instant issuedAt, Instant writtenOffAt,
                   List<InvoiceLine> lines) {
        this.id = Objects.requireNonNull(id);
        this.invoiceNumber = Objects.requireNonNull(invoiceNumber);
        this.customerRef = Objects.requireNonNull(customerRef);
        this.currencyCode = Objects.requireNonNull(currencyCode);
        if (currencyCode.length() != 3) throw new IllegalArgumentException("currency must be ISO 4217");
        this.issueDate = Objects.requireNonNull(issueDate);
        this.dueDate = Objects.requireNonNull(dueDate);
        if (dueDate.isBefore(issueDate)) {
            throw new IllegalArgumentException("dueDate cannot be before issueDate");
        }
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.version = version;
        this.notes = notes;
        this.terms = terms;
        this.status = status == null ? InvoiceStatus.DRAFT : status;
        this.issuedAt = issuedAt;
        this.writtenOffAt = writtenOffAt;
        if (this.status != InvoiceStatus.WRITTEN_OFF) {
            this.writtenOffAt = null;
        }
        if (this.status == InvoiceStatus.ISSUED && this.issuedAt == null) {
            this.issuedAt = Instant.now();
        }
        if (lines != null) {
            for (InvoiceLine l : lines) requireSameCurrency(l.getAmount());
            this.lines.addAll(lines);
        }
        validateState();
    }

    // ==================== Accessors ====================

    public InvoiceId getId() { return id; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public String getCustomerRef() { return customerRef; }
    public String getCurrencyCode() { return currencyCode; }
    public LocalDate getIssueDate() { return issueDate; }
    public LocalDate getDueDate() { return dueDate; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
    public String getNotes() { return notes; }
    public String getTerms() { return terms; }
    public InvoiceStatus getStatus() { return status; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getWrittenOffAt() { return writtenOffAt; }

    public List<InvoiceLine> getLines() { return Collections.unmodifiableList(lines); }

    // ==================== Totals ====================

    public Money getSubtotal() {
        Money sum = Money.of(BigDecimal.ZERO, currencyCode);
        for (InvoiceLine l : lines) sum = sum.add(l.getLineTotal());
        return sum;
    }

    public Money getTaxTotal() {
        Money sum = Money.of(BigDecimal.ZERO, currencyCode);
        for (InvoiceLine l : lines) sum = sum.add(l.getTaxAmount());
        return sum;
    }

    public Money getDiscountTotal() {
        Money sum = Money.of(BigDecimal.ZERO, currencyCode);
        for (InvoiceLine l : lines) sum = sum.add(l.getDiscountAmount());
        return sum;
    }

    public Money getTotal() {
        // total = subtotal - discount_total + tax_total (discount already in line total)
        return getSubtotal().add(getTaxTotal());
    }

    // ==================== Business Logic ====================

    /**
     * Adds a line to DRAFT invoice. Recalculates totals implicitly.
     */
    public void addLine(InvoiceLine line) {
        assertDraft();
        requireSameCurrency(line.getAmount());
        // sequence should be next
        int nextSeq = lines.stream().mapToInt(InvoiceLine::getSequence).max().orElse(0) + 1;
        if (line.getSequence() != nextSeq) {
            // allow caller to set sequence; but ensure no duplicate
            if (lines.stream().anyMatch(l -> l.getSequence() == line.getSequence())) {
                throw new IllegalArgumentException("duplicate line sequence: " + line.getSequence());
            }
        }
        lines.add(line);
        touch();
    }

    /**
     * Removes a line from DRAFT invoice.
     */
    public void removeLine(int sequence) {
        assertDraft();
        boolean removed = lines.removeIf(l -> l.getSequence() == sequence);
        if (!removed) {
            throw new IllegalArgumentException("Line not found: " + sequence);
        }
        touch();
    }

    /**
     * Updates due date (only in DRAFT).
     */
    public void setDueDate(LocalDate dueDate) {
        assertDraft();
        if (dueDate.isBefore(issueDate)) {
            throw new IllegalArgumentException("dueDate cannot be before issueDate");
        }
        this.dueDate = Objects.requireNonNull(dueDate);
        touch();
    }

    /**
     * Sets billing period.
     */
    public void setPeriod(LocalDate start, LocalDate end) {
        assertDraft();
        if (start != null && end != null && end.isBefore(start)) {
            throw new IllegalArgumentException("Period end cannot be before start");
        }
        this.periodStart = start;
        this.periodEnd = end;
        touch();
    }

    /**
     * Sets notes/terms.
     */
    public void setNotesAndTerms(String notes, String terms) {
        assertDraft();
        this.notes = notes;
        this.terms = terms;
        touch();
    }

    /**
     * Issues the invoice. Transitions DRAFT → ISSUED. Emits InvoiceIssued event (via application layer).
     */
    public void issue() {
        if (lines.isEmpty()) {
            throw new IllegalStateException("Invoice must have at least one line");
        }
        transitionTo(LIFECYCLE.issue());
    }

    /**
     * Marks invoice as overdue (ISSUED/PARTIALLY_PAID → OVERDUE).
     */
    public void markOverdue(LocalDate today) {
        if (!dueDate.isBefore(today)) {
            throw new IllegalStateException("Invoice is not overdue");
        }
        transitionTo(LIFECYCLE.markOverdue());
    }

    /**
     * Marks as written-off (terminal). Only allowed from OVERDUE.
     */
    public void writeOff(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("write-off reason is required");
        }
        this.writtenOffAt = Instant.now();
        transitionTo(LIFECYCLE.writeOff());
    }

    public boolean isWrittenOff() {
        return status == InvoiceStatus.WRITTEN_OFF;
    }

    /**
     * Checks if invoice is open (can receive payments).
     */
    public boolean isOpen() {
        return status == InvoiceStatus.ISSUED || status == InvoiceStatus.PARTIALLY_PAID || status == InvoiceStatus.OVERDUE;
    }

    public boolean canIssue() {
        return status == InvoiceStatus.DRAFT && !lines.isEmpty();
    }

    public boolean canWriteOff() {
        return status == InvoiceStatus.OVERDUE;
    }

    /**
     * Checks if invoice is overdue based on dueDate.
     */
    public boolean isOverdue(LocalDate today) {
        if (status != InvoiceStatus.ISSUED && status != InvoiceStatus.PARTIALLY_PAID && status != InvoiceStatus.OVERDUE) {
            return false;
        }
        return dueDate.isBefore(today);
    }

    /**
     * Transitions to PARTIALLY_PAID or PAID based on external allocation info.
     * Called by application layer after allocations change.
     */
    public void applyPaymentStatus(boolean fullyPaid) {
        if (fullyPaid) {
            transitionTo(LIFECYCLE.markPaid());
            return;
        }
        transitionTo(LIFECYCLE.markPartiallyPaid());
    }

    public boolean canReceivePayments() {
        return isOpen() && !isWrittenOff();
    }

    // ==================== Helpers ====================

    private void assertDraft() {
        if (status != InvoiceStatus.DRAFT)
            throw new IllegalStateException("Invoice is not DRAFT: " + status);
    }

    private void transitionTo(InvoiceStatus targetStatus) {
        InvoiceStatus from = this.status;
        LIFECYCLE.assertTransitionAllowed(from, targetStatus);
        this.status = targetStatus;
        if (targetStatus == InvoiceStatus.ISSUED && issuedAt == null) {
            this.issuedAt = Instant.now();
        }
        if (targetStatus != InvoiceStatus.WRITTEN_OFF) {
            this.writtenOffAt = null;
        }
        touch();
    }

    private void transitionTo(InvoiceLifecycleEngine.Transition transition) {
        transitionTo(transition.target());
    }


    private void requireSameCurrency(Money m) {
        if (!currencyCode.equals(m.getCurrencyCode()))
            throw new IllegalArgumentException("Currency mismatch: " + currencyCode + " vs " + m.getCurrencyCode());
    }

    private void touch() {
        this.updatedAt = Instant.now();
        this.version++;
        validateState();
    }

    private void validateState() {
        if (status == InvoiceStatus.ISSUED && issuedAt == null) {
            throw new IllegalStateException("Issued invoice must have issuedAt");
        }
        if (status == InvoiceStatus.WRITTEN_OFF && writtenOffAt == null) {
            throw new IllegalStateException("Written-off invoice must have writtenOffAt");
        }
        if (writtenOffAt != null && status != InvoiceStatus.WRITTEN_OFF) {
            throw new IllegalStateException("Written-off timestamp only allowed for WRITTEN_OFF status");
        }
        if (status == InvoiceStatus.DRAFT && issuedAt != null) {
            throw new IllegalStateException("Draft invoice cannot have issuedAt");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Invoice invoice = (Invoice) o;
        return id.equals(invoice.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
