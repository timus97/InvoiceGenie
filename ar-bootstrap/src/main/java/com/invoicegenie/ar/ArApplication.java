package com.invoicegenie.ar;

import com.invoicegenie.ar.application.port.inbound.AgingUseCase;
import com.invoicegenie.ar.application.port.inbound.ApplyInvoicePaymentUseCase;
import com.invoicegenie.ar.application.port.inbound.AuditQueryUseCase;
import com.invoicegenie.ar.application.port.inbound.ChequeOcrUseCase;
import com.invoicegenie.ar.application.port.inbound.ChequeUseCase;
import com.invoicegenie.ar.application.port.inbound.CreditNoteUseCase;
import com.invoicegenie.ar.application.port.inbound.CustomerUseCase;
import com.invoicegenie.ar.application.port.inbound.DunningUseCase;
import com.invoicegenie.ar.application.port.inbound.ExchangeRateUseCase;
import com.invoicegenie.ar.application.port.inbound.GetInvoiceUseCase;
import com.invoicegenie.ar.application.port.inbound.InvoiceLifecycleUseCase;
import com.invoicegenie.ar.application.port.inbound.InvoiceVersionUseCase;
import com.invoicegenie.ar.application.port.inbound.IssueInvoiceUseCase;
import com.invoicegenie.ar.application.port.inbound.LedgerQueryUseCase;
import com.invoicegenie.ar.application.port.inbound.ListInvoicesUseCase;
import com.invoicegenie.ar.application.port.inbound.NotificationPreferenceUseCase;
import com.invoicegenie.ar.application.port.inbound.NotificationPolicyUseCase;
import com.invoicegenie.ar.application.port.inbound.NotificationUseCase;
import com.invoicegenie.ar.application.port.inbound.PaymentAllocationUseCase;
import com.invoicegenie.ar.application.port.inbound.PaymentQueryUseCase;
import com.invoicegenie.ar.application.port.inbound.PaymentReversalUseCase;
import com.invoicegenie.ar.application.port.inbound.PaymentUnallocateUseCase;
import com.invoicegenie.ar.application.port.inbound.PostingPeriodUseCase;
import com.invoicegenie.ar.application.port.inbound.RecordPaymentUseCase;
import com.invoicegenie.ar.application.port.inbound.StatementUseCase;
import com.invoicegenie.ar.application.port.inbound.TenantUseCase;
import com.invoicegenie.ar.application.port.inbound.WebhookUseCase;
import com.invoicegenie.ar.application.port.outbound.EventPublisher;
import com.invoicegenie.ar.application.port.outbound.IdGenerator;
import com.invoicegenie.ar.application.port.outbound.IdempotencyStore;
import com.invoicegenie.ar.application.service.AgingApplicationService;
import com.invoicegenie.ar.application.service.ApplyInvoicePaymentService;
import com.invoicegenie.ar.application.service.AuditQueryService;
import com.invoicegenie.ar.application.service.ChequeApplicationService;
import com.invoicegenie.ar.application.service.ChequeOcrApplicationService;
import com.invoicegenie.ar.application.service.CreditNoteApplicationService;
import com.invoicegenie.ar.application.service.CustomerManagementService;
import com.invoicegenie.ar.application.service.DunningApplicationService;
import com.invoicegenie.ar.application.service.DunningPolicy;
import com.invoicegenie.ar.application.service.ExchangeRateApplicationService;
import com.invoicegenie.ar.application.service.GetInvoiceService;
import com.invoicegenie.ar.application.service.InvoiceLifecycleService;
import com.invoicegenie.ar.application.service.InvoiceVersionQueryService;
import com.invoicegenie.ar.application.service.IssueInvoiceService;
import com.invoicegenie.ar.application.service.LedgerQueryService;
import com.invoicegenie.ar.application.service.ListInvoicesService;
import com.invoicegenie.ar.application.service.NotificationApplicationService;
import com.invoicegenie.ar.application.service.NotificationEnqueueService;
import com.invoicegenie.ar.application.service.NotificationPreferenceApplicationService;
import com.invoicegenie.ar.application.service.NotificationPolicyApplicationService;
import com.invoicegenie.ar.application.service.NotificationRateLimiter;
import com.invoicegenie.ar.application.service.PaymentAllocationService;
import com.invoicegenie.ar.application.service.PaymentQueryService;
import com.invoicegenie.ar.application.service.PaymentReversalService;
import com.invoicegenie.ar.application.service.PaymentUnallocateService;
import com.invoicegenie.ar.application.service.PostingPeriodService;
import com.invoicegenie.ar.application.service.RecordPaymentService;
import com.invoicegenie.ar.application.service.StatementApplicationService;
import com.invoicegenie.ar.application.service.TenantManagementService;
import com.invoicegenie.ar.application.service.WebhookApplicationService;
import com.invoicegenie.ar.application.service.WebhookRedriveService;
import com.invoicegenie.ar.domain.model.customer.CustomerRepository;
import com.invoicegenie.ar.domain.model.fx.ExchangeRateRepository;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.domain.model.invoice.InvoiceVersionRepository;
import com.invoicegenie.ar.domain.model.ledger.LedgerRepository;
import com.invoicegenie.ar.domain.model.notification.NotificationAttemptRepository;
import com.invoicegenie.ar.domain.model.notification.NotificationPolicyRepository;
import com.invoicegenie.ar.domain.model.notification.NotificationPreferenceRepository;
import com.invoicegenie.ar.domain.model.notification.NotificationRepository;
import com.invoicegenie.ar.domain.model.notification.NotificationTemplateRepository;
import com.invoicegenie.ar.domain.model.outbox.AuditRepository;
import com.invoicegenie.ar.domain.model.payment.ChequeRepository;
import com.invoicegenie.ar.domain.model.payment.CreditNoteRepository;
import com.invoicegenie.ar.domain.model.payment.PaymentId;
import com.invoicegenie.ar.domain.model.payment.PaymentRepository;
import com.invoicegenie.ar.domain.model.period.PostingPeriodRepository;
import com.invoicegenie.ar.domain.model.tenant.TenantRepository;
import com.invoicegenie.ar.domain.model.webhook.WebhookDeliveryRepository;
import com.invoicegenie.ar.domain.model.webhook.WebhookRepository;
import com.invoicegenie.ar.domain.service.AgingService;
import com.invoicegenie.ar.domain.service.ChequeService;
import com.invoicegenie.ar.domain.service.CreditNoteService;
import com.invoicegenie.ar.domain.service.CurrencyConversionService;
import com.invoicegenie.ar.domain.service.CustomerService;
import com.invoicegenie.ar.domain.service.LedgerService;
import com.invoicegenie.shared.domain.UuidV7;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * Single CDI composition root for InvoiceGenie AR.
 */
