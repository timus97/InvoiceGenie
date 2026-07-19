package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.ApplyInvoicePaymentUseCase;
import com.invoicegenie.ar.application.port.inbound.PaymentAllocationUseCase;
import com.invoicegenie.ar.application.port.inbound.RecordPaymentUseCase;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.domain.model.payment.PaymentId;
import com.invoicegenie.ar.domain.model.payment.PaymentMethod;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Unifies the invoice payment shortcut with the Payment + allocation path.
 *
 * <ol>
 *   <li>Load invoice and resolve payment amount (remaining balance or partial)</li>
 *   <li>Record a real Payment (fires {@code PaymentRecorded})</li>
 *   <li>Manually allocate that payment to the invoice (fires {@code PaymentAllocated},
 *       updates amountPaid / status)</li>
 * </ol>
 */
public class ApplyInvoicePaymentService implements ApplyInvoicePaymentUseCase {

    private final InvoiceRepository invoiceRepository;
    private final RecordPaymentUseCase recordPaymentUseCase;
    private final PaymentAllocationUseCase paymentAllocationUseCase;

    public ApplyInvoicePaymentService(InvoiceRepository invoiceRepository,
                                      RecordPaymentUseCase recordPaymentUseCase,
                                      PaymentAllocationUseCase paymentAllocationUseCase) {
        this.invoiceRepository = invoiceRepository;
        this.recordPaymentUseCase = recordPaymentUseCase;
        this.paymentAllocationUseCase = paymentAllocationUseCase;
    }

    @Override
    public Optional<Invoice> apply(TenantId tenantId, InvoiceId invoiceId, ApplyPaymentCommand command) {
        Optional<Invoice> invoiceOpt = invoiceRepository.findByTenantAndId(tenantId, invoiceId);
        if (invoiceOpt.isEmpty()) {
            return Optional.empty();
        }
        Invoice invoice = invoiceOpt.get();

        if (!invoice.canReceivePayments()) {
            throw new IllegalStateException(
                    "Invoice cannot receive payments in status: " + invoice.getStatus());
        }

        CustomerId customerId = resolveCustomerId(invoice);
        Money payAmount = resolveAmount(invoice, command);

        if (payAmount.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("payment amount must be positive");
        }
        if (payAmount.getAmount().compareTo(invoice.getBalanceDue().getAmount()) > 0) {
            throw new IllegalArgumentException(
                    "payment amount " + payAmount.getAmount()
                            + " exceeds balance due " + invoice.getBalanceDue().getAmount());
        }

        String paymentNumber = "PAY-INV-" + invoice.getInvoiceNumber() + "-"
                + System.currentTimeMillis();

        PaymentId paymentId = recordPaymentUseCase.record(tenantId,
                new RecordPaymentUseCase.RecordPaymentCommand(
                        paymentNumber,
                        customerId.getValue().toString(),
                        payAmount.getAmount(),
                        invoice.getCurrencyCode(),
                        LocalDate.now(),
                        PaymentMethod.OTHER,
                        "invoice-payment:" + invoiceId.getValue(),
                        "Applied via invoice payment endpoint"
                ));

        var allocResult = paymentAllocationUseCase.manualAllocate(
                tenantId,
                paymentId,
                List.of(new PaymentAllocationUseCase.ManualAllocationRequest(
                        invoiceId, payAmount, "invoice payment shortcut")),
                null,
                null);

        if (allocResult.isEmpty() || allocResult.get().hasErrors()) {
            String errors = allocResult.map(r -> String.join("; ", r.errors())).orElse("payment not found");
            throw new IllegalStateException("Failed to allocate payment to invoice: " + errors);
        }

        return invoiceRepository.findByTenantAndId(tenantId, invoiceId);
    }

    private CustomerId resolveCustomerId(Invoice invoice) {
        if (invoice.getCustomerId() != null) {
            return invoice.getCustomerId();
        }
        // Legacy: customer_ref may store the customer UUID string
        try {
            return CustomerId.of(UUID.fromString(invoice.getCustomerRef()));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Invoice has no customerId and customerRef is not a UUID: " + invoice.getCustomerRef());
        }
    }

    private Money resolveAmount(Invoice invoice, ApplyPaymentCommand command) {
        if (command.isFullyPaid()) {
            return invoice.getBalanceDue();
        }
        BigDecimal amount = command.amount();
        if (amount == null) {
            throw new IllegalArgumentException("amount is required when fullyPaid is false");
        }
        return Money.of(amount, invoice.getCurrencyCode());
    }
}
