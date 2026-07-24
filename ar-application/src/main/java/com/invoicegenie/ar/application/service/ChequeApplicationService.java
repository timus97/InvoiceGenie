package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.ChequeUseCase;
import com.invoicegenie.ar.application.port.inbound.InvoiceLifecycleUseCase;
import com.invoicegenie.ar.application.port.inbound.PaymentAllocationUseCase;
import com.invoicegenie.ar.application.port.inbound.RecordPaymentUseCase;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.domain.model.ledger.LedgerRepository;
import com.invoicegenie.ar.domain.model.payment.Cheque;
import com.invoicegenie.ar.domain.model.payment.ChequeRepository;
import com.invoicegenie.ar.domain.model.payment.ChequeStatus;
import com.invoicegenie.ar.domain.model.payment.Payment;
import com.invoicegenie.ar.domain.model.payment.PaymentId;
import com.invoicegenie.ar.domain.model.payment.PaymentMethod;
import com.invoicegenie.ar.domain.model.payment.PaymentRepository;
import com.invoicegenie.ar.domain.service.ChequeService;
import com.invoicegenie.ar.domain.service.LedgerService;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service: cheque lifecycle use cases.
 *
 * <p>Clear creates a linked CHECK payment (ledger via payment receive) and allocates
 * to invoices. Bounce from DEPOSITED is status-only; bounce from CLEARED reverses
 * allocations, payment, and ledger once.
 */
public class ChequeApplicationService implements ChequeUseCase {

    private final ChequeService chequeService;
    private final ChequeRepository chequeRepository;
    private final InvoiceLifecycleUseCase invoiceLifecycleUseCase;
    private final LedgerRepository ledgerRepository;
    private final RecordPaymentUseCase recordPaymentUseCase;
    private final PaymentAllocationUseCase paymentAllocationUseCase;
    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final LedgerService ledgerService;

    public ChequeApplicationService(ChequeService chequeService,
                                    ChequeRepository chequeRepository,
                                    InvoiceLifecycleUseCase invoiceLifecycleUseCase,
                                    LedgerRepository ledgerRepository) {
        this(chequeService, chequeRepository, invoiceLifecycleUseCase, ledgerRepository,
                null, null, null, null, null);
    }

    public ChequeApplicationService(ChequeService chequeService,
                                    ChequeRepository chequeRepository,
                                    InvoiceLifecycleUseCase invoiceLifecycleUseCase,
                                    LedgerRepository ledgerRepository,
                                    RecordPaymentUseCase recordPaymentUseCase,
                                    PaymentAllocationUseCase paymentAllocationUseCase,
                                    PaymentRepository paymentRepository,
                                    InvoiceRepository invoiceRepository,
                                    LedgerService ledgerService) {
        this.chequeService = chequeService;
        this.chequeRepository = chequeRepository;
        this.invoiceLifecycleUseCase = invoiceLifecycleUseCase;
        this.ledgerRepository = ledgerRepository;
        this.recordPaymentUseCase = recordPaymentUseCase;
        this.paymentAllocationUseCase = paymentAllocationUseCase;
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.ledgerService = ledgerService != null ? ledgerService : new LedgerService();
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
        if (command.invoiceIds() != null) {
            for (UUID invoiceId : command.invoiceIds()) {
                if (invoiceId != null) {
                    cheque.addAllocatedInvoice(invoiceId);
                }
            }
        }
        chequeRepository.save(tenantId, cheque);
        return cheque;
    }

