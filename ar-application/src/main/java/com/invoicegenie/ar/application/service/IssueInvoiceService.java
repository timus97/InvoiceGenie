package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.IssueInvoiceUseCase;
import com.invoicegenie.ar.application.port.outbound.EventPublisher;
import com.invoicegenie.ar.application.port.outbound.IdGenerator;
import com.invoicegenie.ar.application.port.outbound.IdempotencyStore;
import com.invoicegenie.ar.domain.event.InvoiceIssued;
import com.invoicegenie.ar.domain.exception.CustomerNotInvoiceableException;
import com.invoicegenie.ar.domain.exception.IdempotencyConflictException;
import com.invoicegenie.ar.domain.model.customer.Customer;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.customer.CustomerRepository;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceLine;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.domain.model.invoice.InvoiceVersionRepository;
import com.invoicegenie.ar.domain.model.ledger.LedgerRepository;
import com.invoicegenie.ar.domain.service.CustomerService;
import com.invoicegenie.ar.domain.service.InvoiceSnapshotService;
import com.invoicegenie.ar.domain.model.outbox.AuditEntry;
import com.invoicegenie.ar.domain.model.outbox.AuditRepository;
import com.invoicegenie.ar.domain.service.LedgerService;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.IntStream;

/**
 * Application service: orchestrates domain and ports. No UI; no tenant resolution (receives TenantId).
 */
public class IssueInvoiceService implements IssueInvoiceUseCase {

    private static final Logger LOG = Logger.getLogger(IssueInvoiceService.class.getName());

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final IdGenerator idGenerator;
    private final EventPublisher eventPublisher;
    private final AuditRepository auditRepository;
    private final LedgerService ledgerService;
    private final LedgerRepository ledgerRepository;
    private final IdempotencyStore idempotencyStore;
    private final InvoiceVersionRepository invoiceVersionRepository;
    private final CustomerService customerService;

    public IssueInvoiceService(InvoiceRepository invoiceRepository,
                               CustomerRepository customerRepository,
                               IdGenerator idGenerator,
                               EventPublisher eventPublisher,
                               AuditRepository auditRepository,
                               LedgerService ledgerService,
                               LedgerRepository ledgerRepository,
                               IdempotencyStore idempotencyStore,
                               InvoiceVersionRepository invoiceVersionRepository) {
        this(invoiceRepository, customerRepository, idGenerator, eventPublisher, auditRepository,
                ledgerService, ledgerRepository, idempotencyStore, invoiceVersionRepository, new CustomerService());
    }

    public IssueInvoiceService(InvoiceRepository invoiceRepository,
                               CustomerRepository customerRepository,
                               IdGenerator idGenerator,
                               EventPublisher eventPublisher,
                               AuditRepository auditRepository,
                               LedgerService ledgerService,
                               LedgerRepository ledgerRepository,
                               IdempotencyStore idempotencyStore,
                               InvoiceVersionRepository invoiceVersionRepository,
                               CustomerService customerService) {
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.idGenerator = idGenerator;
        this.eventPublisher = eventPublisher;
        this.auditRepository = auditRepository;
        this.ledgerService = ledgerService;
        this.ledgerRepository = ledgerRepository;
        this.idempotencyStore = idempotencyStore;
        this.invoiceVersionRepository = invoiceVersionRepository;
        this.customerService = customerService != null ? customerService : new CustomerService();
    }

    @Override
    public InvoiceId issue(TenantId tenantId, IssueInvoiceCommand command) {
        return issue(tenantId, command, null);
    }

    @Override
    public InvoiceId issue(TenantId tenantId, IssueInvoiceCommand command, String idempotencyKey) {
        boolean issueNow = command.shouldIssueImmediately();
        String storeKey = null;
        String requestHash = null;
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            storeKey = (issueNow ? "invoice:" : "invoice-draft:") + idempotencyKey;
            requestHash = hashRequest(command);
            var existing = idempotencyStore.find(tenantId, storeKey);
            if (existing.isPresent()) {
                if (!existing.get().requestHash().equals(requestHash)) {
                    throw new IdempotencyConflictException(
                            "Idempotency-Key reused with different request payload: " + idempotencyKey);
                }
                return InvoiceId.of(UUID.fromString(existing.get().responseJson()));
            }
        }

