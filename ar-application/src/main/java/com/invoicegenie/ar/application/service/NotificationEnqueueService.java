package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.StatementUseCase;
import com.invoicegenie.ar.domain.model.customer.Customer;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.customer.CustomerRepository;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.domain.model.invoice.InvoiceStatus;
import com.invoicegenie.ar.domain.model.notification.Notification;
import com.invoicegenie.ar.domain.model.notification.NotificationChannel;
import com.invoicegenie.ar.domain.model.notification.NotificationEventType;
import com.invoicegenie.ar.domain.model.notification.NotificationIdempotencyKeys;
import com.invoicegenie.ar.domain.model.notification.NotificationPolicy;
import com.invoicegenie.ar.domain.model.notification.NotificationPolicyRepository;
import com.invoicegenie.ar.domain.model.notification.NotificationPreference;
import com.invoicegenie.ar.domain.model.notification.NotificationPreferenceRepository;
import com.invoicegenie.ar.domain.model.notification.NotificationRepository;
import com.invoicegenie.ar.domain.model.notification.NotificationSkipReason;
import com.invoicegenie.ar.domain.model.notification.NotificationStatus;
import com.invoicegenie.ar.domain.model.notification.NotificationTemplate;
import com.invoicegenie.ar.domain.model.notification.NotificationTemplateRenderer;
import com.invoicegenie.ar.domain.model.notification.NotificationTemplateRepository;
import com.invoicegenie.shared.domain.TenantId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Enqueues customer notifications with policy/preference checks, template render, and idempotency.
 *
 * <p>{@code force} only bypasses event auto-policy flags (issue/reminder/dunning toggles).
 * It never bypasses global kill-switch, master policy disable, or channel disables (QA-NOTIFY-002).
 */
public class NotificationEnqueueService {

    private static final Set<InvoiceStatus> NOTIFIABLE = EnumSet.of(
            InvoiceStatus.ISSUED, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.OVERDUE);

    private final NotificationRepository notificationRepository;
    private final NotificationPolicyRepository policyRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationTemplateRepository templateRepository;
    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final boolean globalEnabled;
    private final int maxAttempts;
    private final UnsubscribeTokenService unsubscribeTokenService;
    private final String publicBaseUrl;
    private final PdfDocumentService pdfDocumentService;

    public NotificationEnqueueService(NotificationRepository notificationRepository,
                                      NotificationPolicyRepository policyRepository,
                                      NotificationPreferenceRepository preferenceRepository,
                                      NotificationTemplateRepository templateRepository,
                                      InvoiceRepository invoiceRepository,
                                      CustomerRepository customerRepository,
                                      boolean globalEnabled,
                                      int maxAttempts) {
        this(notificationRepository, policyRepository, preferenceRepository, templateRepository,
                invoiceRepository, customerRepository, globalEnabled, maxAttempts, null, null, null);
    }

    public NotificationEnqueueService(NotificationRepository notificationRepository,
                                      NotificationPolicyRepository policyRepository,
                                      NotificationPreferenceRepository preferenceRepository,
                                      NotificationTemplateRepository templateRepository,
                                      InvoiceRepository invoiceRepository,
                                      CustomerRepository customerRepository,
                                      boolean globalEnabled,
                                      int maxAttempts,
                                      UnsubscribeTokenService unsubscribeTokenService,
                                      String publicBaseUrl,
                                      PdfDocumentService pdfDocumentService) {
        this.notificationRepository = notificationRepository;
        this.policyRepository = policyRepository;
        this.preferenceRepository = preferenceRepository;
        this.templateRepository = templateRepository;
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.globalEnabled = globalEnabled;
        this.maxAttempts = maxAttempts > 0 ? maxAttempts : 5;
        this.unsubscribeTokenService = unsubscribeTokenService;
        this.publicBaseUrl = publicBaseUrl;
        this.pdfDocumentService = pdfDocumentService;
    }