    @Override
    public List<Cheque> bulkCreate(TenantId tenantId, List<CreateChequeCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return List.of();
        }
        List<Cheque> created = new ArrayList<>();
        for (CreateChequeCommand command : commands) {
            created.add(create(tenantId, command));
        }
        return created;
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
        return clear(tenantId, chequeId, null);
    }

    @Override
    public Optional<ChequeService.ClearResult> clear(TenantId tenantId, UUID chequeId, List<UUID> invoiceIds) {
        return chequeRepository.findByTenantAndId(tenantId, chequeId)
                .map(cheque -> {
                    // Idempotent: already cleared with payment linked
                    if (cheque.getStatus() == ChequeStatus.CLEARED && cheque.getPaymentId() != null) {
                        return new ChequeService.ClearResult(cheque, cheque.getPaymentId(), List.of(), true,
                                "Cheque already cleared");
                    }

                    ChequeService.ClearResult statusResult = chequeService.clearStatusOnly(tenantId, cheque);
                    if (!statusResult.success()) {
                        return statusResult;
                    }

                    UUID paymentId = null;
                    List<UUID> allocated = new ArrayList<>();

                    if (recordPaymentUseCase != null && paymentAllocationUseCase != null) {
                        String paymentNumber = "PAY-CHQ-" + cheque.getChequeNumber() + "-"
                                + cheque.getId().toString().substring(0, 8);
                        PaymentId pid = recordPaymentUseCase.record(tenantId,
                                new RecordPaymentUseCase.RecordPaymentCommand(
                                        paymentNumber,
                                        cheque.getCustomerId().getValue().toString(),
                                        cheque.getAmount().getAmount(),
                                        cheque.getAmount().getCurrencyCode(),
                                        LocalDate.now(),
                                        PaymentMethod.CHECK,
                                        "Cheque " + cheque.getChequeNumber(),
                                        "Auto-created on cheque clear"
                                ),
                                "cheque-clear:" + cheque.getId());
                        paymentId = pid.getValue();
                        cheque.linkPayment(paymentId);

                        List<UUID> targetInvoices = resolveInvoiceTargets(cheque, invoiceIds);
                        if (!targetInvoices.isEmpty()) {
                            List<PaymentAllocationUseCase.ManualAllocationRequest> requests = new ArrayList<>();
                            Money remaining = cheque.getAmount();
                            for (UUID invId : targetInvoices) {
                                if (remaining.getAmount().signum() <= 0) {
                                    break;
                                }
                                Optional<Invoice> invOpt = invoiceRepository != null
                                        ? invoiceRepository.findByTenantAndId(tenantId, InvoiceId.of(invId))
                                        : Optional.empty();
                                if (invOpt.isEmpty() || !invOpt.get().canReceivePayments()) {
                                    continue;
                                }
                                Invoice inv = invOpt.get();
                                if (!inv.getCurrencyCode().equalsIgnoreCase(cheque.getAmount().getCurrencyCode())) {
                                    continue;
                                }
                                Money apply = inv.getBalanceDue().getAmount().compareTo(remaining.getAmount()) <= 0
                                        ? inv.getBalanceDue()
                                        : remaining;
                                if (apply.getAmount().signum() <= 0) {
                                    continue;
                                }
                                requests.add(new PaymentAllocationUseCase.ManualAllocationRequest(
                                        inv.getId(), apply, "Cheque " + cheque.getChequeNumber()));
                                remaining = remaining.subtract(apply);
                                allocated.add(inv.getId().getValue());
                                cheque.addAllocatedInvoice(inv.getId().getValue());
                            }
                            if (!requests.isEmpty()) {
                                paymentAllocationUseCase.manualAllocate(
                                        tenantId, pid, requests, UUID.randomUUID(),
                                        "cheque-alloc:" + cheque.getId());
                            }
                        } else {
                            // FIFO across customer open invoices
                            paymentAllocationUseCase.autoAllocateFIFO(
                                    tenantId, pid, UUID.randomUUID(), "cheque-fifo:" + cheque.getId())
                                    .ifPresent(result -> {
                                        result.allocations().forEach(a ->
                                                cheque.addAllocatedInvoice(a.invoiceId().getValue()));
                                    });
                            allocated.addAll(cheque.getAllocatedInvoiceIds());
                        }
                    }

                    chequeRepository.save(tenantId, cheque);
                    return new ChequeService.ClearResult(cheque, paymentId != null ? paymentId : cheque.getPaymentId(),
                            List.of(), true, "Cheque cleared successfully");
                });
    }

    private List<UUID> resolveInvoiceTargets(Cheque cheque, List<UUID> invoiceIds) {
        if (invoiceIds != null && !invoiceIds.isEmpty()) {
            return invoiceIds.stream().filter(id -> id != null).toList();
        }
        if (!cheque.getAllocatedInvoiceIds().isEmpty()) {
            return new ArrayList<>(cheque.getAllocatedInvoiceIds());
        }
        return List.of();
    }

    @Override
    public Optional<ChequeService.BounceResult> bounce(TenantId tenantId, UUID chequeId, String reason) {
        return chequeRepository.findByTenantAndId(tenantId, chequeId)
                .map(cheque -> {
                    ChequeStatus prior = cheque.getStatus();
                    boolean wasCleared = prior == ChequeStatus.CLEARED || cheque.getPaymentId() != null;

                    // Status transition first
                    ChequeService.BounceResult statusResult = chequeService.bounceStatusOnly(tenantId, cheque, reason);
                    if (!statusResult.success()) {
                        return statusResult;
                    }

                    List<UUID> affected = new ArrayList<>();
                    List<com.invoicegenie.ar.domain.model.ledger.LedgerEntry> reverseEntries = new ArrayList<>();

                    if (wasCleared && paymentRepository != null && cheque.getPaymentId() != null) {
                        PaymentId paymentId = PaymentId.of(cheque.getPaymentId());
                        Optional<Payment> paymentOpt = paymentRepository.findByTenantAndId(tenantId, paymentId);
                        if (paymentOpt.isPresent()) {
                            Payment payment = paymentOpt.get();
                            // Reverse invoice amountPaid for each allocation
                            for (var allocation : payment.getAllocations()) {
                                UUID invId = allocation.getInvoiceId().getValue();
                                affected.add(invId);
                                if (invoiceRepository != null) {
                                    invoiceRepository.findByTenantAndId(tenantId, allocation.getInvoiceId())
                                            .ifPresent(inv -> {
                                                inv.reverseAllocation(allocation.getAmount());
                                                inv.refreshStatusAfterReversal();
                                                invoiceRepository.save(tenantId, inv);
                                            });
                                } else if (invoiceLifecycleUseCase != null) {
                                    invoiceLifecycleUseCase.reopen(tenantId, new InvoiceId(invId),
                                            "Cheque " + cheque.getChequeNumber() + " bounced: " + reason);
                                }
                            }
                            if (payment.getStatus() == com.invoicegenie.ar.domain.model.payment.PaymentStatus.RECEIVED) {
                                payment.reverse();
                                paymentRepository.save(tenantId, payment);
                            }
                            // Reverse cash application once: Dr AR / Cr Bank
                            LedgerService.TransactionResult rev = ledgerService.recordPaymentReversal(
                                    tenantId, paymentId.getValue(), payment.getPaymentNumber(), payment.getAmount());
                            ledgerService.assertBalanced(rev.entries());
                            ledgerRepository.saveAll(tenantId, rev.entries());
                            reverseEntries.addAll(rev.entries());
                        }
                    }
                    // DEPOSITED-only bounce: status change only, no cash reverse

                    chequeRepository.save(tenantId, cheque);
                    return new ChequeService.BounceResult(cheque, reverseEntries, affected, true,
                            "Cheque bounced: " + reason);
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
        return ListResult.ok(chequeRepository.findByTenant(tenantId));
    }
}
