package com.invoicegenie.ar;

import com.invoicegenie.ar.application.port.inbound.ApplyInvoicePaymentUseCase;
import com.invoicegenie.ar.application.port.inbound.GetInvoiceUseCase;
import com.invoicegenie.ar.application.port.inbound.InvoiceLifecycleUseCase;
import com.invoicegenie.ar.application.port.inbound.IssueInvoiceUseCase;
import com.invoicegenie.ar.application.port.inbound.ListInvoicesUseCase;
import com.invoicegenie.ar.application.port.inbound.PaymentAllocationUseCase;
import com.invoicegenie.ar.application.port.inbound.RecordPaymentUseCase;
import com.invoicegenie.ar.application.port.outbound.EventPublisher;
import com.invoicegenie.ar.application.port.outbound.IdGenerator;
import com.invoicegenie.ar.application.service.ApplyInvoicePaymentService;
import com.invoicegenie.ar.application.service.GetInvoiceService;
import com.invoicegenie.ar.application.service.InvoiceLifecycleService;
import com.invoicegenie.ar.application.service.IssueInvoiceService;
import com.invoicegenie.ar.application.service.ListInvoicesService;
import com.invoicegenie.ar.application.service.PaymentAllocationService;
import com.invoicegenie.ar.application.service.RecordPaymentService;
import com.invoicegenie.ar.domain.model.customer.CustomerRepository;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.domain.model.outbox.AuditRepository;
import com.invoicegenie.ar.domain.model.payment.PaymentId;
import com.invoicegenie.ar.domain.model.payment.PaymentRepository;
import com.invoicegenie.ar.domain.service.AgingService;
import com.invoicegenie.ar.domain.service.ChequeService;
import com.invoicegenie.ar.domain.service.CreditNoteService;
import com.invoicegenie.ar.domain.service.CustomerService;
import com.invoicegenie.ar.domain.service.LedgerService;
import com.invoicegenie.shared.domain.UuidV7;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * Single CDI composition root for InvoiceGenie AR.
 *
 * <p>Adapters implement ports; use cases and plain domain services are wired here.
 * EventPublisher is provided by KafkaEventPublisher in ar-adapter-messaging
 * (transactional outbox pattern).
 */
public class ArApplication {

    // ── Invoice use cases ──────────────────────────────────────────────────

    @Produces
    @ApplicationScoped
    public IssueInvoiceUseCase issueInvoiceUseCase(InvoiceRepository invoiceRepository,
                                                   CustomerRepository customerRepository,
                                                   IdGenerator idGenerator,
                                                   EventPublisher eventPublisher,
                                                   AuditRepository auditRepository) {
        return new IssueInvoiceService(invoiceRepository, customerRepository, idGenerator, eventPublisher, auditRepository);
    }

    @Produces
    @ApplicationScoped
    public GetInvoiceUseCase getInvoiceUseCase(InvoiceRepository invoiceRepository) {
        return new GetInvoiceService(invoiceRepository);
    }

    @Produces
    @ApplicationScoped
    public ListInvoicesUseCase listInvoicesUseCase(InvoiceRepository invoiceRepository) {
        return new ListInvoicesService(invoiceRepository);
    }

    @Produces
    @ApplicationScoped
    public InvoiceLifecycleUseCase lifecycleUseCase(InvoiceRepository invoiceRepository,
                                                     AuditRepository auditRepository,
                                                     ApplyInvoicePaymentUseCase applyInvoicePaymentUseCase) {
        return new InvoiceLifecycleService(invoiceRepository, auditRepository, applyInvoicePaymentUseCase);
    }

    // ── Payment use cases ──────────────────────────────────────────────────

    @Produces
    @ApplicationScoped
    public PaymentAllocationUseCase paymentAllocationUseCase(PaymentRepository paymentRepository,
                                                             InvoiceRepository invoiceRepository,
                                                             EventPublisher eventPublisher) {
        return new PaymentAllocationService(paymentRepository, invoiceRepository, eventPublisher);
    }

    @Produces
    @ApplicationScoped
    public RecordPaymentUseCase recordPaymentUseCase(PaymentRepository paymentRepository,
                                                     CustomerRepository customerRepository,
                                                     IdGenerator idGenerator,
                                                     AuditRepository auditRepository,
                                                     EventPublisher eventPublisher) {
        return new RecordPaymentService(paymentRepository, customerRepository, idGenerator, auditRepository, eventPublisher);
    }

    @Produces
    @ApplicationScoped
    public ApplyInvoicePaymentUseCase applyInvoicePaymentUseCase(InvoiceRepository invoiceRepository,
                                                                 RecordPaymentUseCase recordPaymentUseCase,
                                                                 PaymentAllocationUseCase paymentAllocationUseCase) {
        return new ApplyInvoicePaymentService(invoiceRepository, recordPaymentUseCase, paymentAllocationUseCase);
    }

    // ── Domain services (plain Java — no CDI annotations in ar-domain) ─────

    @Produces
    @ApplicationScoped
    public AgingService agingService() {
        return new AgingService();
    }

    @Produces
    @ApplicationScoped
    public ChequeService chequeService() {
        return new ChequeService();
    }

    @Produces
    @ApplicationScoped
    public LedgerService ledgerService() {
        return new LedgerService();
    }

    @Produces
    @ApplicationScoped
    public CustomerService customerService() {
        return new CustomerService();
    }

    @Produces
    @ApplicationScoped
    public CreditNoteService creditNoteService() {
        return new CreditNoteService();
    }

    // ── Infrastructure ports ───────────────────────────────────────────────

    @Produces
    @ApplicationScoped
    public IdGenerator idGenerator() {
        return new IdGenerator() {
            @Override
            public InvoiceId newInvoiceId() {
                return InvoiceId.of(UuidV7.generate());
            }

            @Override
            public java.util.UUID newUuid() {
                return UuidV7.generate();
            }

            @Override
            public PaymentId newPaymentId() {
                return PaymentId.of(UuidV7.generate());
            }
        };
    }
}
