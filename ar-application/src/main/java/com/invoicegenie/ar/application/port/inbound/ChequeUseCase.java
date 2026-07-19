package com.invoicegenie.ar.application.port.inbound;

import com.invoicegenie.ar.domain.model.payment.Cheque;
import com.invoicegenie.ar.domain.service.ChequeService;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Inbound port: cheque lifecycle operations.
 */
public interface ChequeUseCase {

    Cheque create(TenantId tenantId, CreateChequeCommand command);

    Optional<ChequeService.DepositResult> deposit(TenantId tenantId, UUID chequeId);

    Optional<ChequeService.ClearResult> clear(TenantId tenantId, UUID chequeId);

    /**
     * Bounces a cheque and reopens affected invoices via {@link InvoiceLifecycleUseCase}.
     */
    Optional<ChequeService.BounceResult> bounce(TenantId tenantId, UUID chequeId, String reason);

    Optional<Cheque> get(TenantId tenantId, UUID chequeId);

    ListResult list(TenantId tenantId, String status);

    record CreateChequeCommand(
            String chequeNumber,
            String customerId,
            Money amount,
            String bankName,
            String bankBranch,
            LocalDate chequeDate,
            String notes
    ) {}

    record ListResult(List<Cheque> cheques, boolean success, String errorMessage) {
        public static ListResult ok(List<Cheque> cheques) {
            return new ListResult(cheques, true, null);
        }

        public static ListResult invalidStatus(String message) {
            return new ListResult(List.of(), false, message);
        }
    }
}
