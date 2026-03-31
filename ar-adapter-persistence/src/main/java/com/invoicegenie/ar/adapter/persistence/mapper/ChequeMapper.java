package com.invoicegenie.ar.adapter.persistence.mapper;

import com.invoicegenie.ar.adapter.persistence.entity.ChequeEntity;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.payment.Cheque;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps Cheque aggregate ↔ JPA entity.
 */
public final class ChequeMapper {

    public ChequeEntity toEntity(TenantId tenantId, Cheque cheque) {
        ChequeEntity e = new ChequeEntity();
        e.setId(cheque.getId());
        e.setTenantId(tenantId.getValue());
        e.setChequeNumber(cheque.getChequeNumber());
        e.setCustomerId(cheque.getCustomerId().getValue());
        e.setAmount(cheque.getAmount().getAmount());
        e.setCurrency(cheque.getAmount().getCurrencyCode());
        e.setBankName(cheque.getBankName());
        e.setBankBranch(cheque.getBankBranch());
        e.setChequeDate(cheque.getChequeDate());
        e.setReceivedDate(cheque.getReceivedDate());
        e.setDepositedDate(cheque.getDepositedDate());
        e.setClearedDate(cheque.getClearedDate());
        e.setBouncedDate(cheque.getBouncedDate());
        e.setBounceReason(cheque.getBounceReason());
        e.setStatus(cheque.getStatus());
        e.setPaymentId(cheque.getPaymentId());
        e.setNotes(cheque.getNotes());
        e.setCreatedAt(cheque.getCreatedAt());
        e.setUpdatedAt(cheque.getUpdatedAt());
        return e;
    }

    public Cheque toDomain(ChequeEntity e) {
        // Reconstruct allocated invoice IDs (empty list for now; could be stored separately)
        List<java.util.UUID> allocatedIds = new ArrayList<>();
        
        return new Cheque(
                e.getId(),
                e.getChequeNumber(),
                CustomerId.of(e.getCustomerId()),
                Money.of(e.getAmount().toString(), e.getCurrency()),
                e.getBankName(),
                e.getBankBranch(),
                e.getChequeDate(),
                e.getReceivedDate(),
                e.getDepositedDate(),
                e.getClearedDate(),
                e.getBouncedDate(),
                e.getBounceReason(),
                e.getStatus(),
                e.getPaymentId(),
                allocatedIds,
                e.getNotes(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
