package com.invoicegenie.ar.adapter.persistence.mapper;

import com.invoicegenie.ar.adapter.persistence.entity.InvoiceEntity;
import com.invoicegenie.ar.adapter.persistence.entity.InvoiceLineEntity;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceLine;
import com.invoicegenie.ar.domain.model.invoice.InvoiceStatus;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import java.math.BigDecimal;
import java.util.List;

/**
 * Maps Invoice aggregate ↔ JPA entities.
 */
public final class InvoiceMapper {

    public InvoiceEntity toEntity(TenantId tenantId, Invoice invoice) {
        InvoiceEntity e = new InvoiceEntity();
        e.setId(invoice.getId().getValue());
        e.setTenantId(tenantId.getValue());
        e.setInvoiceNumber(invoice.getInvoiceNumber());
        e.setCustomerId(invoice.getCustomerId() != null ? invoice.getCustomerId().getValue() : null);
        e.setCustomerRef(invoice.getCustomerRef());
        e.setCurrencyCode(invoice.getCurrencyCode());
        e.setIssueDate(invoice.getIssueDate());
        e.setDueDate(invoice.getDueDate());
        e.setPeriodStart(invoice.getPeriodStart());
        e.setPeriodEnd(invoice.getPeriodEnd());
        e.setNotes(invoice.getNotes());
        e.setTerms(invoice.getTerms());
        e.setCreatedAt(invoice.getCreatedAt());
        e.setUpdatedAt(invoice.getUpdatedAt());
        e.setIssuedAt(invoice.getIssuedAt());
        e.setWrittenOffAt(invoice.getWrittenOffAt());
        e.setVersion(invoice.getVersion());
        e.setStatus(invoice.getStatus());
        e.setSubtotal(invoice.getSubtotal().getAmount());
        e.setTaxTotal(invoice.getTaxTotal().getAmount());
        e.setDiscountTotal(invoice.getDiscountTotal().getAmount());
        e.setTotal(invoice.getTotal().getAmount());
        // Persist outstanding as amount_due (schema column); domain tracks amountPaid
        e.setAmountDue(invoice.getBalanceDue().getAmount());
        return e;
    }

    public List<InvoiceLineEntity> toLineEntities(TenantId tenantId, Invoice invoice) {
        return invoice.getLines().stream()
                .map(line -> toLineEntity(tenantId, invoice.getId(), line))
                .toList();
    }

    public InvoiceLineEntity toLineEntity(TenantId tenantId, InvoiceId invoiceId, InvoiceLine line) {
        InvoiceLineEntity e = new InvoiceLineEntity();
        e.setTenantId(tenantId.getValue());
        e.setInvoiceId(invoiceId.getValue());
        e.setSequence(line.getSequence());
        e.setDescription(line.getDescription());
        e.setQuantity(line.getQuantity());
        e.setUnitPrice(line.getUnitPrice().getAmount());
        e.setDiscountAmount(line.getDiscountAmount().getAmount());
        e.setTaxRate(line.getTaxRate());
        e.setTaxAmount(line.getTaxAmount().getAmount());
        e.setLineTotal(line.getLineTotal().getAmount());
        return e;
    }

    public Invoice toDomain(InvoiceEntity e, List<InvoiceLineEntity> lineEntities) {
        List<InvoiceLine> lines = lineEntities.stream()
                .map(le -> new InvoiceLine(
                        le.getSequence(),
                        le.getDescription(),
                        le.getQuantity(),
                        Money.of(le.getUnitPrice(), e.getCurrencyCode()),
                        Money.of(le.getDiscountAmount(), e.getCurrencyCode()),
                        le.getTaxRate(),
                        Money.of(le.getTaxAmount(), e.getCurrencyCode()),
                        Money.of(le.getLineTotal(), e.getCurrencyCode())
                ))
                .toList();

        // amountPaid = total - amount_due (amount_due is outstanding balance)
        Money amountPaid = resolveAmountPaid(e, lines);

        CustomerId customerId = e.getCustomerId() != null ? CustomerId.of(e.getCustomerId()) : null;

        return new Invoice(
                InvoiceId.of(e.getId()),
                e.getInvoiceNumber(),
                customerId,
                e.getCustomerRef(),
                e.getCurrencyCode(),
                e.getIssueDate(),
                e.getDueDate(),
                e.getPeriodStart(),
                e.getPeriodEnd(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getVersion(),
                e.getNotes(),
                e.getTerms(),
                e.getStatus() == null ? InvoiceStatus.DRAFT : e.getStatus(),
                e.getIssuedAt(),
                e.getWrittenOffAt(),
                amountPaid,
                lines
        );
    }

    /**
     * Derives amountPaid from stored amount_due using the same total formula as
     * {@link Invoice#getTotal()} (subtotal of line totals + tax total).
     * When amount_due is null (legacy rows), assume nothing paid.
     */
    private Money resolveAmountPaid(InvoiceEntity e, List<InvoiceLine> lines) {
        String currency = e.getCurrencyCode();
        Money total;
        if (lines.isEmpty() && e.getTotal() != null) {
            // List views may omit lines; use denormalized total column
            total = Money.of(e.getTotal(), currency);
        } else {
            Money subtotal = Money.of(BigDecimal.ZERO, currency);
            Money taxTotal = Money.of(BigDecimal.ZERO, currency);
            for (InvoiceLine l : lines) {
                subtotal = subtotal.add(l.getLineTotal());
                taxTotal = taxTotal.add(l.getTaxAmount());
            }
            total = subtotal.add(taxTotal);
        }
        if (e.getAmountDue() == null) {
            return Money.of(BigDecimal.ZERO, currency);
        }
        BigDecimal paid = total.getAmount().subtract(e.getAmountDue());
        if (paid.signum() < 0) {
            paid = BigDecimal.ZERO;
        }
        if (paid.compareTo(total.getAmount()) > 0) {
            paid = total.getAmount();
        }
        return Money.of(paid, currency);
    }
}