public class ArApplication {

    @Produces
    @ApplicationScoped
    public PostingPeriodUseCase postingPeriodUseCase(PostingPeriodRepository postingPeriodRepository) {
        return new PostingPeriodService(postingPeriodRepository);
    }

    @Produces
    @ApplicationScoped
    public IssueInvoiceUseCase issueInvoiceUseCase(InvoiceRepository invoiceRepository,
                                                   CustomerRepository customerRepository,
                                                   IdGenerator idGenerator,
                                                   EventPublisher eventPublisher,
                                                   AuditRepository auditRepository,
                                                   LedgerService ledgerService,
                                                   LedgerRepository ledgerRepository,
                                                   IdempotencyStore idempotencyStore,
                                                   InvoiceVersionRepository invoiceVersionRepository,
                                                   PostingPeriodUseCase postingPeriodUseCase) {
        return new IssueInvoiceService(invoiceRepository, customerRepository, idGenerator, eventPublisher,
                auditRepository, ledgerService, ledgerRepository, idempotencyStore, invoiceVersionRepository,
                null, postingPeriodUseCase);
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
                                                     ApplyInvoicePaymentUseCase applyInvoicePaymentUseCase,
                                                     LedgerService ledgerService,
                                                     LedgerRepository ledgerRepository,
                                                     InvoiceVersionRepository invoiceVersionRepository,
                                                     CustomerRepository customerRepository,
                                                     CustomerService customerService,
                                                     PostingPeriodUseCase postingPeriodUseCase) {
        return new InvoiceLifecycleService(invoiceRepository, auditRepository, applyInvoicePaymentUseCase,
                ledgerService, ledgerRepository, invoiceVersionRepository, customerRepository, customerService,
                postingPeriodUseCase);
    }

    @Produces
    @ApplicationScoped
    public InvoiceVersionUseCase invoiceVersionUseCase(InvoiceVersionRepository invoiceVersionRepository) {
        return new InvoiceVersionQueryService(invoiceVersionRepository);
    }

    @Produces
    @ApplicationScoped
    public PaymentAllocationUseCase paymentAllocationUseCase(PaymentRepository paymentRepository,
                                                             InvoiceRepository invoiceRepository,
                                                             EventPublisher eventPublisher,
                                                             IdempotencyStore idempotencyStore,
                                                             CurrencyConversionService currencyConversionService,
                                                             @org.eclipse.microprofile.config.inject.ConfigProperty(
                                                                     name = "invoicegenie.payments.allow-fx-allocation",
                                                                     defaultValue = "false") boolean allowFxAllocation) {
        return new PaymentAllocationService(paymentRepository, invoiceRepository, eventPublisher, idempotencyStore,
                currencyConversionService, allowFxAllocation);
    }

