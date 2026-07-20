package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.RecordPaymentUseCase;
import com.invoicegenie.ar.application.port.outbound.EventPublisher;
import com.invoicegenie.ar.application.port.outbound.IdGenerator;
import com.invoicegenie.ar.application.port.outbound.IdempotencyStore;
import com.invoicegenie.ar.domain.event.PaymentRecorded;
import com.invoicegenie.ar.domain.exception.IdempotencyConflictException;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.customer.CustomerRepository;
import com.invoicegenie.ar.domain.model.ledger.LedgerRepository;
import com.invoicegenie.ar.domain.model.outbox.AuditEntry;
import com.invoicegenie.ar.domain.model.outbox.AuditRepository;
import com.invoicegenie.ar.domain.model.payment.Payment;
import com.invoicegenie.ar.domain.model.payment.PaymentId;
import com.invoicegenie.ar.domain.model.payment.PaymentRepository;
import com.invoicegenie.ar.domain.service.LedgerService;
import com.invoicegenie.shared.domain.TenantId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service: records a new payment received from a customer.
 *
 * <p>Validates that the customer exists before creating the payment.
 * Creates audit log entry for compliance.
 * Posts payment-received ledger entries (Dr Bank / Cr AR).
 * Publishes {@link PaymentRecorded} after successful save.
 */
public class RecordPaymentService implements RecordPaymentUseCase {

    private final PaymentRepository paymentRepository;
    private final CustomerRepository customerRepository;
    private final IdGenerator idGenerator;
    private final AuditRepository auditRepository;
    private final EventPublisher eventPublisher;
    private final LedgerService ledgerService;
    private final LedgerRepository ledgerRepository;
    private final IdempotencyStore idempotencyStore;

    public RecordPaymentService(PaymentRepository paymentRepository,
                                CustomerRepository customerRepository,
                                IdGenerator idGenerator,
                                AuditRepository auditRepository,
                                EventPublisher eventPublisher,
                                LedgerService ledgerService,
                                LedgerRepository ledgerRepository,
                                IdempotencyStore idempotencyStore) {
        this.paymentRepository = paymentRepository;
        this.customerRepository = customerRepository;
        this.idGenerator = idGenerator;
        this.auditRepository = auditRepository;
        this.eventPublisher = eventPublisher;
        this.ledgerService = ledgerService;
        this.ledgerRepository = ledgerRepository;
        this.idempotencyStore = idempotencyStore;
    }

    @Override
    public PaymentId record(TenantId tenantId, RecordPaymentCommand command) {
        return record(tenantId, command, null);
    }

    @Override
    public PaymentId record(TenantId tenantId, RecordPaymentCommand command, String idempotencyKey) {
        String storeKey = null;
        String requestHash = null;
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            storeKey = "payment:" + idempotencyKey;
            requestHash = hashRequest(command);
            var existingKey = idempotencyStore.find(tenantId, storeKey);
            if (existingKey.isPresent()) {
                if (!existingKey.get().requestHash().equals(requestHash)) {
                    throw new IdempotencyConflictException(
                            "Idempotency-Key reused with different request payload: " + idempotencyKey);
                }
                return PaymentId.of(UUID.fromString(existingKey.get().responseJson()));
            }
        }

        // Validate customer exists
        CustomerId customerId = CustomerId.of(UUID.fromString(command.customerId()));
        Optional<com.invoicegenie.ar.domain.model.customer.Customer> customerOpt =
                customerRepository.findByTenantAndId(tenantId, customerId);
        if (customerOpt.isEmpty()) {
            throw new IllegalArgumentException("Customer not found: " + command.customerId());
        }

        // Check for duplicate payment number within tenant
        Optional<Payment> existing = paymentRepository.findByTenantAndNumber(tenantId, command.paymentNumber());
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Payment number already exists: " + command.paymentNumber());
        }

        // Create payment aggregate (uses full constructor for all fields)
        PaymentId paymentId = PaymentId.of(idGenerator.newUuid());
        String currency = command.currencyCode() != null ? command.currencyCode() : "USD";

        Payment payment = new Payment(
                paymentId,
                command.paymentNumber(),
                customerId,
                com.invoicegenie.shared.domain.Money.of(command.amount().toString(), currency),
                command.paymentDate(),
                java.time.Instant.now(),
                command.method(),
                command.reference(),
                null, // bankAccountId (optional, not in command for now)
                command.notes(),
                com.invoicegenie.ar.domain.model.payment.PaymentStatus.RECEIVED,
                java.time.Instant.now(),
                java.time.Instant.now(),
                1L,
                java.util.List.of()
        );

        // Persist
        paymentRepository.save(tenantId, payment);

        // Durable ledger: Dr Bank / Cr AR
        LedgerService.TransactionResult ledgerTx = ledgerService.recordPaymentReceived(
                tenantId, paymentId.getValue(), command.paymentNumber(), payment.getAmount());
        ledgerService.assertBalanced(ledgerTx.entries());
        ledgerRepository.saveAll(tenantId, ledgerTx.entries());

        // Audit log
        String afterState = String.format(
                "{\"id\":\"%s\",\"number\":\"%s\",\"customerId\":\"%s\",\"amount\":%s,\"method\":\"%s\"}",
                paymentId.getValue(), command.paymentNumber(), command.customerId(),
                command.amount(), command.method());
        AuditEntry audit = AuditEntry.create(tenantId, "PAYMENT", paymentId.getValue(),
                command.paymentNumber(), null, afterState);
        auditRepository.save(tenantId, audit);

        // Publish PaymentRecorded for GL / downstream consumers
        eventPublisher.publish(new PaymentRecorded(
                tenantId,
                paymentId,
                command.customerId(),
                payment.getAmount(),
                payment.getReceivedAt()
        ));

        if (storeKey != null) {
            idempotencyStore.put(tenantId, storeKey, requestHash, paymentId.getValue().toString());
        }

        return paymentId;
    }

    private static String hashRequest(RecordPaymentCommand command) {
        String payload = command.paymentNumber() + "|" + command.customerId() + "|"
                + command.amount() + "|" + command.currencyCode() + "|" + command.paymentDate() + "|"
                + command.method() + "|" + command.reference() + "|" + command.notes();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(payload.hashCode());
        }
    }
}
