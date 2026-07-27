package com.invoicegenie.ar.adapter.messaging;

import com.invoicegenie.ar.application.port.outbound.EmailSender;
import com.invoicegenie.ar.application.service.NotificationDestinationValidator;
import com.invoicegenie.ar.domain.model.notification.Notification;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.UUID;

/**
 * Dev/demo email sink — logs the message instead of sending.
 */
@ApplicationScoped
public class LoggingEmailSender implements EmailSender {

    private static final Logger LOG = Logger.getLogger(LoggingEmailSender.class);

    @ConfigProperty(name = "invoicegenie.notifications.log-payloads", defaultValue = "false")
    boolean logPayloads;

    @Override
    public SendResult send(Notification notification) {
        return send(notification, List.of());
    }

    @Override
    public SendResult send(Notification notification, List<Attachment> attachments) {
        String messageId = "log-email-" + UUID.randomUUID();
        int attachCount = attachments != null ? attachments.size() : 0;
        if (logPayloads) {
            LOG.infof("[EMAIL-LOG] id=%s to=%s subject=%s body=%s attachments=%d",
                    notification.getId(),
                    notification.getDestination(),
                    notification.getSubject(),
                    truncate(notification.getBody()),
                    attachCount);
        } else {
            LOG.infof("[EMAIL-LOG] id=%s to=%s subject=%s attachments=%d (payload redacted; set log-payloads=true for demo)",
                    notification.getId(),
                    NotificationDestinationValidator.mask(notification.getDestination()),
                    notification.getSubject(),
                    attachCount);
        }
        return SendResult.ok(messageId);
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() <= 500 ? s : s.substring(0, 500) + "...";
    }
}
