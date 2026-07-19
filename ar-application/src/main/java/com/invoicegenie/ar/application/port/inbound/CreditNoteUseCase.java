package com.invoicegenie.ar.application.port.inbound;

import com.invoicegenie.ar.domain.model.payment.CreditNote;
import com.invoicegenie.ar.domain.service.CreditNoteService;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Inbound port: credit note operations.
 */
public interface CreditNoteUseCase {

    CreditNoteService.CreditNoteResult generateEarlyPaymentDiscount(
            TenantId tenantId, UUID customerId, Money discountAmount, UUID referenceInvoiceId);

    Optional<CreditNote> apply(TenantId tenantId, UUID creditNoteId, UUID paymentId);

    Optional<CreditNote> get(TenantId tenantId, UUID creditNoteId);

    ListResult list(TenantId tenantId, String status);

    record ListResult(List<CreditNote> creditNotes, boolean success, String errorMessage) {
        public static ListResult ok(List<CreditNote> creditNotes) {
            return new ListResult(creditNotes, true, null);
        }

        public static ListResult invalidStatus(String message) {
            return new ListResult(List.of(), false, message);
        }
    }
}