    /**
     * @throws IllegalArgumentException with message {@code INVOICE_NOT_FOUND} when invoice missing
     */
    public List<Notification> enqueueForInvoice(TenantId tenantId, InvoiceId invoiceId,
                                                NotificationEventType eventType,
                                                List<NotificationChannel> channels,
                                                String qualifier,
                                                Map<String, String> extraVars,
                                                boolean force,
                                                boolean respectAutoPolicy) {
        List<Notification> results = new ArrayList<>();
        Optional<Invoice> invOpt = invoiceRepository.findByTenantAndId(tenantId, invoiceId);
        if (invOpt.isEmpty()) {
            throw new IllegalArgumentException("INVOICE_NOT_FOUND");
        }
        Invoice invoice = invOpt.get();
        CustomerId customerId = invoice.getCustomerId();
        Customer customer = null;
        if (customerId != null) {
            customer = customerRepository.findByTenantAndId(tenantId, customerId).orElse(null);
        }

        NotificationPolicy policy = policyRepository.findByTenant(tenantId)
                .orElseGet(() -> NotificationPolicy.defaults(tenantId));

        List<NotificationChannel> targetChannels = channels;
        if (targetChannels == null || targetChannels.isEmpty()) {
            targetChannels = policy.channelsFor(eventType);
        }

        String effectiveQualifier = qualifier;
        if (effectiveQualifier == null || effectiveQualifier.isBlank()) {
            if (eventType == NotificationEventType.PAYMENT_REMINDER) {
                effectiveQualifier = qualifierFor(eventType, invoice.getDueDate(), null);
            } else if (eventType == NotificationEventType.DUNNING_NOTICE) {
                effectiveQualifier = qualifierFor(eventType, null, 1);
            }
        }

        for (NotificationChannel channel : targetChannels) {
            results.add(enqueueOne(tenantId, invoice, customer, eventType, channel,
                    effectiveQualifier, extraVars, force, respectAutoPolicy, policy));
        }
        return results;
    }

