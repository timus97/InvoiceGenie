package com.invoicegenie.ar.adapter.messaging;

import com.invoicegenie.ar.application.port.outbound.EmailSender;
import com.invoicegenie.ar.application.port.outbound.WhatsAppSender;
import com.invoicegenie.ar.application.service.NotificationDestinationValidator;
import com.invoicegenie.ar.application.service.NotificationEnqueueService;
import com.invoicegenie.ar.domain.model.notification.Notification;
import com.invoicegenie.ar.domain.model.notification.NotificationAttempt;
import com.invoicegenie.ar.domain.model.notification.NotificationAttemptRepository;
import com.invoicegenie.ar.domain.model.notification.NotificationChannel;
import com.invoicegenie.ar.domain.model.notification.NotificationRepository;
import com.invoicegenie.ar.domain.model.notification.NotificationStatus;
import com.invoicegenie.shared.tenant.TenantContext;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;

/**
 * Polls due notifications and dispatches via Email/WhatsApp senders with exponential retry.
 * Uses claimDue (FOR UPDATE SKIP LOCKED) for multi-instance safety (QA-NOTIFY-004/012).
 * PP-004: optional EMAIL fallback when WhatsApp fails permanently.
 */
@ApplicationScoped
public class NotificationDispatchWorker {

    private static final Logger LOG = Logger.getLogger(NotificationDispatchWorker.class);
    static final String FALLBACK_QUALIFIER = "fallback:wa";

    @Inject
    NotificationRepository notificationRepository;

    @Inject
    NotificationAttemptRepository attemptRepository;

    @Inject
    Instance<EmailSender> emailSender;

    @Inject
    Instance<WhatsAppSender> whatsAppSender;

    @Inject
    Instance<LoggingEmailSender> loggingEmailSender;

    @Inject
    Instance<SmtpEmailSender> smtpEmailSender;

    @Inject
    Instance<LoggingWhatsAppSender> loggingWhatsAppSender;

    @Inject
    Instance<MetaWhatsAppSender> metaWhatsAppSender;

    @Inject
    Instance<FailClosedWhatsAppSender> failClosedWhatsAppSender;

    @Inject
    Instance<NotificationEnqueueService> enqueueService;

