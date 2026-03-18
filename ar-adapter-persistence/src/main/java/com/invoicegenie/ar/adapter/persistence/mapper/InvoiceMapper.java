package com.invoicegenie.ar.adapter.persistence.mapper;

import com.invoicegenie.ar.adapter.persistence.entity.InvoiceEntity;
import com.invoicegenie.ar.adapter.persistence.entity.InvoiceLineEntity;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceLine;
import com.invoicegenie.ar.domain.model.invoice.InvoiceStatus;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

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
        e.setCancelledAt(invoice.getCancelledAt());
        e.setVersion(invoice.getVersion());
        e.setStatus(invoice.getStatus());
        e.setSubtotal(invoice.getSubtotal().getAmount());
        e.setTaxTotal(invoice.getTaxTotal().getAmount());
        e.setDiscountTotal(invoice.getDiscountTotal().getAmount());
        e.setTotal(invoice.getTotal().getAmount());
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

        return new Invoice(
                InvoiceId.of(e.getId()),
                e.getInvoiceNumber(),
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
                e.getCancelledAt(),
                lines
        );
    }
}
