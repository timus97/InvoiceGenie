package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.IssueInvoiceUseCase;
import com.invoicegenie.ar.application.port.outbound.EventPublisher;
import com.invoicegenie.ar.application.port.outbound.IdGenerator;
import com.invoicegenie.ar.application.port.outbound.IdempotencyStore;
import com.invoicegenie.ar.domain.event.InvoiceIssued;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.customer.CustomerRepository;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceLine;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.domain.model.ledger.LedgerRepository;
import com.invoicegenie.ar.domain.model.outbox.AuditEntry;
import com.invoicegenie.ar.domain.model.outbox.AuditRepository;
import com.invoicegenie.ar.domain.service.LedgerService;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Application service: orchestrates domain and ports. No UI; no tenant resolution (receives TenantId).
 */
public class IssueInvoiceService implements IssueInvoiceUseCase {

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final IdGenerator idGenerator;
    private final EventPublisher eventPublisher;
    private final AuditRepository auditRepository;
    private final LedgerService ledgerService;
    private final LedgerRepository ledgerRepository;
    private final IdempotencyStore idempotencyStore;

    public IssueInvoiceService(InvoiceRepository invoiceRepository,
                               CustomerRepository customerRepository,
                               IdGenerator idGenerator,
                               EventPublisher eventPublisher,
                               AuditRepository auditRepository,
                               LedgerService ledgerService,
                               LedgerRepository ledgerRepository,
                               IdempotencyStore idempotencyStore) {
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.idGenerator = idGenerator;
        this.eventPublisher = eventPublisher;
        this.auditRepository = auditRepository;
        this.ledgerService = ledgerService;
        this.ledgerRepository = ledgerRepository;
        this.idempotencyStore = idempotencyStore;
    }

    @Override
    public InvoiceId issue(TenantId tenantId, IssueInvoiceCommand command) {
        return issue(tenantId, command, null);
    }

    @Override
    public InvoiceId issue(TenantId tenantId, IssueInvoiceCommand command, String idempotencyKey) {
        String storeKey = null;
        String requestHash = null;
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            storeKey = "invoice:" + idempotencyKey;
            requestHash = hashRequest(command);
            var existing = idempotencyStore.find(tenantId, storeKey);
            if (existing.isPresent()) {
                if (!existing.get().requestHash().equals(requestHash)) {
                    throw new IllegalArgumentException(
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

        if (customerRepository.findByTenantAndId(tenantId, customerId).isEmpty()) {
            throw new IllegalArgumentException("Customer not found: " + command.customerId());
        }

        InvoiceId id = idGenerator.newInvoiceId();
        String currency = command.currencyCode() != null ? command.currencyCode() : "USD";
        List<InvoiceLine> lines = IntStream.range(0, command.lines().size())
                .mapToObj(i -> {
                    var item = command.lines().get(i);
                    return new InvoiceLine(i + 1, item.description(), Money.of(item.amount(), currency));
                })
                .toList();
        Invoice invoice = new Invoice(id, command.invoiceNumber(), customerId, command.customerRef(), currency,
                command.dueDate(), command.dueDate(), lines);
        invoice.issue();
        invoiceRepository.save(tenantId, invoice);

        // Durable ledger: Dr AR / Cr Revenue
        LedgerService.TransactionResult ledgerTx = ledgerService.recordInvoiceIssued(
                tenantId, id.getValue(), command.invoiceNumber(), invoice.getTotal());
        ledgerService.assertBalanced(ledgerTx.entries());
        ledgerRepository.saveAll(tenantId, ledgerTx.entries());

        // Audit: record invoice creation
        String afterState = String.format(
                "{\"id\":\"%s\",\"number\":\"%s\",\"customerId\":\"%s\",\"customer\":\"%s\",\"total\":%s}",
                id.getValue(), command.invoiceNumber(), command.customerId(), command.customerRef(),
                invoice.getTotal().getAmount());
        AuditEntry audit = AuditEntry.create(tenantId, "INVOICE", id.getValue(), command.invoiceNumber(),
                null, afterState);
        auditRepository.save(tenantId, audit);

        eventPublisher.publish(new InvoiceIssued(tenantId, id, command.customerRef(), invoice.getTotal(),
                command.dueDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant()));

        if (storeKey != null) {
            idempotencyStore.put(tenantId, storeKey, requestHash, id.getValue().toString());
        }

        return id;
    }

    private static String hashRequest(IssueInvoiceCommand command) {
        String payload = command.invoiceNumber() + "|" + command.customerId() + "|" + command.customerRef() + "|"
                + command.currencyCode() + "|" + command.dueDate() + "|" + command.lines();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(payload.hashCode());
        }
    }
}
