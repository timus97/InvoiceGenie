package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.ChequeUseCase;
import com.invoicegenie.ar.application.port.inbound.InvoiceLifecycleUseCase;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.ledger.LedgerRepository;
import com.invoicegenie.ar.domain.model.payment.Cheque;
import com.invoicegenie.ar.domain.model.payment.ChequeRepository;
import com.invoicegenie.ar.domain.model.payment.ChequeStatus;
import com.invoicegenie.ar.domain.service.ChequeService;
import com.invoicegenie.shared.domain.TenantId;

import java.util.Optional;
import java.util.UUID;

/**
 * Application service: cheque lifecycle use cases.
 *
 * <p>On clear/bounce, persists durable ledger entries. On bounce, reopens affected invoices.
 */
public class ChequeApplicationService implements ChequeUseCase {

    private final ChequeService chequeService;
    private final ChequeRepository chequeRepository;
    private final InvoiceLifecycleUseCase invoiceLifecycleUseCase;
    private final LedgerRepository ledgerRepository;

    public ChequeApplicationService(ChequeService chequeService,
                                    ChequeRepository chequeRepository,
                                    InvoiceLifecycleUseCase invoiceLifecycleUseCase,
                                    LedgerRepository ledgerRepository) {
        this.chequeService = chequeService;
        this.chequeRepository = chequeRepository;
        this.invoiceLifecycleUseCase = invoiceLifecycleUseCase;
        this.ledgerRepository = ledgerRepository;
    }

    @Override
    public Cheque create(TenantId tenantId, CreateChequeCommand command) {
        Cheque cheque = new Cheque(
                UUID.randomUUID(),
                command.chequeNumber(),
                new CustomerId(UUID.fromString(command.customerId())),
                command.amount(),
                command.bankName(),
                command.bankBranch(),
                command.chequeDate(),
                command.notes()
        );
        chequeRepository.save(tenantId, cheque);
        return cheque;
    }

    @Override
    public Optional<ChequeService.DepositResult> deposit(TenantId tenantId, UUID chequeId) {
        return chequeRepository.findByTenantAndId(tenantId, chequeId)
                .map(cheque -> {
                    ChequeService.DepositResult result = chequeService.deposit(tenantId, cheque);
                    if (result.success()) {
                        chequeRepository.save(tenantId, cheque);
                    }
                    return result;
                });
    }

    @Override
    public Optional<ChequeService.ClearResult> clear(TenantId tenantId, UUID chequeId) {
        return chequeRepository.findByTenantAndId(tenantId, chequeId)
                .map(cheque -> {
                    ChequeService.ClearResult result = chequeService.clear(tenantId, cheque);
                    if (result.success()) {
                        chequeRepository.save(tenantId, cheque);
                        if (result.ledgerEntries() != null && !result.ledgerEntries().isEmpty()) {
                            ledgerRepository.saveAll(tenantId, result.ledgerEntries());
                        }
                    }
                    return result;
                });
    }

    @Override
    public Optional<ChequeService.BounceResult> bounce(TenantId tenantId, UUID chequeId, String reason) {
        return chequeRepository.findByTenantAndId(tenantId, chequeId)
                .map(cheque -> {
                    ChequeService.BounceResult result = chequeService.bounce(tenantId, cheque, reason);
                    if (result.success()) {
                        chequeRepository.save(tenantId, cheque);
                        if (result.reverseEntries() != null && !result.reverseEntries().isEmpty()) {
                            ledgerRepository.saveAll(tenantId, result.reverseEntries());
                        }
                        for (UUID invoiceId : result.affectedInvoiceIds()) {
                            invoiceLifecycleUseCase.reopen(
                                    tenantId,
                                    new InvoiceId(invoiceId),
                                    "Cheque " + cheque.getChequeNumber() + " bounced: " + reason);
                        }
                    }
                    return result;
                });
    }

    @Override
    public Optional<Cheque> get(TenantId tenantId, UUID chequeId) {
        return chequeRepository.findByTenantAndId(tenantId, chequeId);
    }

    @Override
    public ListResult list(TenantId tenantId, String status) {
        if (status != null) {
            try {
                ChequeStatus chequeStatus = ChequeStatus.valueOf(status.toUpperCase());
                return ListResult.ok(chequeRepository.findByTenantAndStatus(tenantId, chequeStatus));
            } catch (IllegalArgumentException e) {
                return ListResult.invalidStatus("Unknown status: " + status);
            }
        }
        // No full-list endpoint on repository — return empty when unfiltered
        return ListResult.ok(java.util.List.of());
    }
}
