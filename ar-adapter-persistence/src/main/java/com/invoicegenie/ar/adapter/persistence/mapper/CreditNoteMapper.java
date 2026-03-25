package com.invoicegenie.ar.adapter.persistence.mapper;

import com.invoicegenie.ar.adapter.persistence.entity.CreditNoteEntity;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.payment.CreditNote;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

/**
 * Maps CreditNote aggregate ↔ JPA entity.
 */
public final class CreditNoteMapper {

    public CreditNoteEntity toEntity(TenantId tenantId, CreditNote creditNote) {
        CreditNoteEntity e = new CreditNoteEntity();
        e.setId(creditNote.getId());
        e.setTenantId(tenantId.getValue());
        e.setCreditNoteNumber(creditNote.getCreditNoteNumber());
        e.setCustomerId(creditNote.getCustomerId().getValue());
        e.setAmount(creditNote.getAmount().getAmount());
        e.setCurrency(creditNote.getAmount().getCurrencyCode());
        e.setType(toEntityType(creditNote.getType()));
        e.setReferenceInvoiceId(creditNote.getReferenceInvoiceId());
        e.setDescription(creditNote.getDescription());
        e.setStatus(toEntityStatus(creditNote.getStatus()));
        e.setIssueDate(creditNote.getIssueDate());
        e.setAppliedDate(creditNote.getAppliedDate());
        e.setExpiryDate(creditNote.getExpiryDate());
        e.setAppliedToPaymentId(creditNote.getAppliedToPaymentId());
        e.setNotes(creditNote.getNotes());
        e.setCreatedAt(creditNote.getCreatedAt());
        e.setUpdatedAt(creditNote.getUpdatedAt());
        return e;
    }

    public CreditNote toDomain(CreditNoteEntity e) {
        return new CreditNote(
                e.getId(),
                e.getCreditNoteNumber(),
                CustomerId.of(e.getCustomerId()),
                Money.of(e.getAmount().toString(), e.getCurrency()),
                toDomainType(e.getType()),
                e.getReferenceInvoiceId(),
                e.getDescription(),
                toDomainStatus(e.getStatus()),
                e.getIssueDate(),
                e.getAppliedDate(),
                e.getExpiryDate(),
                e.getAppliedToPaymentId(),
                e.getNotes(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }

    private CreditNoteEntity.CreditNoteType toEntityType(CreditNote.CreditNoteType type) {
        return switch (type) {
            case EARLY_PAYMENT_DISCOUNT -> CreditNoteEntity.CreditNoteType.EARLY_PAYMENT_DISCOUNT;
            case ADJUSTMENT -> CreditNoteEntity.CreditNoteType.ADJUSTMENT;
            case REFUND -> CreditNoteEntity.CreditNoteType.REFUND;
        };
    }

    private CreditNote.CreditNoteType toDomainType(CreditNoteEntity.CreditNoteType type) {
        return switch (type) {
            case EARLY_PAYMENT_DISCOUNT -> CreditNote.CreditNoteType.EARLY_PAYMENT_DISCOUNT;
            case ADJUSTMENT -> CreditNote.CreditNoteType.ADJUSTMENT;
            case REFUND -> CreditNote.CreditNoteType.REFUND;
        };
    }

    private CreditNoteEntity.CreditNoteStatus toEntityStatus(CreditNote.CreditNoteStatus status) {
        return switch (status) {
            case ISSUED -> CreditNoteEntity.CreditNoteStatus.ISSUED;
            case APPLIED -> CreditNoteEntity.CreditNoteStatus.APPLIED;
            case EXPIRED -> CreditNoteEntity.CreditNoteStatus.EXPIRED;
            case VOIDED -> CreditNoteEntity.CreditNoteStatus.VOIDED;
        };
    }

    private CreditNote.CreditNoteStatus toDomainStatus(CreditNoteEntity.CreditNoteStatus status) {
        return switch (status) {
            case ISSUED -> CreditNote.CreditNoteStatus.ISSUED;
            case APPLIED -> CreditNote.CreditNoteStatus.APPLIED;
            case EXPIRED -> CreditNote.CreditNoteStatus.EXPIRED;
            case VOIDED -> CreditNote.CreditNoteStatus.VOIDED;
        };
    }
}
