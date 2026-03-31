package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.RecordPaymentUseCase;
import com.invoicegenie.ar.application.port.outbound.IdGenerator;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.customer.CustomerRepository;
import com.invoicegenie.ar.domain.model.outbox.AuditEntry;
import com.invoicegenie.ar.domain.model.outbox.AuditRepository;
import com.invoicegenie.ar.domain.model.payment.Payment;
import com.invoicegenie.ar.domain.model.payment.PaymentId;
import com.invoicegenie.ar.domain.model.payment.PaymentRepository;
import com.invoicegenie.shared.domain.TenantId;

import java.util.Optional;

/**
 * Application service: records a new payment received from a customer.
 * 
 * <p>Validates that the customer exists before creating the payment.
 * Creates audit log entry for compliance.
 */
public class RecordPaymentService implements RecordPaymentUseCase {

    private final PaymentRepository paymentRepository;
    private final CustomerRepository customerRepository;
    private final IdGenerator idGenerator;
    private final AuditRepository auditRepository;

    public RecordPaymentService(PaymentRepository paymentRepository,
                                CustomerRepository customerRepository,
                                IdGenerator idGenerator,
                                AuditRepository auditRepository) {
        this.paymentRepository = paymentRepository;
        this.customerRepository = customerRepository;
        this.idGenerator = idGenerator;
        this.auditRepository = auditRepository;
    }

    @Override
    public PaymentId record(TenantId tenantId, RecordPaymentCommand command) {
        // Validate customer exists
        CustomerId customerId = CustomerId.of(java.util.UUID.fromString(command.customerId()));
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

        // Audit log
        String afterState = String.format(
                "{\"id\":\"%s\",\"number\":\"%s\",\"customerId\":\"%s\",\"amount\":%s,\"method\":\"%s\"}",
                paymentId.getValue(), command.paymentNumber(), command.customerId(), 
                command.amount(), command.method());
        AuditEntry audit = AuditEntry.create(tenantId, "PAYMENT", paymentId.getValue(), 
                command.paymentNumber(), null, afterState);
        auditRepository.save(tenantId, audit);

        return paymentId;
    }
}
