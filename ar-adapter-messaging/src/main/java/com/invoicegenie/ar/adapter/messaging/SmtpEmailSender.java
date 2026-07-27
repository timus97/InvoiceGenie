package com.invoicegenie.ar.adapter.messaging;

import com.invoicegenie.ar.application.port.outbound.EmailSender;
import com.invoicegenie.ar.application.service.NotificationDestinationValidator;
import com.invoicegenie.ar.domain.model.notification.Notification;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Real SMTP email sender (PP-001). Fail-closed when host/credentials missing.
 * Success only after transport accepts the message.
 */
@ApplicationScoped
@Typed(SmtpEmailSender.class)
public class SmtpEmailSender implements EmailSender {

    private static final Logger LOG = Logger.getLogger(SmtpEmailSender.class);

    @ConfigProperty(name = "invoicegenie.notifications.smtp.host", defaultValue = "none")
    String host;

    @ConfigProperty(name = "invoicegenie.notifications.smtp.port", defaultValue = "587")
    int port;

    @ConfigProperty(name = "invoicegenie.notifications.smtp.username", defaultValue = "none")
    String username;

    @ConfigProperty(name = "invoicegenie.notifications.smtp.password", defaultValue = "none")
    String password;

    @ConfigProperty(name = "invoicegenie.notifications.smtp.from", defaultValue = "noreply@invoicegenie.local")
    String from;

    @Inject
    Instance<SmtpMailTransport> transportInstance;

    /** Optional override for unit tests (bypasses CDI Instance). */
    SmtpMailTransport transportOverride;

    @Override
    public SendResult send(Notification notification) {
        if (host == null || host.isBlank() || "none".equalsIgnoreCase(host.trim())) {
            return SendResult.fail("SMTP host not configured (invoicegenie.notifications.smtp.host)", null);
        }
        if (!hasCredentials()) {
            return SendResult.fail(
                    "SMTP credentials not configured (invoicegenie.notifications.smtp.username/password)", null);
        }

        String to = notification.getDestination();
        if (to == null || to.isBlank()) {
            return SendResult.fail("Notification destination is empty", null);
        }

        try {
            SmtpMailTransport transport = resolveTransport();
            if (transport == null) {
                return SendResult.fail("SMTP transport bean unavailable", null);
            }
            String messageId = transport.send(
                    from,
                    to.trim(),
                    notification.getSubject(),
                    notification.getBody());
            LOG.infof("[EMAIL-SMTP] sent id=%s to=%s host=%s:%d providerMsgId=%s",
                    notification.getId(),
                    NotificationDestinationValidator.mask(to),
                    host, port, messageId);
            return SendResult.ok(messageId);
        } catch (Exception e) {
            LOG.warnf(e, "[EMAIL-SMTP] send failed id=%s to=%s",
                    notification.getId(),
                    NotificationDestinationValidator.mask(to));
            return SendResult.fail("SMTP send failed: " + e.getMessage(), null);
        }
    }

    private SmtpMailTransport resolveTransport() {
        if (transportOverride != null) {
            return transportOverride;
        }
        if (transportInstance != null && transportInstance.isResolvable()) {
            return transportInstance.get();
        }
        return null;
    }

    private boolean hasCredentials() {
        return username != null && !username.isBlank() && !"none".equalsIgnoreCase(username.trim())
                && password != null && !password.isBlank() && !"none".equalsIgnoreCase(password.trim());
    }
}
