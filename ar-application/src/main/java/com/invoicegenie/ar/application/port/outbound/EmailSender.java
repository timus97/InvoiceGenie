package com.invoicegenie.ar.application.port.outbound;

import com.invoicegenie.ar.domain.model.notification.Notification;

import java.util.Collections;
import java.util.List;

/**
 * Outbound port: email delivery.
 */
public interface EmailSender {

    /**
     * Send without attachments (default path).
     */
    SendResult send(Notification notification);

    /**
     * Send with optional attachments (PP-012). Default ignores attachments and delegates to {@link #send}.
     */
    default SendResult send(Notification notification, List<Attachment> attachments) {
        return send(notification);
    }

    record Attachment(String filename, String contentType, byte[] content) {
        public Attachment {
            if (filename == null || filename.isBlank()) {
                throw new IllegalArgumentException("filename required");
            }
            contentType = contentType != null ? contentType : "application/octet-stream";
            content = content != null ? content : new byte[0];
        }
    }

    record SendResult(boolean success, String providerMessageId, String errorMessage, Integer httpStatus) {
        public static SendResult ok(String messageId) {
            return new SendResult(true, messageId, null, 200);
        }
        public static SendResult fail(String error, Integer httpStatus) {
            return new SendResult(false, null, error, httpStatus);
        }
    }

    static List<Attachment> noAttachments() {
        return Collections.emptyList();
    }
}
