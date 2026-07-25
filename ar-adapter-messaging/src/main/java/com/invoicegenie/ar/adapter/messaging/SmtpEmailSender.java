package com.invoicegenie.ar.adapter.messaging;

import com.invoicegenie.ar.application.port.outbound.EmailSender;
import com.invoicegenie.ar.application.service.NotificationDestinationValidator;
import com.invoicegenie.ar.domain.model.notification.Notification;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * SMTP email sender — fail-closed until real Jakarta Mail transport is wired (QA-NOTIFY-013).
 * Does NOT report success without actually sending.
 */
@ApplicationScoped
@Typed(SmtpEmailSender.class)
public class SmtpEmailSender implements EmailSender {

    private static final Logger LOG = Logger.getLogger(SmtpEmailSender.class);

    @ConfigProperty(name = "invoicegenie.notifications.smtp.host", defaultValue = "none")
    String host;

    @ConfigProperty(name = "invoicegenie.notifications.smtp.port", defaultValue = "587")
    int port;

    @ConfigProperty(name = "invoicegenie.notifications.smtp.from", defaultValue = "noreply@invoicegenie.local")
    String from;

    @Override
    public SendResult send(Notification notification) {
        if (host == null || host.isBlank() || "none".equalsIgnoreCase(host.trim())) {
            return SendResult.fail("SMTP host not configured (invoicegenie.notifications.smtp.host)", null);
        }
        // Fail closed: stub must not pretend delivery (QA-NOTIFY-013)
        LOG.warnf("[EMAIL-SMTP] transport not implemented — failing closed. host=%s:%d from=%s to=%s id=%s",
                host, port, from,
                NotificationDestinationValidator.mask(notification.getDestination()),
                notification.getId());
        return SendResult.fail(
                "SMTP transport not implemented; use email.provider=logging for demo or integrate Jakarta Mail",
                null);
    }
}