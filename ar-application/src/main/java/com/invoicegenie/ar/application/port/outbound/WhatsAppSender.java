package com.invoicegenie.ar.application.port.outbound;

import com.invoicegenie.ar.domain.model.notification.Notification;

/**
 * Outbound port: WhatsApp delivery (Meta Cloud API templates).
 */
public interface WhatsAppSender {

    SendResult send(Notification notification);

    record SendResult(boolean success, String providerMessageId, String errorMessage, Integer httpStatus) {
        public static SendResult ok(String messageId) {
            return new SendResult(true, messageId, null, 200);
        }
        public static SendResult fail(String error, Integer httpStatus) {
            return new SendResult(false, null, error, httpStatus);
        }
    }
}
