package com.invoicegenie.ar.adapter.messaging;

import com.invoicegenie.ar.application.port.outbound.WhatsAppSender;
import com.invoicegenie.ar.application.service.NotificationDestinationValidator;
import com.invoicegenie.ar.domain.model.notification.Notification;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.UUID;

/**
 * Dev/demo WhatsApp sink — logs instead of calling Meta Cloud API.
 */
@ApplicationScoped
public class LoggingWhatsAppSender implements WhatsAppSender {

    private static final Logger LOG = Logger.getLogger(LoggingWhatsAppSender.class);

    @ConfigProperty(name = "invoicegenie.notifications.log-payloads", defaultValue = "false")
    boolean logPayloads;

    @Override
    public SendResult send(Notification notification) {
        String messageId = "log-wa-" + UUID.randomUUID();
        if (logPayloads) {
            LOG.infof("[WHATSAPP-LOG] id=%s to=%s subject=%s body=%s",
                    notification.getId(),
                    notification.getDestination(),
                    notification.getSubject(),
                    truncate(notification.getBody()));
        } else {
            LOG.infof("[WHATSAPP-LOG] id=%s to=%s (payload redacted; set log-payloads=true for demo)",
                    notification.getId(),
                    NotificationDestinationValidator.mask(notification.getDestination()));
        }
        return SendResult.ok(messageId);
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() <= 500 ? s : s.substring(0, 500) + "...";
    }
}