        CustomerId customerId;
        try {
            customerId = CustomerId.of(UUID.fromString(command.customerId()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("customerId must be a valid UUID: " + command.customerId());
        }

        Customer customer = customerRepository.findByTenantAndId(tenantId, customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + command.customerId()));

        // Always reject BLOCKED/DELETED (even for draft create)
        if (!customer.canBeInvoiced()) {
            throw new CustomerNotInvoiceableException(
                    "Customer cannot be invoiced (status: " + customer.getStatus() + ")");
        }

        String currency = command.currencyCode() != null ? command.currencyCode() : "USD";
        BigDecimal invoiceAmount = command.lines().stream()
                .map(IssueInvoiceCommand.LineItem::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstanding = sumOpenBalance(tenantId, customerId, currency);

        if (issueNow) {
            // Hard-block credit limit on issue (before allocating ids / persisting)
            enforceCreditLimit(tenantId, customerId, outstanding, invoiceAmount);
        } else if (customer.getCreditLimit() != null
                && !customer.canBeInvoicedForAmount(outstanding, invoiceAmount)) {
            // Product default: soft-warn on draft credit overage (still create draft)
            LOG.warning(String.format(
                    "Draft invoice %s for customer %s would exceed credit limit (outstanding=%s, amount=%s, limit=%s)",
                    command.invoiceNumber(), command.customerId(), outstanding, invoiceAmount, customer.getCreditLimit()));
        }

        InvoiceId id = idGenerator.newInvoiceId();
        List<InvoiceLine> lines = IntStream.range(0, command.lines().size())
                .mapToObj(i -> {
                    var item = command.lines().get(i);
                    return new InvoiceLine(i + 1, item.description(), Money.of(item.amount(), currency));
                })
                .toList();
        Invoice invoice = new Invoice(id, command.invoiceNumber(), customerId, command.customerRef(), currency,
                command.dueDate(), command.dueDate(), lines);

        if (issueNow) {
            invoice.issue();
            invoiceRepository.save(tenantId, invoice);
            invoiceVersionRepository.save(tenantId, InvoiceSnapshotService.snapshot(tenantId, invoice, "ISSUE_ON_CREATE"));

            // Durable ledger: Dr AR / Cr Revenue
            LedgerService.TransactionResult ledgerTx = ledgerService.recordInvoiceIssued(
                    tenantId, id.getValue(), command.invoiceNumber(), invoice.getTotal());
            ledgerService.assertBalanced(ledgerTx.entries());
            ledgerRepository.saveAll(tenantId, ledgerTx.entries());

            String afterState = String.format(
                    "{\"id\":\"%s\",\"number\":\"%s\",\"customerId\":\"%s\",\"customer\":\"%s\",\"total\":%s,\"status\":\"ISSUED\"}",
                    id.getValue(), command.invoiceNumber(), command.customerId(), command.customerRef(),
                    invoice.getTotal().getAmount());
            AuditEntry audit = AuditEntry.create(tenantId, "INVOICE", id.getValue(), command.invoiceNumber(),
                    null, afterState);
            auditRepository.save(tenantId, audit);

            eventPublisher.publish(new InvoiceIssued(tenantId, id, command.customerRef(), invoice.getTotal(),
                    command.dueDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant()));
        } else {
            // Pure DRAFT: no ledger, no InvoiceIssued
            invoiceRepository.save(tenantId, invoice);
            invoiceVersionRepository.save(tenantId, InvoiceSnapshotService.snapshot(tenantId, invoice, "CREATE_DRAFT"));

            String afterState = String.format(
                    "{\"id\":\"%s\",\"number\":\"%s\",\"customerId\":\"%s\",\"customer\":\"%s\",\"total\":%s,\"status\":\"DRAFT\"}",
                    id.getValue(), command.invoiceNumber(), command.customerId(), command.customerRef(),
                    invoice.getTotal().getAmount());
            AuditEntry audit = AuditEntry.create(tenantId, "INVOICE", id.getValue(), command.invoiceNumber(),
                    null, afterState);
            auditRepository.save(tenantId, audit);
        }

        if (storeKey != null) {
            idempotencyStore.put(tenantId, storeKey, requestHash, id.getValue().toString());
        }

        return id;
    }

    /**
     * Hard credit check for issue path. Uses system-computed open AR for the customer
     * in the invoice currency (same-currency policy).
     */
    void enforceCreditLimit(TenantId tenantId, CustomerId customerId,
                            BigDecimal outstanding, BigDecimal invoiceAmount) {
        CustomerService.CreditCheckResult check = customerService.checkCreditLimit(
                tenantId, customerRepository, customerId, outstanding, invoiceAmount);
        if (!check.canInvoice()) {
            throw new CustomerNotInvoiceableException(check.message());
        }
    }

    BigDecimal sumOpenBalance(TenantId tenantId, CustomerId customerId, String currencyCode) {
        String currency = currencyCode != null ? currencyCode : "USD";
        return invoiceRepository.findOpenByTenantAndCustomer(tenantId, customerId).stream()
                .filter(inv -> currency.equalsIgnoreCase(inv.getCurrencyCode()))
                .map(inv -> inv.getBalanceDue().getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String hashRequest(IssueInvoiceCommand command) {
        String payload = command.invoiceNumber() + "|" + command.customerId() + "|" + command.customerRef() + "|"
                + command.currencyCode() + "|" + command.dueDate() + "|" + command.lines() + "|"
                + command.shouldIssueImmediately();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(payload.hashCode());
        }
    }
}