    @Produces
    @ApplicationScoped
    public RecordPaymentUseCase recordPaymentUseCase(PaymentRepository paymentRepository,
                                                     CustomerRepository customerRepository,
                                                     IdGenerator idGenerator,
                                                     AuditRepository auditRepository,
                                                     EventPublisher eventPublisher,
                                                     LedgerService ledgerService,
                                                     LedgerRepository ledgerRepository,
                                                     IdempotencyStore idempotencyStore,
                                                     PostingPeriodUseCase postingPeriodUseCase) {
        return new RecordPaymentService(paymentRepository, customerRepository, idGenerator, auditRepository,
                eventPublisher, ledgerService, ledgerRepository, idempotencyStore, postingPeriodUseCase);
    }

    @Produces
    @ApplicationScoped
    public ApplyInvoicePaymentUseCase applyInvoicePaymentUseCase(InvoiceRepository invoiceRepository,
                                                                 RecordPaymentUseCase recordPaymentUseCase,
                                                                 PaymentAllocationUseCase paymentAllocationUseCase) {
        return new ApplyInvoicePaymentService(invoiceRepository, recordPaymentUseCase, paymentAllocationUseCase);
    }

    @Produces
    @ApplicationScoped
    public PaymentQueryUseCase paymentQueryUseCase(PaymentRepository paymentRepository) {
        return new PaymentQueryService(paymentRepository);
    }

    @Produces
    @ApplicationScoped
    public PaymentReversalUseCase paymentReversalUseCase(PaymentRepository paymentRepository,
                                                         InvoiceRepository invoiceRepository,
                                                         LedgerService ledgerService,
                                                         LedgerRepository ledgerRepository,
                                                         AuditRepository auditRepository,
                                                         IdempotencyStore idempotencyStore) {
        return new PaymentReversalService(paymentRepository, invoiceRepository, ledgerService,
                ledgerRepository, auditRepository, idempotencyStore);
    }

    @Produces
    @ApplicationScoped
    public PaymentUnallocateUseCase paymentUnallocateUseCase(PaymentRepository paymentRepository,
                                                             InvoiceRepository invoiceRepository,
                                                             AuditRepository auditRepository) {
        return new PaymentUnallocateService(paymentRepository, invoiceRepository, auditRepository);
    }

    @Produces
    @ApplicationScoped
    public CustomerUseCase customerUseCase(CustomerService customerService,
                                           CustomerRepository customerRepository,
                                           InvoiceRepository invoiceRepository) {
        return new CustomerManagementService(customerService, customerRepository, invoiceRepository);
    }

    @Produces
    @ApplicationScoped
    public ChequeOcrUseCase chequeOcrUseCase(
            jakarta.enterprise.inject.Instance<ChequeOcrApplicationService.PdfTextExtractor> pdfTextExtractors) {
        // PdfBox extractor lives in ar-adapter-api; optional so tests without it still boot
        if (pdfTextExtractors != null && pdfTextExtractors.isResolvable()) {
            return new ChequeOcrApplicationService(pdfTextExtractors.get());
        }
        return new ChequeOcrApplicationService();
    }

    @Produces
    @ApplicationScoped
    public ChequeUseCase chequeUseCase(ChequeService chequeService,
                                       ChequeRepository chequeRepository,
                                       InvoiceLifecycleUseCase invoiceLifecycleUseCase,
                                       LedgerRepository ledgerRepository,
                                       RecordPaymentUseCase recordPaymentUseCase,
                                       PaymentAllocationUseCase paymentAllocationUseCase,
                                       PaymentRepository paymentRepository,
                                       InvoiceRepository invoiceRepository,
                                       LedgerService ledgerService,
                                       CustomerRepository customerRepository,
                                       @org.eclipse.microprofile.config.inject.ConfigProperty(
                                               name = "invoicegenie.ocr.min-confidence",
                                               defaultValue = "0.45") double minOcrConfidence) {
        return new ChequeApplicationService(chequeService, chequeRepository, invoiceLifecycleUseCase,
                ledgerRepository, recordPaymentUseCase, paymentAllocationUseCase, paymentRepository,
                invoiceRepository, ledgerService, customerRepository, minOcrConfidence);
    }

    @Produces
    @ApplicationScoped
    public CreditNoteUseCase creditNoteUseCase(CreditNoteService creditNoteService,
                                               CreditNoteRepository creditNoteRepository,
                                               InvoiceRepository invoiceRepository,
                                               LedgerService ledgerService,
                                               LedgerRepository ledgerRepository) {
        return new CreditNoteApplicationService(creditNoteService, creditNoteRepository,
                invoiceRepository, ledgerService, ledgerRepository);
    }