    /**
     * Enqueue statement email for a customer (PP-013). Optionally attaches statement PDF bytes
     * in metadata when {@link PdfDocumentService} is available.
     */
    public Notification enqueueStatementSend(TenantId tenantId, Customer customer,
                                             StatementUseCase.CustomerStatement statement,
                                             boolean attachPdf) {
        CustomerId customerId = customer.getId();
        NotificationChannel channel = NotificationChannel.EMAIL;
        LocalDate asOf = statement.asOfDate();
        String idempotencyKey = NotificationIdempotencyKeys.forStatementSend(customerId, asOf, channel);

        Optional<Notification> existing = notificationRepository.findByIdempotencyKey(tenantId, idempotencyKey);
        if (existing.isPresent()) {
            Notification prev = existing.get();
            if (prev.getStatus().isTerminalSuccess()
                    || prev.getStatus() == NotificationStatus.PENDING
                    || prev.getStatus() == NotificationStatus.QUEUED
                    || prev.getStatus() == NotificationStatus.SENDING
                    || prev.getStatus() == NotificationStatus.FAILED) {
                return prev;
            }
            if (prev.getStatus().isRecoverableSkip()) {
                notificationRepository.delete(tenantId, prev.getId());
            } else {
                return prev;
            }
        }

        NotificationPolicy policy = policyRepository.findByTenant(tenantId)
                .orElseGet(() -> NotificationPolicy.defaults(tenantId));

        if (!globalEnabled) {
            return saveSkipped(tenantId, customerId, null, NotificationEventType.STATEMENT_SEND, channel,
                    idempotencyKey, NotificationSkipReason.GLOBAL_DISABLED);
        }
        if (!policy.isEnabled() || !policy.isChannelGloballyEnabled(channel)) {
            return saveSkipped(tenantId, customerId, null, NotificationEventType.STATEMENT_SEND, channel,
                    idempotencyKey, !policy.isEnabled()
                            ? NotificationSkipReason.POLICY_DISABLED
                            : NotificationSkipReason.CHANNEL_DISABLED);
        }

        Optional<NotificationPreference> pref =
                preferenceRepository.findByCustomerAndChannel(tenantId, customerId, channel);
        if (pref.isPresent() && !pref.get().isEnabled()) {
            return saveSkipped(tenantId, customerId, null, NotificationEventType.STATEMENT_SEND, channel,
                    idempotencyKey, NotificationSkipReason.OPTED_OUT);
        }

        String destination = resolveDestination(customer, customerId, tenantId, channel);
        if (destination == null || destination.isBlank()) {
            return saveSkipped(tenantId, customerId, null, NotificationEventType.STATEMENT_SEND, channel,
                    idempotencyKey, NotificationSkipReason.NO_DESTINATION);
        }
        try {
            NotificationDestinationValidator.validateOrThrow(channel, destination);
        } catch (IllegalArgumentException ex) {
            return saveSkipped(tenantId, customerId, null, NotificationEventType.STATEMENT_SEND, channel,
                    idempotencyKey, NotificationSkipReason.NO_DESTINATION);
        }

        Optional<NotificationTemplate> templateOpt =
                templateRepository.findActive(tenantId, NotificationEventType.STATEMENT_SEND, channel, "en");
        if (templateOpt.isEmpty()) {
            return saveSkipped(tenantId, customerId, null, NotificationEventType.STATEMENT_SEND, channel,
                    idempotencyKey, NotificationSkipReason.NO_TEMPLATE);
        }
        NotificationTemplate template = templateOpt.get();

        Map<String, String> vars = new HashMap<>();
        vars.put("customerName", customer.getDisplayName());
        vars.put("asOfDate", asOf != null ? asOf.toString() : "");
        vars.put("totalBalance", statement.totalBalance() != null
                ? statement.totalBalance().toPlainString() : "0");
        vars.put("currency", statement.currency() != null ? statement.currency() : "");
        vars.put("openItemCount", String.valueOf(statement.openItems() != null ? statement.openItems().size() : 0));
        vars.put("statementSummary", buildStatementSummary(statement));

        String subject = NotificationTemplateRenderer.render(template.getSubject(), vars);
        String body = NotificationTemplateRenderer.render(template.getBody(), vars);
        body = appendUnsubscribeFooter(tenantId, customerId, channel, body);

        String metadata = null;
        if (attachPdf && pdfDocumentService != null) {
            try {
                byte[] pdf = pdfDocumentService.generateStatementPdf(statement);
                metadata = "{\"attachments\":[{\"filename\":\"statement-" + asOf + ".pdf\","
                        + "\"contentType\":\"application/pdf\","
                        + "\"base64\":\"" + Base64.getEncoder().encodeToString(pdf) + "\"}]}";
            } catch (Exception ignored) {
                // best effort — still send email without attachment
            }
        }

        Notification n = Notification.enqueue(tenantId, customerId, null,
                NotificationEventType.STATEMENT_SEND, channel, idempotencyKey, destination,
                subject, body, template.getId(), metadata, maxAttempts);
        try {
            notificationRepository.save(n);
            return n;
        } catch (RuntimeException e) {
            return notificationRepository.findByIdempotencyKey(tenantId, idempotencyKey).orElse(n);
        }
    }

