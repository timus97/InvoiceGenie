package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.CreditNoteUseCase;
import com.invoicegenie.ar.domain.model.payment.CreditNote;
import com.invoicegenie.ar.domain.model.payment.CreditNoteRepository;
import com.invoicegenie.ar.domain.service.CreditNoteService;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service: credit note use cases.
 */
public class CreditNoteApplicationService implements CreditNoteUseCase {

    private final CreditNoteService creditNoteService;
    private final CreditNoteRepository creditNoteRepository;

    public CreditNoteApplicationService(CreditNoteService creditNoteService,
                                        CreditNoteRepository creditNoteRepository) {
        this.creditNoteService = creditNoteService;
        this.creditNoteRepository = creditNoteRepository;
    }

    @Override
    public CreditNoteService.CreditNoteResult generateEarlyPaymentDiscount(
            TenantId tenantId, UUID customerId, Money discountAmount, UUID referenceInvoiceId) {
        CreditNoteService.CreditNoteResult result = creditNoteService.generateEarlyPaymentDiscount(
                tenantId, customerId, discountAmount, referenceInvoiceId);
        if (result.success()) {
            creditNoteRepository.save(tenantId, result.creditNote());
        }
        return result;
    }

    @Override
    public Optional<CreditNote> apply(TenantId tenantId, UUID creditNoteId, UUID paymentId) {
        return creditNoteRepository.findByTenantAndId(tenantId, creditNoteId)
                .map(creditNote -> {
                    creditNote.apply(paymentId);
                    creditNoteRepository.save(tenantId, creditNote);
                    return creditNote;
                });
    }

    @Override
    public Optional<CreditNote> get(TenantId tenantId, UUID creditNoteId) {
        return creditNoteRepository.findByTenantAndId(tenantId, creditNoteId);
    }

    @Override
    public ListResult list(TenantId tenantId, String status) {
        if (status != null) {
            try {
                CreditNote.CreditNoteStatus creditNoteStatus =
                        CreditNote.CreditNoteStatus.valueOf(status.toUpperCase());
                return ListResult.ok(creditNoteRepository.findByTenantAndStatus(tenantId, creditNoteStatus));
            } catch (IllegalArgumentException e) {
                return ListResult.invalidStatus("Unknown status: " + status);
            }
        }
        return ListResult.ok(creditNoteRepository.findByTenant(tenantId));
    }
}
