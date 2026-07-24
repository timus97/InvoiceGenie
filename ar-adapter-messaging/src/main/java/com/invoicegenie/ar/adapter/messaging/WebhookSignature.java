package com.invoicegenie.ar.adapter.messaging;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * HMAC-SHA256 body signature for customer webhooks.
 */
public final class WebhookSignature {

    public static final String HEADER = "X-InvoiceGenie-Signature";

    private WebhookSignature() {}

    public static String sign(String secret, String body) {
        if (secret == null || secret.isBlank()) {
            return "";
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal((body == null ? "" : body).getBytes(StandardCharsets.UTF_8));
            return "sha256=" + HexFormat.of().formatHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC sign failed", e);
        }
    }
}