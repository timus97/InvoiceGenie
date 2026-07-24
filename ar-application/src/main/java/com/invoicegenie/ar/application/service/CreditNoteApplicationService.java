package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.CreditNoteUseCase;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.domain.model.ledger.LedgerRepository;
import com.invoicegenie.ar.domain.model.payment.CreditNote;
import com.invoicegenie.ar.domain.model.payment.CreditNoteRepository;
import com.invoicegenie.ar.domain.service.CreditNoteService;
import com.invoicegenie.ar.domain.service.LedgerService;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service: credit note use cases.
 * Apply reduces AR via invoice amountPaid + credit-memo ledger (Dr REVENUE / Cr AR).
 */
public class CreditNoteApplicationService implements CreditNoteUseCase {

    private final CreditNoteService creditNoteService;
    private final CreditNoteRepository creditNoteRepository;
    private final InvoiceRepository invoiceRepository;
    private final LedgerService ledgerService;
    private final LedgerRepository ledgerRepository;

    public CreditNoteApplicationService(CreditNoteService creditNoteService,
                                        CreditNoteRepository creditNoteRepository) {
        this(creditNoteService, creditNoteRepository, null, null, null);
    }

    public CreditNoteApplicationService(CreditNoteService creditNoteService,
                                        CreditNoteRepository creditNoteRepository,
                                        InvoiceRepository invoiceRepository,
                                        LedgerService ledgerService,
                                        LedgerRepository ledgerRepository) {
        this.creditNoteService = creditNoteService;
        this.creditNoteRepository = creditNoteRepository;
        this.invoiceRepository = invoiceRepository;
        this.ledgerService = ledgerService != null ? ledgerService : new LedgerService();
        this.ledgerRepository = ledgerRepository;
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
                    // Full-apply only: reduce open AR on reference invoice (or first open)
                    if (invoiceRepository != null) {
                        applyToInvoice(tenantId, creditNote);
                    }
                    creditNote.apply(paymentId);
                    creditNoteRepository.save(tenantId, creditNote);

                    if (ledgerRepository != null) {
                        LedgerService.TransactionResult tx = ledgerService.recordCreditNoteApplied(
                                tenantId, creditNote.getId(), creditNote.getCreditNoteNumber(), creditNote.getAmount());
                        ledgerService.assertBalanced(tx.entries());
                        ledgerRepository.saveAll(tenantId, tx.entries());
                    }
                    return creditNote;
                });
    }

    private void applyToInvoice(TenantId tenantId, CreditNote creditNote) {
        Invoice target = null;
        if (creditNote.getReferenceInvoiceId() != null) {
            target = invoiceRepository.findByTenantAndId(tenantId,
                    InvoiceId.of(creditNote.getReferenceInvoiceId())).orElse(null);
        }
        if (target == null || !target.canReceivePayments()) {
            List<Invoice> open = invoiceRepository.findOpenByTenantAndCustomer(
                    tenantId, creditNote.getCustomerId());
            target = open.stream()
                    .filter(inv -> creditNote.getAmount().getCurrencyCode().equalsIgnoreCase(inv.getCurrencyCode()))
                    .filter(Invoice::canReceivePayments)
                    .findFirst()
                    .orElse(null);
        }
        if (target == null) {
            throw new IllegalStateException("No open invoice to apply credit note "
                    + creditNote.getCreditNoteNumber());
        }
        Money applyAmount = creditNote.getAmount();
        if (applyAmount.getAmount().compareTo(target.getBalanceDue().getAmount()) > 0) {
            applyAmount = target.getBalanceDue();
        }
        if (applyAmount.getAmount().signum() <= 0) {
            throw new IllegalStateException("Invoice has no open balance for credit note apply");
        }
        target.recordPaymentApplied(applyAmount);
        invoiceRepository.save(tenantId, target);
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
