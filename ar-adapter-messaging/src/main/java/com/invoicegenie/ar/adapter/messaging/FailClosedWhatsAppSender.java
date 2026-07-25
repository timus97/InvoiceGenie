package com.invoicegenie.ar.adapter.messaging;

import com.invoicegenie.ar.application.port.outbound.WhatsAppSender;
import com.invoicegenie.ar.domain.model.notification.Notification;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import org.jboss.logging.Logger;

/**
 * Used when whatsapp.provider=meta until Meta Cloud API client is implemented.
 */
@ApplicationScoped
@Typed(FailClosedWhatsAppSender.class)
public class FailClosedWhatsAppSender implements WhatsAppSender {

    private static final Logger LOG = Logger.getLogger(FailClosedWhatsAppSender.class);

    @Override
    public SendResult send(Notification notification) {
        LOG.warnf("[WHATSAPP-META] client not implemented — failing closed id=%s", notification.getId());
        return SendResult.fail(
                "WhatsApp Meta provider not implemented; set whatsapp.provider=logging for demo",
                null);
    }
}