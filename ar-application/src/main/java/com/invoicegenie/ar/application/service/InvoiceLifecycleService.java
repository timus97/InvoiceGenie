package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.ApplyInvoicePaymentUseCase;
import com.invoicegenie.ar.application.port.inbound.InvoiceLifecycleUseCase;
import com.invoicegenie.ar.domain.exception.CustomerNotInvoiceableException;
import com.invoicegenie.ar.domain.model.customer.Customer;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.customer.CustomerRepository;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.domain.model.invoice.InvoiceVersionRepository;
import com.invoicegenie.ar.domain.service.CustomerService;
import com.invoicegenie.ar.domain.service.InvoiceSnapshotService;
import com.invoicegenie.ar.domain.model.ledger.LedgerRepository;
import com.invoicegenie.ar.domain.model.outbox.AuditEntry;
import com.invoicegenie.ar.domain.model.outbox.AuditRepository;
import com.invoicegenie.ar.domain.service.LedgerService;
import com.invoicegenie.shared.domain.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Application service: invoice lifecycle operations.
 */
public class InvoiceLifecycleService implements InvoiceLifecycleUseCase {

    private final InvoiceRepository invoiceRepository;
    private final AuditRepository auditRepository;
    private final ApplyInvoicePaymentUseCase applyInvoicePaymentUseCase;
    private final LedgerService ledgerService;
    private final LedgerRepository ledgerRepository;
    private final InvoiceVersionRepository invoiceVersionRepository;
    private final CustomerRepository customerRepository;
    private final CustomerService customerService;

    public InvoiceLifecycleService(InvoiceRepository invoiceRepository,
                                   AuditRepository auditRepository,
                                   ApplyInvoicePaymentUseCase applyInvoicePaymentUseCase,
                                   LedgerService ledgerService,
                                   LedgerRepository ledgerRepository,
                                   InvoiceVersionRepository invoiceVersionRepository) {
        this(invoiceRepository, auditRepository, applyInvoicePaymentUseCase, ledgerService,
                ledgerRepository, invoiceVersionRepository, null, null);
    }

    public InvoiceLifecycleService(InvoiceRepository invoiceRepository,
                                   AuditRepository auditRepository,
                                   ApplyInvoicePaymentUseCase applyInvoicePaymentUseCase,
                                   LedgerService ledgerService,
                                   LedgerRepository ledgerRepository,
                                   InvoiceVersionRepository invoiceVersionRepository,
                                   CustomerRepository customerRepository,
                                   CustomerService customerService) {
        this.invoiceRepository = invoiceRepository;
        this.auditRepository = auditRepository;
        this.applyInvoicePaymentUseCase = applyInvoicePaymentUseCase;
        this.ledgerService = ledgerService;
        this.ledgerRepository = ledgerRepository;
        this.invoiceVersionRepository = invoiceVersionRepository;
        this.customerRepository = customerRepository;
        this.customerService = customerService != null ? customerService : new CustomerService();
    }

    private void snapshot(TenantId tenantId, Invoice inv, String reason) {
        invoiceVersionRepository.save(tenantId, InvoiceSnapshotService.snapshot(tenantId, inv, reason));
    }

    @Override
    public Optional<Invoice> issue(TenantId tenantId, InvoiceId invoiceId) {
        return invoiceRepository.findByTenantAndId(tenantId, invoiceId)
                .map(inv -> {
                    enforceCustomerCanBeIssued(tenantId, inv);
                    String before = String.format("{\"status\":\"%s\"}", inv.getStatus());
                    inv.issue();
                    invoiceRepository.save(tenantId, inv);
                    snapshot(tenantId, inv, "ISSUE");

                    // Durable ledger when issuing an existing draft
                    LedgerService.TransactionResult ledgerTx = ledgerService.recordInvoiceIssued(
                            tenantId, invoiceId.getValue(), inv.getInvoiceNumber(), inv.getTotal());
                    ledgerService.assertBalanced(ledgerTx.entries());
                    ledgerRepository.saveAll(tenantId, ledgerTx.entries());

                    String after = String.format("{\"status\":\"%s\"}", inv.getStatus());
                    auditRepository.save(tenantId, AuditEntry.transition(tenantId, "INVOICE", invoiceId.getValue(),
                            inv.getInvoiceNumber(), null, "ISSUE", before, after));
                    return inv;
                });
    }