    @ConfigProperty(name = "invoicegenie.notifications.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "invoicegenie.notifications.dispatch.batch", defaultValue = "50")
    int batch;

    @ConfigProperty(name = "invoicegenie.notifications.email.provider", defaultValue = "logging")
    String emailProvider;

    @ConfigProperty(name = "invoicegenie.notifications.whatsapp.provider", defaultValue = "logging")
    String whatsappProvider;

    @ConfigProperty(name = "invoicegenie.notifications.email.enabled", defaultValue = "true")
    boolean emailEnabled;

    @ConfigProperty(name = "invoicegenie.notifications.whatsapp.enabled", defaultValue = "false")
    boolean whatsappEnabled;

    @ConfigProperty(name = "invoicegenie.notifications.log-payloads", defaultValue = "false")
    boolean logPayloads;

    @ConfigProperty(name = "invoicegenie.notifications.channel-fallback-enabled", defaultValue = "false")
    boolean channelFallbackEnabled;

    @Scheduled(every = "${invoicegenie.notifications.dispatch.interval:15s}", delayed = "25s")
    public void processDue() {
        if (!enabled) {
            return;
        }
        List<Notification> claimed;
        try {
            claimed = claimBatch();
        } catch (Exception e) {
            LOG.debugf("Notification claim pass skipped: %s", e.getMessage());
            return;
        }
        for (Notification n : claimed) {
            try {
                dispatchOne(n);
            } catch (Exception e) {
                LOG.warnf(e, "Dispatch failed for notification %s", n.getId());
            }
        }
    }

    @Transactional
    List<Notification> claimBatch() {
        return notificationRepository.claimDue(Instant.now(), batch);
    }

    void dispatchOne(Notification n) {
        if (n.getStatus() != NotificationStatus.SENDING
                && n.getStatus() != NotificationStatus.PENDING
                && n.getStatus() != NotificationStatus.QUEUED) {
            return;
        }
        try {
            TenantContext.setCurrentTenant(n.getTenantId());
            // Already claimed as SENDING; refresh domain state if needed
            if (n.getStatus() != NotificationStatus.SENDING) {
                n.markSending();
                saveTx(n);
            }

            int attempt = n.getAttemptCount() + 1;
            boolean channelOn = n.getChannel() == NotificationChannel.EMAIL ? emailEnabled : whatsappEnabled;
            if (!channelOn) {
                n.markFailed(attempt, "Channel disabled in runtime config");
                saveTx(n);
                saveAttemptTx(NotificationAttempt.failure(
                        n.getTenantId(), n.getId(), attempt, "config", null, "channel disabled"));
                return;
            }

            boolean success;
            String providerMessageId = null;
            String error = null;
            Integer http = null;
            String providerName;

            if (n.getChannel() == NotificationChannel.EMAIL) {
                EmailSender sender = resolveEmailSender();
                providerName = emailProvider;
                EmailSender.SendResult r = sender.send(n);
                success = r.success();
                providerMessageId = r.providerMessageId();
                error = r.errorMessage();
                http = r.httpStatus();
            } else {
                WhatsAppSender sender = resolveWhatsAppSender();
                providerName = whatsappProvider;
                WhatsAppSender.SendResult r = sender.send(n);
                success = r.success();
                providerMessageId = r.providerMessageId();
                error = r.errorMessage();
                http = r.httpStatus();
            }

            if (success) {
                n.markSent(providerMessageId);
                saveTx(n);
                saveAttemptTx(NotificationAttempt.success(
                        n.getTenantId(), n.getId(), attempt, providerName, providerMessageId, "ok"));
                LOG.infof("Notification SENT id=%s channel=%s to=%s",
                        n.getId(), n.getChannel(),
                        NotificationDestinationValidator.mask(n.getDestination()));
            } else {
                scheduleOrFail(n, attempt, providerName, http, error);
            }
        } catch (Exception e) {
            LOG.warnf(e, "Dispatch failed for notification %s", n.getId());
            try {
                scheduleOrFail(n, n.getAttemptCount() + 1, "error", null, e.getMessage());
            } catch (Exception ignored) {
                // best effort
            }
        } finally {
            TenantContext.clear();
        }
    }

    @Transactional
    void saveTx(Notification n) {
        notificationRepository.save(n);
    }

    @Transactional
    void saveAttemptTx(NotificationAttempt a) {
        attemptRepository.save(a);
    }

    private void scheduleOrFail(Notification n, int attempt, String provider, Integer http, String error) {
        if (attempt >= n.getMaxAttempts()) {
            n.markFailed(attempt, error);
            saveTx(n);
            saveAttemptTx(NotificationAttempt.failure(
                    n.getTenantId(), n.getId(), attempt, provider, http, error));
            LOG.warnf("Notification FAILED after %d attempts id=%s: %s", attempt, n.getId(), error);
            maybeEnqueueChannelFallback(n);
            return;
        }
        long backoffSec = (long) Math.min(3600, Math.pow(2, attempt) * 5L);
        Instant next = Instant.now().plusSeconds(backoffSec);
        n.markRetry(attempt, error, next);
        saveTx(n);
        saveAttemptTx(NotificationAttempt.failure(
                n.getTenantId(), n.getId(), attempt, provider, http, error));
        LOG.infof("Notification RETRY attempt=%d next=%s id=%s", attempt, next, n.getId());
    }

    /**
     * PP-004: when WhatsApp fails permanently and fallback is enabled, enqueue EMAIL once.
     */
    private void maybeEnqueueChannelFallback(Notification n) {
        if (!channelFallbackEnabled) {
            return;
        }
        if (n.getChannel() != NotificationChannel.WHATSAPP) {
            return;
        }
        if (n.getInvoiceId() == null) {
            return;
        }
        if (enqueueService == null || !enqueueService.isResolvable()) {
            LOG.warnf("Channel fallback skipped: NotificationEnqueueService unavailable id=%s", n.getId());
            return;
        }
        try {
            List<Notification> fallback = enqueueService.get().enqueueForInvoice(
                    n.getTenantId(),
                    n.getInvoiceId(),
                    n.getEventType(),
                    List.of(NotificationChannel.EMAIL),
                    FALLBACK_QUALIFIER,
                    null,
                    true,   // force: bypass event auto-policy for fallback
                    false); // already forced path
            LOG.infof("Channel fallback EMAIL enqueued for failed WhatsApp id=%s fallback=%s",
                    n.getId(),
                    fallback.isEmpty() ? "none" : fallback.get(0).getId() + "/" + fallback.get(0).getStatus());
        } catch (Exception e) {
            LOG.warnf(e, "Channel fallback enqueue failed for notification %s", n.getId());
        }
    }

    private EmailSender resolveEmailSender() {
        if ("smtp".equalsIgnoreCase(emailProvider)) {
            if (smtpEmailSender.isResolvable()) {
                return smtpEmailSender.get();
            }
            return notification -> EmailSender.SendResult.fail(
                    "SMTP provider selected but SmtpEmailSender bean unavailable", null);
        }
        if (loggingEmailSender.isResolvable()) {
            return loggingEmailSender.get();
        }
        return emailSender.get();
    }

    private WhatsAppSender resolveWhatsAppSender() {
        if ("meta".equalsIgnoreCase(whatsappProvider)) {
            if (metaWhatsAppSender.isResolvable()) {
                return metaWhatsAppSender.get();
            }
            // Fail closed if Meta bean missing
            if (failClosedWhatsAppSender.isResolvable()) {
                return failClosedWhatsAppSender.get();
            }
            return notification -> WhatsAppSender.SendResult.fail(
                    "WhatsApp provider=meta selected but MetaWhatsAppSender unavailable", null);
        }
        if (loggingWhatsAppSender.isResolvable()) {
            return loggingWhatsAppSender.get();
        }
        return whatsAppSender.get();
    }
}
