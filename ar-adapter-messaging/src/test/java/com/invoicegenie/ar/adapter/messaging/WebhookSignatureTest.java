package com.invoicegenie.ar.adapter.messaging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WebhookSignatureTest {

    @Test
    void signsWithHmacSha256Prefix() {
        String sig = WebhookSignature.sign("secret", "{\"a\":1}");
        assertTrue(sig.startsWith("sha256="));
        assertEquals(7 + 64, sig.length());
    }

    @Test
    void emptySecretYieldsEmptySignature() {
        assertEquals("", WebhookSignature.sign("", "body"));
        assertEquals("", WebhookSignature.sign(null, "body"));
    }

    @Test
    void sameInputsAreDeterministic() {
        assertEquals(WebhookSignature.sign("s", "body"), WebhookSignature.sign("s", "body"));
    }
}