package com.invoicegenie.ar.adapter.messaging;

/**
 * Low-level SMTP transport — extractable for unit tests (PP-001).
 */
public interface SmtpMailTransport {

    /**
     * Send a plain-text email. Returns provider message id when available, else a generated id.
     */
    String send(String from, String to, String subject, String body) throws Exception;
}