    private void enforceCustomerCanBeIssued(TenantId tenantId, Invoice inv) {
        if (customerRepository == null || inv.getCustomerId() == null) {
            return;
        }
        CustomerId customerId = inv.getCustomerId();
        Customer customer = customerRepository.findByTenantAndId(tenantId, customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId.getValue()));
        if (!customer.canBeInvoiced()) {
            throw new CustomerNotInvoiceableException(
                    "Customer cannot be invoiced (status: " + customer.getStatus() + ")");
        }
        BigDecimal outstanding = invoiceRepository.findOpenByTenantAndCustomer(tenantId, customerId).stream()
                .filter(open -> inv.getCurrencyCode().equalsIgnoreCase(open.getCurrencyCode()))
                .map(open -> open.getBalanceDue().getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        CustomerService.CreditCheckResult check = customerService.checkCreditLimit(
                tenantId, customerRepository, customerId, outstanding, inv.getTotal().getAmount());
        if (!check.canInvoice()) {
            throw new CustomerNotInvoiceableException(check.message());
        }
    }

    @Override
    public Optional<Invoice> markOverdue(TenantId tenantId, InvoiceId invoiceId, LocalDate today) {
        return invoiceRepository.findByTenantAndId(tenantId, invoiceId)
                .map(inv -> {
                    String before = String.format("{\"status\":\"%s\"}", inv.getStatus());
                    inv.markOverdue(today);
                    invoiceRepository.save(tenantId, inv);
                    snapshot(tenantId, inv, "MARK_OVERDUE");
                    String after = String.format("{\"status\":\"%s\"}", inv.getStatus());
                    auditRepository.save(tenantId, AuditEntry.transition(tenantId, "INVOICE", invoiceId.getValue(),
                            inv.getInvoiceNumber(), null, "MARK_OVERDUE", before, after));
                    return inv;
                });
    }

    @Override
    public Optional<Invoice> writeOff(TenantId tenantId, InvoiceId invoiceId, String reason) {
        return invoiceRepository.findByTenantAndId(tenantId, invoiceId)
                .map(inv -> {
                    String before = String.format("{\"status\":\"%s\"}", inv.getStatus());
                    // Capture outstanding balance before write-off
                    var writeOffAmount = inv.getBalanceDue();
                    inv.writeOff(reason);
                    invoiceRepository.save(tenantId, inv);
                    snapshot(tenantId, inv, "WRITE_OFF");

                    // Durable ledger: Dr Bad Debt Expense / Cr AR
                    if (writeOffAmount.getAmount().signum() > 0) {
                        LedgerService.TransactionResult ledgerTx = ledgerService.recordWriteOff(
                                tenantId, invoiceId.getValue(), inv.getInvoiceNumber(), writeOffAmount);
                        ledgerService.assertBalanced(ledgerTx.entries());
                        ledgerRepository.saveAll(tenantId, ledgerTx.entries());
                    }

                    String after = String.format("{\"status\":\"%s\",\"reason\":\"%s\"}", inv.getStatus(), reason);
                    auditRepository.save(tenantId, AuditEntry.transition(tenantId, "INVOICE", invoiceId.getValue(),
                            inv.getInvoiceNumber(), null, "WRITE_OFF", before, after));
                    return inv;
                });
    }

    @Override
    public Optional<Invoice> applyPayment(TenantId tenantId, InvoiceId invoiceId, boolean fullyPaid) {
        // Creates a real Payment + allocates to this invoice (PaymentRecorded + PaymentAllocated)
        return applyInvoicePaymentUseCase.apply(tenantId, invoiceId,
                new ApplyInvoicePaymentUseCase.ApplyPaymentCommand(null, fullyPaid));
    }

    @Override
    public Optional<Invoice> updateDueDate(TenantId tenantId, InvoiceId invoiceId, LocalDate newDueDate) {
        return invoiceRepository.findByTenantAndId(tenantId, invoiceId)
                .map(inv -> {
                    String before = String.format("{\"dueDate\":\"%s\"}", inv.getDueDate());
                    inv.setDueDate(newDueDate);
                    invoiceRepository.save(tenantId, inv);
                    snapshot(tenantId, inv, "UPDATE_DUE_DATE");
                    String after = String.format("{\"dueDate\":\"%s\"}", inv.getDueDate());
                    auditRepository.save(tenantId, AuditEntry.transition(tenantId, "INVOICE", invoiceId.getValue(),
                            inv.getInvoiceNumber(), null, "UPDATE_DUE_DATE", before, after));
                    return inv;
                });
    }

    @Override
    public Optional<Invoice> reopen(TenantId tenantId, InvoiceId invoiceId, String reason) {
        return invoiceRepository.findByTenantAndId(tenantId, invoiceId)
                .map(inv -> {
                    String before = String.format("{\"status\":\"%s\"}", inv.getStatus());
                    inv.reopen(reason);
                    invoiceRepository.save(tenantId, inv);
                    snapshot(tenantId, inv, "REOPEN");
                    String after = String.format("{\"status\":\"%s\",\"reason\":\"%s\"}", inv.getStatus(), reason);
                    auditRepository.save(tenantId, AuditEntry.transition(tenantId, "INVOICE", invoiceId.getValue(),
                            inv.getInvoiceNumber(), null, "REOPEN", before, after));
                    return inv;
                });
    }
}