    public Notification enqueueOne(TenantId tenantId, Invoice invoice, Customer customer,
                                   NotificationEventType eventType, NotificationChannel channel,
                                   String qualifier, Map<String, String> extraVars,
                                   boolean force, boolean respectAutoPolicy,
                                   NotificationPolicy policy) {
        InvoiceId invoiceId = invoice.getId();
        CustomerId customerId = invoice.getCustomerId();
        String idempotencyKey = NotificationIdempotencyKeys.build(eventType, invoiceId, channel, qualifier);

        Optional<Notification> existing = notificationRepository.findByIdempotencyKey(tenantId, idempotencyKey);
        if (existing.isPresent()) {
            Notification prev = existing.get();
            if (prev.getStatus().isTerminalSuccess()
                    || prev.getStatus() == NotificationStatus.PENDING
                    || prev.getStatus() == NotificationStatus.QUEUED
                    || prev.getStatus() == NotificationStatus.SENDING
                    || prev.getStatus() == NotificationStatus.FAILED) {
                return prev;
            }
            if (prev.getStatus().isRecoverableSkip()) {
                notificationRepository.delete(tenantId, prev.getId());
            } else {
                return prev;
            }
        }

        if (!globalEnabled) {
            return saveSkipped(tenantId, customerId, invoiceId, eventType, channel, idempotencyKey,
                    NotificationSkipReason.GLOBAL_DISABLED);
        }

        if (policy == null) {
            policy = policyRepository.findByTenant(tenantId).orElseGet(() -> NotificationPolicy.defaults(tenantId));
        }

        if (!policy.isEnabled()) {
            return saveSkipped(tenantId, customerId, invoiceId, eventType, channel, idempotencyKey,
                    NotificationSkipReason.POLICY_DISABLED);
        }

        if (respectAutoPolicy && !force && !policy.isEventEnabled(eventType)) {
            return saveSkipped(tenantId, customerId, invoiceId, eventType, channel, idempotencyKey,
                    NotificationSkipReason.EVENT_DISABLED);
        }

        if (!policy.isChannelGloballyEnabled(channel)) {
            return saveSkipped(tenantId, customerId, invoiceId, eventType, channel, idempotencyKey,
                    NotificationSkipReason.CHANNEL_DISABLED);
        }

        if (invoice.getStatus() == null || !NOTIFIABLE.contains(invoice.getStatus())) {
            return saveSkipped(tenantId, customerId, invoiceId, eventType, channel, idempotencyKey,
                    NotificationSkipReason.INVALID_STATUS);
        }

        if (customerId != null) {
            Optional<NotificationPreference> pref =
                    preferenceRepository.findByCustomerAndChannel(tenantId, customerId, channel);
            if (pref.isPresent() && !pref.get().isEnabled()) {
                return saveSkipped(tenantId, customerId, invoiceId, eventType, channel, idempotencyKey,
                        NotificationSkipReason.OPTED_OUT);
            }
        }

        String destination = resolveDestination(customer, customerId, tenantId, channel);
        if (destination == null || destination.isBlank()) {
            return saveSkipped(tenantId, customerId, invoiceId, eventType, channel, idempotencyKey,
                    NotificationSkipReason.NO_DESTINATION);
        }
        try {
            NotificationDestinationValidator.validateOrThrow(channel, destination);
        } catch (IllegalArgumentException ex) {
            return saveSkipped(tenantId, customerId, invoiceId, eventType, channel, idempotencyKey,
                    NotificationSkipReason.NO_DESTINATION);
        }

        Optional<NotificationTemplate> templateOpt =
                templateRepository.findActive(tenantId, eventType, channel, "en");
        if (templateOpt.isEmpty()) {
            return saveSkipped(tenantId, customerId, invoiceId, eventType, channel, idempotencyKey,
                    NotificationSkipReason.NO_TEMPLATE);
        }
        NotificationTemplate template = templateOpt.get();

        Map<String, String> vars = buildVars(invoice, customer, extraVars);
        String subject = NotificationTemplateRenderer.render(template.getSubject(), vars);
        String body = NotificationTemplateRenderer.render(template.getBody(), vars);
        if (channel == NotificationChannel.EMAIL && customerId != null) {
            body = appendUnsubscribeFooter(tenantId, customerId, channel, body);
        }

        String metadata = null;
        if (channel == NotificationChannel.EMAIL
                && eventType == NotificationEventType.INVOICE_ISSUED
                && policy.isAttachPdfOnIssue()
                && pdfDocumentService != null) {
            try {
                byte[] pdf = pdfDocumentService.generateInvoicePdf(invoice, customer);
                String filename = "invoice-" + invoice.getInvoiceNumber() + ".pdf";
                metadata = "{\"attachments\":[{\"filename\":\"" + escapeJson(filename) + "\","
                        + "\"contentType\":\"application/pdf\","
                        + "\"base64\":\"" + Base64.getEncoder().encodeToString(pdf) + "\"}]}";
            } catch (Exception ignored) {
                // best effort
            }
        }

        Notification n = Notification.enqueue(tenantId, customerId, invoiceId, eventType, channel,
                idempotencyKey, destination, subject, body, template.getId(), metadata, maxAttempts);
        try {
            notificationRepository.save(n);
            return n;
        } catch (RuntimeException e) {
            Optional<Notification> raced = notificationRepository.findByIdempotencyKey(tenantId, idempotencyKey);
            if (raced.isPresent()) {
                return raced.get();
            }
            throw e;
        }
    }