    @Produces
    @ApplicationScoped
    public AgingUseCase agingUseCase(AgingService agingService,
                                     InvoiceRepository invoiceRepository) {
        return new AgingApplicationService(agingService, invoiceRepository);
    }

    @Produces
    @ApplicationScoped
    public StatementUseCase statementUseCase(CustomerRepository customerRepository,
                                             InvoiceRepository invoiceRepository,
                                             EventPublisher eventPublisher) {
        return new StatementApplicationService(customerRepository, invoiceRepository, eventPublisher);
    }

    @Produces
    @ApplicationScoped
    public DunningUseCase dunningUseCase(InvoiceRepository invoiceRepository,
                                         EventPublisher eventPublisher,
                                         DunningPolicy dunningPolicy) {
        return new DunningApplicationService(invoiceRepository, eventPublisher, dunningPolicy);
    }

    @Produces
    @ApplicationScoped
    public LedgerQueryUseCase ledgerQueryUseCase(LedgerService ledgerService,
                                                 LedgerRepository ledgerRepository,
                                                 com.invoicegenie.ar.domain.model.ledger.ChartOfAccountsRepository chartOfAccountsRepository) {
        return new LedgerQueryService(ledgerService, ledgerRepository, chartOfAccountsRepository);
    }

    @Produces
    @ApplicationScoped
    public TenantUseCase tenantUseCase(TenantRepository tenantRepository,
                                       com.invoicegenie.ar.domain.model.ledger.ChartOfAccountsRepository chartOfAccountsRepository) {
        return new TenantManagementService(tenantRepository, chartOfAccountsRepository);
    }

    @Produces
    @ApplicationScoped
    public ExchangeRateUseCase exchangeRateUseCase(ExchangeRateRepository exchangeRateRepository,
                                                   CurrencyConversionService currencyConversionService) {
        return new ExchangeRateApplicationService(exchangeRateRepository, currencyConversionService);
    }

    @Produces
    @ApplicationScoped
    public CurrencyConversionService currencyConversionService(ExchangeRateRepository exchangeRateRepository) {
        return new CurrencyConversionService(exchangeRateRepository);
    }

    @Produces
    @ApplicationScoped
    public AuditQueryUseCase auditQueryUseCase(AuditRepository auditRepository) {
        return new AuditQueryService(auditRepository);
    }

    @Produces
    @ApplicationScoped
    public WebhookUseCase webhookUseCase(WebhookRepository webhookRepository) {
        return new WebhookApplicationService(webhookRepository);
    }

    @Produces
    @ApplicationScoped
    public WebhookRedriveService webhookRedriveService(WebhookDeliveryRepository deliveryRepository) {
        return new WebhookRedriveService(deliveryRepository);
    }

    @Produces
    @ApplicationScoped
    public NotificationEnqueueService notificationEnqueueService(
            NotificationRepository notificationRepository,
            NotificationPolicyRepository policyRepository,
            NotificationPreferenceRepository preferenceRepository,
            NotificationTemplateRepository templateRepository,
            InvoiceRepository invoiceRepository,
            CustomerRepository customerRepository,
            @org.eclipse.microprofile.config.inject.ConfigProperty(
                    name = "invoicegenie.notifications.enabled", defaultValue = "true") boolean enabled,
            @org.eclipse.microprofile.config.inject.ConfigProperty(
                    name = "invoicegenie.notifications.max-attempts", defaultValue = "5") int maxAttempts) {
        return new NotificationEnqueueService(notificationRepository, policyRepository, preferenceRepository,
                templateRepository, invoiceRepository, customerRepository, enabled, maxAttempts);
    }

    @Produces
    @ApplicationScoped
    public NotificationUseCase notificationUseCase(NotificationRepository notificationRepository,
                                                   NotificationAttemptRepository attemptRepository,
                                                   NotificationEnqueueService enqueueService) {
        return new NotificationApplicationService(notificationRepository, attemptRepository, enqueueService);
    }

    @Produces
    @ApplicationScoped
    public NotificationPreferenceUseCase notificationPreferenceUseCase(
            NotificationPreferenceRepository preferenceRepository,
            CustomerRepository customerRepository) {
        return new NotificationPreferenceApplicationService(preferenceRepository, customerRepository);
    }

    @Produces
    @ApplicationScoped
    public NotificationRateLimiter notificationRateLimiter(
            @org.eclipse.microprofile.config.inject.ConfigProperty(
                    name = "invoicegenie.notifications.send-rate-per-minute", defaultValue = "30") int maxPerMinute) {
        return new NotificationRateLimiter(maxPerMinute);
    }

    @Produces
    @ApplicationScoped
    public NotificationPolicyUseCase notificationPolicyUseCase(NotificationPolicyRepository policyRepository) {
        return new NotificationPolicyApplicationService(policyRepository);
    }

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