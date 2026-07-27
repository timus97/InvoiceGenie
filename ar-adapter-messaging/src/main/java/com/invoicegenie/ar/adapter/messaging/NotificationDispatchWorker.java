package com.invoicegenie.ar.adapter.messaging;

import com.invoicegenie.ar.application.port.outbound.EmailSender;
import com.invoicegenie.ar.application.port.outbound.WhatsAppSender;
import com.invoicegenie.ar.application.service.NotificationDestinationValidator;
import com.invoicegenie.ar.domain.model.notification.Notification;
import com.invoicegenie.ar.domain.model.notification.NotificationAttempt;
import com.invoicegenie.ar.domain.model.notification.NotificationAttemptRepository;
import com.invoicegenie.ar.domain.model.notification.NotificationChannel;
import com.invoicegenie.ar.domain.model.notification.NotificationPolicy;
import com.invoicegenie.ar.domain.model.notification.NotificationPolicyRepository;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Polls due notifications and dispatches via Email/WhatsApp senders with exponential retry.
 * Uses claimDue (FOR UPDATE SKIP LOCKED) for multi-instance safety (QA-NOTIFY-004/012).
 * Defers sends during tenant quiet hours (PP-010).
 */
@ApplicationScoped
public class NotificationDispatchWorker {

    private static final Logger LOG = Logger.getLogger(NotificationDispatchWorker.class);

    private static final Pattern ATTACH_PATTERN = Pattern.compile(
            "\"filename\"\\s*:\\s*\"([^\"]+)\".*?\"contentType\"\\s*:\\s*\"([^\"]+)\".*?\"base64\"\\s*:\\s*\"([^\"]+)\"",
            Pattern.DOTALL);

    @Inject
    NotificationRepository notificationRepository;

    @Inject
    NotificationAttemptRepository attemptRepository;

    @Inject
    Instance<NotificationPolicyRepository> policyRepository;

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
    Instance<FailClosedWhatsAppSender> failClosedWhatsAppSender;

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
            if (n.getStatus() != NotificationStatus.SENDING) {
                n.markSending();
                saveTx(n);
            }

            // PP-010: quiet hours — defer without consuming attempt
            if (policyRepository.isResolvable()) {
                NotificationPolicy policy = policyRepository.get().findByTenant(n.getTenantId())
                        .orElse(null);
                if (policy != null && policy.isInQuietHours(Instant.now())) {
                    Instant resume = policy.nextQuietHoursEnd(Instant.now());
                    n.deferUntil(resume);
                    saveTx(n);
                    LOG.infof("Notification deferred for quiet hours id=%s until=%s", n.getId(), resume);
                    return;
                }
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
                List<EmailSender.Attachment> attachments = parseAttachments(n.getMetadataJson());
                EmailSender.SendResult r = attachments.isEmpty()
                        ? sender.send(n)
                        : sender.send(n, attachments);
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

    static List<EmailSender.Attachment> parseAttachments(String metadataJson) {
        List<EmailSender.Attachment> list = new ArrayList<>();
        if (metadataJson == null || metadataJson.isBlank()) {
            return list;
        }
        Matcher m = ATTACH_PATTERN.matcher(metadataJson);
        while (m.find()) {
            try {
                String filename = m.group(1);
                String contentType = m.group(2);
                byte[] content = Base64.getDecoder().decode(m.group(3));
                list.add(new EmailSender.Attachment(filename, contentType, content));
            } catch (Exception ignored) {
                // skip malformed attachment
            }
        }
        return list;
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
            if (failClosedWhatsAppSender.isResolvable()) {
                return failClosedWhatsAppSender.get();
            }
            return notification -> WhatsAppSender.SendResult.fail(
                    "WhatsApp provider=meta is not implemented; use logging for demo", null);
        }
        if (loggingWhatsAppSender.isResolvable()) {
            return loggingWhatsAppSender.get();
        }
        return whatsAppSender.get();
    }
}