    private String appendUnsubscribeFooter(TenantId tenantId, CustomerId customerId,
                                           NotificationChannel channel, String body) {
        if (unsubscribeTokenService == null || publicBaseUrl == null || publicBaseUrl.isBlank()
                || "none".equalsIgnoreCase(publicBaseUrl.trim())) {
            return body;
        }
        try {
            String token = unsubscribeTokenService.generate(tenantId, customerId, channel);
            String base = publicBaseUrl.endsWith("/")
                    ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                    : publicBaseUrl.trim();
            String url = base + "/api/v1/public/unsubscribe?token=" + token;
            String footer = "\n\n---\nTo unsubscribe from these emails, visit: " + url
                    + "\nOr POST to the same URL.";
            return (body != null ? body : "") + footer;
        } catch (Exception e) {
            return body;
        }
    }

    private static String buildStatementSummary(StatementUseCase.CustomerStatement statement) {
        if (statement.openItems() == null || statement.openItems().isEmpty()) {
            return "(no open items)";
        }
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (StatementUseCase.OpenItem item : statement.openItems()) {
            if (n >= 20) {
                sb.append("... and ").append(statement.openItems().size() - n).append(" more\n");
                break;
            }
            sb.append("- ").append(item.invoiceNumber())
                    .append(" due ").append(item.dueDate())
                    .append(": ").append(item.balanceDue()).append(' ').append(item.currency())
                    .append('\n');
            n++;
        }
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Notification saveSkipped(TenantId tenantId, CustomerId customerId, InvoiceId invoiceId,
                                     NotificationEventType eventType, NotificationChannel channel,
                                     String idempotencyKey, NotificationSkipReason reason) {
        Optional<Notification> existing = notificationRepository.findByIdempotencyKey(tenantId, idempotencyKey);
        if (existing.isPresent()) {
            Notification prev = existing.get();
            if (prev.getStatus().isRecoverableSkip()) {
                notificationRepository.delete(tenantId, prev.getId());
            } else {
                return prev;
            }
        }
        Notification n = Notification.skipped(tenantId, customerId, invoiceId, eventType, channel,
                idempotencyKey, reason, null);
        try {
            notificationRepository.save(n);
        } catch (RuntimeException e) {
            return notificationRepository.findByIdempotencyKey(tenantId, idempotencyKey).orElse(n);
        }
        return n;
    }

    private String resolveDestination(Customer customer, CustomerId customerId, TenantId tenantId,
                                      NotificationChannel channel) {
        if (customerId != null) {
            Optional<NotificationPreference> pref =
                    preferenceRepository.findByCustomerAndChannel(tenantId, customerId, channel);
            if (pref.isPresent() && pref.get().getDestinationOverride() != null
                    && !pref.get().getDestinationOverride().isBlank()) {
                return pref.get().getDestinationOverride().trim();
            }
        }
        if (customer == null) {
            return null;
        }
        return switch (channel) {
            case EMAIL -> customer.getEmail();
            case WHATSAPP -> customer.getPhone();
        };
    }

    private Map<String, String> buildVars(Invoice invoice, Customer customer, Map<String, String> extra) {
        Map<String, String> vars = new HashMap<>();
        vars.put("invoiceNumber", invoice.getInvoiceNumber());
        vars.put("invoiceId", invoice.getId().getValue().toString());
        vars.put("currency", invoice.getCurrencyCode());
        vars.put("total", invoice.getTotal() != null ? invoice.getTotal().getAmount().toPlainString() : "");
        vars.put("balanceDue", invoice.getBalanceDue() != null ? invoice.getBalanceDue().getAmount().toPlainString() : "");
        vars.put("dueDate", invoice.getDueDate() != null ? invoice.getDueDate().toString() : "");
        vars.put("issueDate", invoice.getIssueDate() != null ? invoice.getIssueDate().toString() : "");
        vars.put("customerName", customer != null ? customer.getDisplayName() : invoice.getCustomerRef());
        vars.put("customerRef", invoice.getCustomerRef() != null ? invoice.getCustomerRef() : "");
        if (extra != null) {
            vars.putAll(extra);
        }
        return vars;
    }

    public static String qualifierFor(NotificationEventType eventType, LocalDate dueDate, Integer dunningLevel) {
        return switch (eventType) {
            case INVOICE_ISSUED, STATEMENT_SEND -> null;
            case PAYMENT_REMINDER -> dueDate != null ? "due:" + dueDate : null;
            case DUNNING_NOTICE -> dunningLevel != null ? "L" + dunningLevel : null;
        };
    }
}
