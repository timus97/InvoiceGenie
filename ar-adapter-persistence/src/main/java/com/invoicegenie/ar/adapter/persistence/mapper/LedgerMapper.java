package com.invoicegenie.ar.adapter.persistence.mapper;

import com.invoicegenie.ar.adapter.persistence.entity.LedgerEntryEntity;
import com.invoicegenie.ar.domain.model.ledger.Account;
import com.invoicegenie.ar.domain.model.ledger.EntryType;
import com.invoicegenie.ar.domain.model.ledger.LedgerEntry;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Maps domain {@link LedgerEntry} ↔ {@link LedgerEntryEntity}.
 */
public final class LedgerMapper {

    public LedgerEntryEntity toEntity(TenantId tenantId, LedgerEntry entry) {
        LedgerEntryEntity e = new LedgerEntryEntity();
        e.setId(entry.getId());
        e.setTenantId(tenantId.getValue());
        e.setEntryNumber("LE-" + entry.getId().toString().substring(0, 8));
        LocalDate date = entry.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
        e.setEntryDate(date);
        e.setPostingDate(date);
        e.setAccount(entry.getAccount().name());
        e.setCurrency(entry.getAmount().getCurrencyCode());
        e.setDescription(entry.getDescription());
        e.setReference(entry.getReferenceType());
        e.setTransactionId(entry.getTransactionId());
        e.setReferenceType(entry.getReferenceType());
        e.setReferenceId(entry.getReferenceId());
        e.setCreatedAt(entry.getCreatedAt());

        if (entry.getEntryType() == EntryType.DEBIT) {
            e.setDebit(entry.getAmount().getAmount());
            e.setCredit(BigDecimal.ZERO.setScale(2));
        } else {
            e.setDebit(BigDecimal.ZERO.setScale(2));
            e.setCredit(entry.getAmount().getAmount());
        }

        if (entry.getReferenceId() != null && entry.getReferenceType() != null) {
            String type = entry.getReferenceType().toUpperCase();
            if (type.contains("INVOICE")) {
                e.setInvoiceId(entry.getReferenceId());
            } else if (type.contains("PAYMENT")) {
                e.setPaymentId(entry.getReferenceId());
            }
        }
        return e;
    }

    public LedgerEntry toDomain(LedgerEntryEntity e) {
        EntryType entryType;
        BigDecimal amount;
        if (e.getDebit() != null && e.getDebit().signum() > 0) {
            entryType = EntryType.DEBIT;
            amount = e.getDebit();
        } else {
            entryType = EntryType.CREDIT;
            amount = e.getCredit() != null ? e.getCredit() : BigDecimal.ZERO;
        }

        return new LedgerEntry(
                e.getId(),
                Account.valueOf(e.getAccount()),
                Money.of(amount, e.getCurrency()),
                entryType,
                e.getDescription(),
                e.getTransactionId(),
                e.getReferenceType() != null ? e.getReferenceType() : e.getReference(),
                e.getReferenceId(),
                e.getCreatedAt()
        );
    }
}
